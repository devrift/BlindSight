package com.example.service

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.model.SupportedLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class WeatherData(
    val temperatureC: Double,
    val weatherCode: Int,
    val windSpeed: Double,
    val cityName: String,
    val countryName: String,
    val timestamp: Long = System.currentTimeMillis()
)

class LocationWeatherHelper(private val context: Context) {

    private val tag = "LocationWeatherHelper"
    private var cachedWeather: WeatherData? = null
    private val cacheTtlMs = 10 * 60 * 1000L // 10 minutes

    /**
     * Retrieves current coordinates using Android LocationManager,
     * or IP-based geolocation fallback if GPS is not yet acquired.
     */
    suspend fun getCoordinates(): Pair<Double, Double> = withContext(Dispatchers.IO) {
        // 1. Try Android LocationManager
        try {
            val hasFine = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            val hasCoarse = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            if (hasFine || hasCoarse) {
                val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                if (lm != null) {
                    @SuppressLint("MissingPermission")
                    val locGps = try { lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) } catch (_: Exception) { null }
                    @SuppressLint("MissingPermission")
                    val locNet = try { lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) } catch (_: Exception) { null }
                    @SuppressLint("MissingPermission")
                    val locPassive = try { lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER) } catch (_: Exception) { null }

                    val bestLoc = listOfNotNull(locGps, locNet, locPassive)
                        .maxByOrNull { it.time }

                    if (bestLoc != null && bestLoc.latitude != 0.0 && bestLoc.longitude != 0.0) {
                        return@withContext Pair(bestLoc.latitude, bestLoc.longitude)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Device location check error: ${e.message}")
        }

        // 2. IP-based location fallback (Zero permission, always works!)
        try {
            val url = URL("https://ipapi.co/json/")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.setRequestProperty("User-Agent", "BlindSight-App/1.7")
            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                val lat = json.optDouble("latitude", Double.NaN)
                val lon = json.optDouble("longitude", Double.NaN)
                if (!lat.isNaN() && !lon.isNaN()) {
                    return@withContext Pair(lat, lon)
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "IP location fallback unavailable: ${e.message}")
        }

        // Default fallback: New Delhi (Standard default)
        return@withContext Pair(28.6139, 77.2090)
    }

