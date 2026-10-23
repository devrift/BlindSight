package app.blindsight.companion

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.net.http.SslError
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.net.URI

/**
 * BlindSight Android shell.
 *
 * A deliberately thin, auditable wrapper: one WebView pointed at the user's
 * gateway (Pi or laptop) on the local Wi-Fi. All intelligence stays on the
 * gateway; this app adds what a browser cannot: a real launcher icon, native
 * runtime mic/camera permission prompts, self-signed-cert trust scoped to the
 * saved gateway host only, and a friendly retry screen when the gateway sleeps.
 */
class MainActivity : AppCompatActivity() {
    private lateinit var web: WebView
    private var pendingPermission: PermissionRequest? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this)
        web.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        setContentView(web)
        setupWeb()
        loadGateway()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWeb() {
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        // Replies are audio; requiring a fresh gesture per play would mute the
        // companion after every navigation inside the shell.
        web.settings.mediaPlaybackRequiresUserGesture = false
        web.settings.allowFileAccess = false

        web.addJavascriptInterface(object {
            @JavascriptInterface
            fun changeGateway() {
                runOnUiThread { askGatewayUrl(firstRun = false) }
            }
        }, "BlindSightNative")

        web.webViewClient = object : WebViewClient() {
            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler, error: SslError) {
                // Home-LAN gateways use self-signed certificates (the Pi starter
                // generates them). Trust is scoped to the saved gateway host and
                // nothing else - any other certificate error fails closed.
                val saved = gatewayHost()
                val host = runCatching { URI(error.url).host }.getOrNull()
                if (saved != null && host == saved) handler.proceed()
                else super.onReceivedSslError(view, handler, error)
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                if (request?.isForMainFrame == true) {
                    view?.loadData(retryHtml(), "text/html", "utf-8")
                }
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                // The PWA asks for the mic; Android asks through us. Grant only
                // after the runtime permission dialog is accepted.
                pendingPermission = request
                val needed = mutableListOf<String>()
                if (request.resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE) &&
                    ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                    needed += Manifest.permission.RECORD_AUDIO
                }
                if (request.resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE) &&
                    ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CAMERA) !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                    needed += Manifest.permission.CAMERA
                }
                if (needed.isEmpty()) request.grant(request.resources)
                else ActivityCompat.requestPermissions(this@MainActivity, needed.toTypedArray(), REQUEST_PERMS)
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_PERMS) return
        val request = pendingPermission ?: return
        pendingPermission = null
        if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            request.grant(request.resources)
        } else {
            request.deny()
        }
    }

    private fun prefs() = getSharedPreferences("blindsight", MODE_PRIVATE)
    private fun gatewayUrl(): String? = prefs().getString("gateway_url", null)
    private fun gatewayHost(): String? = gatewayUrl()?.let { runCatching { URI(it).host }.getOrNull() }

    private fun loadGateway() {
        val url = gatewayUrl()
        if (url == null) askGatewayUrl(firstRun = true) else web.loadUrl(url)
    }

    private fun askGatewayUrl(firstRun: Boolean) {
        val input = EditText(this)
        input.hint = "https://192.168.1.20:5000"
        input.setText(gatewayUrl() ?: "")
        input.setSingleLine()
        AlertDialog.Builder(this)
            .setTitle(if (firstRun) "BlindSight gateway address" else "Change gateway address")
            .setMessage("The web address of the BlindSight gateway on your Wi-Fi. " +
                "The Pi or laptop starter prints it (use the https:// one).")
            .setView(input)
            .setPositiveButton("Connect") { _, _ ->
                val raw = input.text.toString().trim()
                if (raw.isEmpty()) return@setPositiveButton
                val withScheme =
                    if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "https://$raw"
                prefs().edit().putString("gateway_url", withScheme).apply()
                web.loadUrl(withScheme)
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                if (firstRun) finish()
            }
            .show()
    }

    private fun retryHtml(): String = """
        <html><body style="background:#05070c;color:#e6edf3;font-family:sans-serif;text-align:center;padding-top:18vh;margin:0">
        <h2>Gateway not reachable</h2>
        <p>Make sure the Pi or laptop running BlindSight is switched on<br>and on the same Wi-Fi as this phone.</p>
        <p><a href="#" onclick="location.reload();return false;" style="color:#5eead4;font-size:1.2em">Try again</a>
        &nbsp;&middot;&nbsp;
        <a href="#" onclick="window.BlindSightNative.changeGateway();return false;" style="color:#5eead4;font-size:1.2em">Change address</a></p>
        </body></html>
    """.trimIndent()

    @Deprecated("Use back callback")
    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    companion object {
        private const val REQUEST_PERMS = 42
    }
}
