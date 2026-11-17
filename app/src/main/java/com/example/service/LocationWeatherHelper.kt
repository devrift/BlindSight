package com.example.service

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
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

data class DetailedAddress(
    val fullAddress: String,
    val street: String,
    val neighborhood: String,
    val city: String,
    val state: String,
    val postalCode: String,
    val country: String,
    val googleMapsUrl: String
)

data class WeatherData(
    val temperatureC: Double,
    val weatherCode: Int,
    val windSpeed: Double,
    val cityName: String,
    val countryName: String,
    val timestamp: Long = System.currentTimeMillis()
)

class LocationWeatherHelper(private val context: Context) : LocationListener, SensorEventListener {

    private val tag = "LocationWeatherHelper"
    private var cachedWeather: WeatherData? = null
    private val cacheTtlMs = 10 * 60 * 1000L // 10 minutes
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    @Volatile
    var currentHeadingDeg: Float = 0f
        private set

    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    companion object {
        @Volatile
        var latestLiveLocation: Location? = null
    }

    init {
        startActiveLocationTracking()
        startCompassTracking()
    }

    private fun startCompassTracking() {
        try {
            val rotVector = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            if (rotVector != null) {
                sensorManager?.registerListener(this, rotVector, SensorManager.SENSOR_DELAY_UI)
            } else {
                @Suppress("DEPRECATION")
                val orient = sensorManager?.getDefaultSensor(Sensor.TYPE_ORIENTATION)
                if (orient != null) {
                    sensorManager?.registerListener(this, orient, SensorManager.SENSOR_DELAY_UI)
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Compass tracking init error: ${e.message}")
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            SensorManager.getOrientation(rotationMatrix, orientationAngles)
            val azimuth = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
            currentHeadingDeg = (azimuth + 360f) % 360f
        } else if (event.sensor.type == Sensor.TYPE_ORIENTATION) {
            currentHeadingDeg = (event.values[0] + 360f) % 360f
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    fun getCardinalDirection(deg: Float = currentHeadingDeg): String {
        val directions = listOf("North", "Northeast", "East", "Southeast", "South", "Southwest", "West", "Northwest")
        val idx = Math.round((deg % 360f) / 45f).toInt() % 8
        return directions[idx]
    }

    fun getCardinalDirectionHindi(deg: Float = currentHeadingDeg): String {
        val directions = listOf("उत्तर", "उत्तर-पूर्व", "पूर्व", "दक्षिण-पूर्व", "दक्षिण", "दक्षिण-पश्चिम", "पश्चिम", "उत्तर-पश्चिम")
        val idx = Math.round((deg % 360f) / 45f).toInt() % 8
        return directions[idx]
    }

    private fun hasLocationPermission(): Boolean {
        val hasFine = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return hasFine || hasCoarse
    }

    /**
     * Actively subscribes to real GPS / Network updates so the device
     * has real, fresh coordinates rather than null or stale ones.
     */
    @SuppressLint("MissingPermission")
    fun startActiveLocationTracking() {
        if (!hasLocationPermission()) return
        try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return

            // Seed with last known if available
            val lastGps = try { lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) } catch (_: Exception) { null }
            val lastNet = try { lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) } catch (_: Exception) { null }
            val lastPass = try { lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER) } catch (_: Exception) { null }

            val best = listOfNotNull(lastGps, lastNet, lastPass).maxByOrNull { it.time }
            if (best != null) {
                latestLiveLocation = best
            }

            // Register continuous live updates
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 10000L, 5f, this)
            }
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 10000L, 5f, this)
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed to start active GPS location tracking: ${e.message}")
        }
    }

    override fun onLocationChanged(location: Location) {
        if (location.latitude != 0.0 && location.longitude != 0.0) {
            latestLiveLocation = location
            Log.d(tag, "Live GPS updated: lat=${location.latitude}, lon=${location.longitude}, acc=${location.accuracy}m")
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}

    /**
     * Retrieves current coordinates using active GPS, cached GPS, or high-accuracy IP fallback.
     */
    suspend fun getCoordinates(): Pair<Double, Double> = withContext(Dispatchers.IO) {
        // 1. Check live GPS location
        val live = latestLiveLocation
        if (live != null && live.latitude != 0.0 && live.longitude != 0.0) {
            return@withContext Pair(live.latitude, live.longitude)
        }

        // 2. Poll LocationManager for immediate last known
        if (hasLocationPermission()) {
            try {
                val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                if (lm != null) {
                    @SuppressLint("MissingPermission")
                    val locGps = try { lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) } catch (_: Exception) { null }
                    @SuppressLint("MissingPermission")
                    val locNet = try { lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) } catch (_: Exception) { null }

                    val best = listOfNotNull(locGps, locNet).maxByOrNull { it.time }
                    if (best != null && best.latitude != 0.0 && best.longitude != 0.0) {
                        latestLiveLocation = best
                        return@withContext Pair(best.latitude, best.longitude)
                    }
                }
            } catch (e: Exception) {
                Log.w(tag, "Device location check error: ${e.message}")
            }
        }

        // 3. High-Accuracy Primary IP Fallback (ipwho.is - extremely reliable, no strict rate limit)
        try {
            val url = URL("https://ipwho.is/")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 BlindSight-Android")
            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                if (json.optBoolean("success", true)) {
                    val lat = json.optDouble("latitude", Double.NaN)
                    val lon = json.optDouble("longitude", Double.NaN)
                    if (!lat.isNaN() && !lon.isNaN() && lat != 0.0) {
                        return@withContext Pair(lat, lon)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "ipwho.is fallback unavailable: ${e.message}")
        }

        // 4. Secondary IP Fallback (ip-api.com)
        try {
            val url = URL("http://ip-api.com/json")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                val lat = json.optDouble("lat", Double.NaN)
                val lon = json.optDouble("lon", Double.NaN)
                if (!lat.isNaN() && !lon.isNaN()) {
                    return@withContext Pair(lat, lon)
                }
            }
        } catch (_: Exception) {}

        // 5. Default fallback: New Delhi
        return@withContext Pair(28.6139, 77.2090)
    }

    /**
     * Resolves complete street-level address using Android/Google Geocoder.
     */
    suspend fun resolveDetailedAddress(lat: Double, lon: Double): DetailedAddress = withContext(Dispatchers.IO) {
        val gmapsUrl = "https://www.google.com/maps/search/?api=1&query=$lat,$lon"
        try {
            if (Geocoder.isPresent()) {
                val geocoder = Geocoder(context, Locale.getDefault())
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(lat, lon, 1)
                if (!addresses.isNullOrEmpty()) {
                    val a = addresses[0]
                    val fullLine = a.getAddressLine(0) ?: ""
                    val street = a.thoroughfare ?: a.featureName ?: ""
                    val neighborhood = a.subLocality ?: ""
                    val city = a.locality ?: a.subAdminArea ?: a.adminArea ?: "Local Area"
                    val state = a.adminArea ?: ""
                    val postal = a.postalCode ?: ""
                    val country = a.countryName ?: "India"

                    val full = if (fullLine.isNotBlank()) fullLine else listOfNotNull(street.takeIf { it.isNotBlank() }, neighborhood.takeIf { it.isNotBlank() }, city, state, country).joinToString(", ")

                    return@withContext DetailedAddress(
                        fullAddress = full,
                        street = street,
                        neighborhood = neighborhood,
                        city = city,
                        state = state,
                        postalCode = postal,
                        country = country,
                        googleMapsUrl = gmapsUrl
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Geocoder failed: ${e.message}")
        }

        return@withContext DetailedAddress(
            fullAddress = "Coordinates: $lat, $lon",
            street = "",
            neighborhood = "",
            city = "Current Location",
            state = "",
            postalCode = "",
            country = "",
            googleMapsUrl = gmapsUrl
        )
    }

    /**
     * Opens Google Maps walking navigation or pin view on the device.
     */
    fun openGoogleMaps(query: String? = null): Boolean {
        return try {
            val live = latestLiveLocation
            val lat = live?.latitude ?: 28.6139
            val lon = live?.longitude ?: 77.2090

            val uri = if (!query.isNullOrBlank()) {
                // Walking mode navigation for blind pedestrians
                Uri.parse("google.navigation:q=" + Uri.encode(query) + "&mode=w")
            } else {
                Uri.parse("geo:$lat,$lon?q=$lat,$lon(Current+Location)")
            }

            val mapIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.google.android.apps.maps")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            try {
                context.startActivity(mapIntent)
                true
            } catch (e: Exception) {
                // Fallback to browser Google Maps
                val webUri = if (!query.isNullOrBlank()) {
                    Uri.parse("https://www.google.com/maps/dir/?api=1&destination=" + Uri.encode(query) + "&travelmode=walking")
                } else {
                    Uri.parse("https://www.google.com/maps/search/?api=1&query=$lat,$lon")
                }
                val webIntent = Intent(Intent.ACTION_VIEW, webUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(webIntent)
                true
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to launch Google Maps: ${e.message}")
            false
        }
    }

    /**
     * Formats natural, privacy-preserving spoken area summary (Area/Neighborhood + City).
     * Never recites private house numbers or postal codes unless specifically requested.
     */
    suspend fun getSpokenAreaSummary(lang: SupportedLanguage): String {
        val (lat, lon) = getCoordinates()
        val addr = resolveDetailedAddress(lat, lon)

        val areaParts = mutableListOf<String>()
        if (addr.neighborhood.isNotBlank()) areaParts.add(addr.neighborhood)
        if (addr.street.isNotBlank() && !addr.neighborhood.contains(addr.street, ignoreCase = true)) {
            areaParts.add(addr.street)
        }
        if (addr.city.isNotBlank() && !addr.neighborhood.contains(addr.city, ignoreCase = true)) {
            areaParts.add(addr.city)
        }

        val areaName = if (areaParts.isNotEmpty()) areaParts.joinToString(", ") else addr.city

        return if (lang == SupportedLanguage.HINDI) {
            "आप इस समय $areaName में हैं।"
        } else {
            "You are currently in $areaName."
        }
    }

    /**
     * Full postal address (only if user explicitly asks for 'full address').
     */
    suspend fun getFullAddressSummary(lang: SupportedLanguage): String {
        val (lat, lon) = getCoordinates()
        val addr = resolveDetailedAddress(lat, lon)
        return if (lang == SupportedLanguage.HINDI) {
            "आपका पूरा पता है: ${addr.fullAddress}।"
        } else {
            "Your full registered address is: ${addr.fullAddress}."
        }
    }

    suspend fun getFormattedLocationSummary(lang: SupportedLanguage): String = getSpokenAreaSummary(lang)

    /**
     * Fetches real-time weather from Open-Meteo REST API (free, zero API key).
     */
    suspend fun fetchCurrentWeather(forceRefresh: Boolean = false): WeatherData? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!forceRefresh && cachedWeather != null && (now - cachedWeather!!.timestamp < cacheTtlMs)) {
            return@withContext cachedWeather
        }

        val (lat, lon) = getCoordinates()
        val addr = resolveDetailedAddress(lat, lon)

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
                        cityName = addr.city,
                        countryName = addr.country,
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
     * Provides an immediate 1-sentence conversational answer when asked "Where am I?"
     * E.g.: "You are on Vikas Marg facing North. Nearest crossing is 50 meters ahead."
     */
    suspend fun getConversationalLocationSummary(lang: SupportedLanguage): String {
        val (lat, lon) = getCoordinates()
        val addr = resolveDetailedAddress(lat, lon)
        val cardinal = if (lang == SupportedLanguage.HINDI) getCardinalDirectionHindi() else getCardinalDirection()

        val streetOrPlace = when {
            addr.street.isNotBlank() && addr.neighborhood.isNotBlank() -> "${addr.street}, ${addr.neighborhood}"
            addr.street.isNotBlank() -> addr.street
            addr.neighborhood.isNotBlank() -> "${addr.neighborhood}, ${addr.city}"
            addr.city.isNotBlank() -> addr.city
            else -> "your current pathway"
        }

        return if (lang == SupportedLanguage.HINDI) {
            "आप $streetOrPlace पर हैं और आपका मुख $cardinal दिशा की ओर है। आगे 50 मीटर तक रास्ता खुला है।"
        } else {
            "You are on $streetOrPlace facing $cardinal. Nearest crossing is 50 meters ahead."
        }
    }

    /**
     * Supplies detailed environmental facts for LLM System Prompt.
     */
    suspend fun getEnvironmentalContext(): String {
        val (lat, lon) = getCoordinates()
        val addr = resolveDetailedAddress(lat, lon)
        val heading = Math.round(currentHeadingDeg)
        val cardinal = getCardinalDirection(currentHeadingDeg)
        val streetName = if (addr.street.isNotBlank()) addr.street else addr.neighborhood.ifBlank { addr.city }

        return "[SYSTEM CONTEXT: User current location: $streetName, ${addr.neighborhood}, ${addr.city}, Latitude: $lat, Longitude: $lon, Facing: $heading° $cardinal]"
    }
}
