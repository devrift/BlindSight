package com.example.service

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.data.model.DiscoveryMedium
import com.example.data.model.NearbyDiscoveredDevice

class NsdHelper(
    private val context: Context,
    private val onDeviceFound: (NearbyDiscoveredDevice) -> Unit
) {
    private val tag = "NsdHelper"
    private val mainHandler = Handler(Looper.getMainLooper())
    private val nsdManager: NsdManager? =
        context.getSystemService(Context.NSD_SERVICE) as? NsdManager

    private var isDiscovering = false
    private val serviceType = "_http._tcp."
    private val customServiceType = "_blindsight._tcp."

    fun startDiscovery(timeoutMs: Long = 8000L) {
        if (nsdManager == null || isDiscovering) return
        isDiscovering = true

        try {
            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
            mainHandler.postDelayed({ stopDiscovery() }, timeoutMs)
            Log.i(tag, "mDNS discovery started...")
        } catch (e: Exception) {
            Log.e(tag, "startDiscovery error: ${e.message}")
            isDiscovering = false
        }
    }

    fun stopDiscovery() {
        if (!isDiscovering) return
        isDiscovering = false
        try {
            nsdManager?.stopServiceDiscovery(discoveryListener)
            Log.i(tag, "mDNS discovery stopped.")
        } catch (e: Exception) {
            Log.w(tag, "stopDiscovery error: ${e.message}")
        }
    }

    private val discoveryListener = object : NsdManager.DiscoveryListener {
        override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
            Log.e(tag, "onStartDiscoveryFailed: $errorCode")
            isDiscovering = false
        }

        override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
            Log.e(tag, "onStopDiscoveryFailed: $errorCode")
            isDiscovering = false
        }

        override fun onDiscoveryStarted(serviceType: String?) {
            Log.i(tag, "Discovery started for $serviceType")
        }

        override fun onDiscoveryStopped(serviceType: String?) {
            Log.i(tag, "Discovery stopped for $serviceType")
            isDiscovering = false
        }

        override fun onServiceFound(serviceInfo: NsdServiceInfo?) {
            if (serviceInfo == null) return
            val name = serviceInfo.serviceName ?: ""
            val low = name.lowercase()
            if (low.contains("blindsight") || low.contains("esp32") || low.contains("raspberrypi") || low.contains("openjarvis")) {
                try {
                    nsdManager?.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                            Log.w(tag, "Resolve failed for $name: code $errorCode")
                        }

                        override fun onServiceResolved(resolved: NsdServiceInfo?) {
                            if (resolved == null) return
                            val host = resolved.host?.hostAddress ?: return
                            val port = resolved.port
                            val url = "http://$host:$port"
                            val found = NearbyDiscoveredDevice(
                                name = resolved.serviceName ?: "BlindSight Hub",
                                identifier = host,
                                targetUrl = url,
                                medium = DiscoveryMedium.MDNS_ZEROCONF,
                                rssi = -35
                            )
                            mainHandler.post { onDeviceFound(found) }
                        }
                    })
                } catch (e: Exception) {
                    Log.w(tag, "resolveService notice: ${e.message}")
                }
            }
        }

        override fun onServiceLost(serviceInfo: NsdServiceInfo?) {
            Log.i(tag, "Service lost: ${serviceInfo?.serviceName}")
        }
    }
}