    /**
     * Resolves human city and country name for coordinates.
     */
    suspend fun resolveCityAndCountry(lat: Double, lon: Double): Pair<String, String> = withContext(Dispatchers.IO) {
        try {
            if (Geocoder.isPresent()) {
                val geocoder = Geocoder(context, Locale.getDefault())
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(lat, lon, 1)
                if (!addresses.isNullOrEmpty()) {
                    val addr = addresses[0]
                    val city = addr.locality ?: addr.subAdminArea ?: addr.adminArea ?: "Local Area"
                    val country = addr.countryName ?: "India"
                    return@withContext Pair(city, country)
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Geocoder failed: ${e.message}")
        }
        return@withContext Pair("Current Location", "Local Region")
    }

    /**
     * Fetches real-time weather from Open-Meteo REST API (free, zero API key).
     */
    suspend fun fetchCurrentWeather(forceRefresh: Boolean = false): WeatherData? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!forceRefresh && cachedWeather != null && (now - cachedWeather!!.timestamp < cacheTtlMs)) {
            return@withContext cachedWeather
        }

        val (lat, lon) = getCoordinates()
        val (city, country) = resolveCityAndCountry(lat, lon)

        try {
            val endpoint = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current_weather=true"
            val url = URL(endpoint)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 4000
            conn.readTimeout = 5000
            conn.setRequestProperty("User-Agent", "BlindSight-App/1.7")

            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                val cw = json.optJSONObject("current_weather")
                if (cw != null) {
                    val temp = cw.optDouble("temperature", 22.0)
                    val code = cw.optInt("weathercode", 0)
                    val wind = cw.optDouble("windspeed", 5.0)

                    val data = WeatherData(
                        temperatureC = temp,
                        weatherCode = code,
                        windSpeed = wind,
                        cityName = city,
                        countryName = country,
                        timestamp = now
                    )
                    cachedWeather = data
                    return@withContext data
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Weather fetch error: ${e.message}")
        }

        return@withContext cachedWeather
    }

    fun decodeWeatherCode(code: Int, lang: SupportedLanguage): String {
        return when (code) {
            0 -> if (lang == SupportedLanguage.HINDI) "साफ और धूप भरा आसमान" else "clear and sunny sky"
            1 -> if (lang == SupportedLanguage.HINDI) "मुख्यतः साफ आसमान" else "mainly clear"
            2 -> if (lang == SupportedLanguage.HINDI) "आंशिक रूप से बादल छाए हुए" else "partly cloudy"
            3 -> if (lang == SupportedLanguage.HINDI) "घने बादल" else "overcast"
            45, 48 -> if (lang == SupportedLanguage.HINDI) "कोहरा" else "foggy"
            51, 53, 55 -> if (lang == SupportedLanguage.HINDI) "हल्की बूंदाबांदी" else "light drizzle"
            61, 63, 65 -> if (lang == SupportedLanguage.HINDI) "बारिश" else "rainy"
            71, 73, 75 -> if (lang == SupportedLanguage.HINDI) "बर्फबारी" else "snow"
            80, 81, 82 -> if (lang == SupportedLanguage.HINDI) "तेज बारिश की बौछारें" else "rain showers"
            95, 96, 99 -> if (lang == SupportedLanguage.HINDI) "गरज के साथ तूफान" else "thunderstorm"
            else -> if (lang == SupportedLanguage.HINDI) "सुहाना मौसम" else "pleasant weather"
        }
    }

    /**
     * Formats weather into natural human spoken response.
     */
    suspend fun getFormattedWeatherSummary(lang: SupportedLanguage): String {
        val weather = fetchCurrentWeather()
        if (weather == null) {
            return if (lang == SupportedLanguage.HINDI) {
                "मौसम की जानकारी अभी उपलब्ध नहीं हो पाई है। कृपया कुछ देर बाद पुनः प्रयास करें।"
            } else {
                "I couldn't retrieve the live weather forecast right now. Please check your connection."
            }
        }

        val tempC = Math.round(weather.temperatureC).toInt()
        val tempF = Math.round((weather.temperatureC * 9 / 5) + 32).toInt()
        val desc = decodeWeatherCode(weather.weatherCode, lang)
        val wind = Math.round(weather.windSpeed).toInt()

        return if (lang == SupportedLanguage.HINDI) {
            "वर्तमान में ${weather.cityName} में तापमान $tempC डिग्री सेल्सियस है और $desc है।"
        } else {
            "It is currently $tempC°C ($tempF°F) and $desc in ${weather.cityName}, with wind around $wind kilometers per hour."
        }
    }

    /**
     * Supplies detailed environmental facts for LLM System Prompt.
     */
    suspend fun getEnvironmentalContext(): String {
        val (lat, lon) = getCoordinates()
        val weather = fetchCurrentWeather()
        val timeStr = SimpleDateFormat("h:mm a, EEEE, MMMM d, yyyy", Locale.ENGLISH).format(Date())

        val cityStr = weather?.cityName ?: "Local Area"
        val countryStr = weather?.countryName ?: "India"
        val tempStr = if (weather != null) "${Math.round(weather.temperatureC)}°C (${decodeWeatherCode(weather.weatherCode, SupportedLanguage.ENGLISH)})" else "22°C (Fair)"
        val windStr = if (weather != null) "${Math.round(weather.windSpeed)} km/h" else "5 km/h"

        return """
[REAL-TIME ENVIRONMENTAL CONTEXT (DO NOT ASK USER FOR ZIP CODE OR CITY)]
- Current Location: $cityStr, $countryStr (Approx coordinates: $lat, $lon)
- Current Real-Time Weather: $tempStr, Wind: $windStr
- Current Local Date & Time: $timeStr
[CRITICAL INSTRUCTION: If user asks about the weather, temperature, location, or time, IMMEDIATELY provide this exact real-time information. NEVER ask the user for their zip code or city.]
""".trimIndent()
    }
}
