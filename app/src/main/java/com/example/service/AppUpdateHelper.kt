package com.example.service

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.example.BuildConfig
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val releaseNotes: String
)

sealed class UpdateDownloadState {
    object Idle : UpdateDownloadState()
    object Checking : UpdateDownloadState()
    data class Available(val info: UpdateInfo) : UpdateDownloadState()
    data class Downloading(val progressPercent: Int) : UpdateDownloadState()
    data class ReadyToInstall(val apkFile: File) : UpdateDownloadState()
    data class PermissionRequired(val apkFile: File, val info: UpdateInfo? = null) : UpdateDownloadState()
    data class Error(val message: String) : UpdateDownloadState()
}

class AppUpdateHelper(private val context: Context) {

    private val tag = "AppUpdateHelper"
    private val mainHandler = Handler(Looper.getMainLooper())
    private val versionUrl = "https://blindsightai.vercel.app/version.json"

    val currentVersionCode: Int = BuildConfig.VERSION_CODE
    val currentVersionName: String = BuildConfig.VERSION_NAME

    private fun openCloudConnection(url: URL): HttpURLConnection {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (cm != null) {
                val internetNet = cm.allNetworks.firstOrNull { net ->
                    val caps = cm.getNetworkCapabilities(net)
                    caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                            (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ||
                             caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))
                }
                if (internetNet != null) {
                    return internetNet.openConnection(url) as HttpURLConnection
                }
            }
        } catch (_: Exception) {}
        return url.openConnection() as HttpURLConnection
    }

    fun checkForUpdate(
        onResult: (UpdateInfo?) -> Unit,
        onError: ((String) -> Unit)? = null
    ) {
        thread(name = "BlindSightUpdateCheck") {
            try {
                val url = URL(versionUrl)
                val conn = openCloudConnection(url)
                conn.connectTimeout = 4000
                conn.readTimeout = 4000
                conn.requestMethod = "GET"
                conn.setRequestProperty("User-Agent", "BlindSight-Android/${currentVersionName}")

                if (conn.responseCode == 200) {
                    val rawJson = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(rawJson)
                    val remoteVersionCode = json.optInt("versionCode", currentVersionCode)
                    val remoteVersionName = json.optString("versionName", currentVersionName)
                    val apkUrl = json.optString("apkUrl", "https://blindsightai.vercel.app/BlindSight-v1.0.0.apk")
                    val releaseNotes = json.optString("releaseNotes", "Bug fixes and performance improvements.")

                    if (remoteVersionCode > currentVersionCode) {
                        val info = UpdateInfo(
                            versionCode = remoteVersionCode,
                            versionName = remoteVersionName,
                            apkUrl = apkUrl,
                            releaseNotes = releaseNotes
                        )
                        mainHandler.post { onResult(info) }
                    } else {
                        mainHandler.post { onResult(null) }
                    }
                } else {
                    mainHandler.post { onError?.invoke("HTTP ${conn.responseCode}") ?: onResult(null) }
                }
            } catch (e: Exception) {
                Log.w(tag, "Update check failed: ${e.message}")
                mainHandler.post { onError?.invoke(e.message ?: "Network error") ?: onResult(null) }
            }
        }
    }

    fun canInstallPackages(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun openInstallPermissionSettings() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(settingsIntent)
            } else {
                val settingsIntent = Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(settingsIntent)
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed to open install settings: ${e.message}")
            try {
                val appDetailsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(appDetailsIntent)
            } catch (_: Exception) {}
        }
    }

    fun downloadAndInstall(
        info: UpdateInfo,
        onProgress: (Int) -> Unit,
        onReady: (File) -> Unit,
        onError: (String) -> Unit
    ) {
        thread(name = "BlindSightApkDownload") {
            try {
                val url = URL(info.apkUrl)
                val conn = openCloudConnection(url)
                conn.connectTimeout = 8000
                conn.readTimeout = 30000
                conn.requestMethod = "GET"

                if (conn.responseCode != 200) {
                    mainHandler.post { onError("Download failed with server code ${conn.responseCode}") }
                    return@thread
                }

                val contentLength = conn.contentLength
                val cacheDir = context.externalCacheDir ?: context.cacheDir
                val apkFile = File(cacheDir, "BlindSight-update-${info.versionName}.apk")
                if (apkFile.exists()) {
                    apkFile.delete()
                }

                var downloadedBytes = 0
                val buffer = ByteArray(8192)

                conn.inputStream.use { input ->
                    FileOutputStream(apkFile).use { output ->
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            downloadedBytes += read
                            if (contentLength > 0) {
                                val percent = ((downloadedBytes * 100L) / contentLength).toInt()
                                mainHandler.post { onProgress(percent) }
                            }
                        }
                    }
                }

                if (apkFile.exists() && apkFile.length() > 500000) {
                    mainHandler.post {
                        onReady(apkFile)
                    }
                } else {
                    mainHandler.post { onError("Corrupted APK file downloaded.") }
                }
            } catch (e: Exception) {
                Log.e(tag, "Failed downloading update: ${e.message}", e)
                mainHandler.post { onError(e.message ?: "Failed downloading update") }
            }
        }
    }

    fun triggerInstall(apkFile: File): Boolean {
        try {
            // Check unknown apps install permission on Android 8+ (Oreo+)
            if (!canInstallPackages()) {
                openInstallPermissionSettings()
                return false
            }

            val apkUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
            return true
        } catch (e: Exception) {
            Log.e(tag, "Failed to launch package installer: ${e.message}", e)
            return false
        }
    }
}
