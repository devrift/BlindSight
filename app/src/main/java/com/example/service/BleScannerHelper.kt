package com.example.service

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.model.DiscoveryMedium
import com.example.data.model.NearbyDiscoveredDevice

class BleScannerHelper(
    private val context: Context,
    private val onDeviceFound: (NearbyDiscoveredDevice) -> Unit
) {
    private val tag = "BleScannerHelper"
    private val mainHandler = Handler(Looper.getMainLooper())
    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private var isScanning = false
    private val discoveredMacs = mutableSetOf<String>()

    fun hasBlePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.BLUETOOTH
            ) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan(timeoutMs: Long = 8000L) {
        if (!hasBlePermission()) {
            Log.w(tag, "Bluetooth permissions not granted")
            return
        }
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.w(tag, "Bluetooth is disabled or unavailable")
            return
        }
        if (isScanning) return

        discoveredMacs.clear()

        // 1. Check existing bonded / paired devices first
        try {
            val bonded = bluetoothAdapter.bondedDevices
            if (bonded != null) {
                for (dev in bonded) {
                    val name = dev.name ?: "Unknown Bluetooth Device"
                    if (isBlindSightDevice(name)) {
                        val device = NearbyDiscoveredDevice(
                            name = name,
                            identifier = dev.address,
                            targetUrl = "http://192.168.4.1:5000",
                            medium = DiscoveryMedium.BLUETOOTH_BLE,
                            rssi = -40
                        )
                        discoveredMacs.add(dev.address)
                        mainHandler.post { onDeviceFound(device) }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Bonded check error: ${e.message}")
        }

        // 2. Start active BLE LeScanner
        val scanner = bluetoothAdapter.bluetoothLeScanner
        if (scanner == null) {
            Log.w(tag, "BluetoothLeScanner is null")
            return
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            isScanning = true
            scanner.startScan(emptyList<ScanFilter>(), settings, scanCallback)
            mainHandler.postDelayed({ stopScan() }, timeoutMs)
            Log.i(tag, "BLE scanning started...")
        } catch (e: Exception) {
            Log.e(tag, "startScan error: ${e.message}")
            isScanning = false
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!isScanning) return
        isScanning = false
        try {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
            Log.i(tag, "BLE scanning stopped.")
        } catch (e: Exception) {
            Log.w(tag, "stopScan error: ${e.message}")
        }
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            if (result == null) return
            val device = result.device ?: return
            val devAddress = device.address ?: return
            if (discoveredMacs.contains(devAddress)) return

            val devName = try {
                result.scanRecord?.deviceName ?: device.name ?: ""
            } catch (_: Exception) {
                ""
            }

            if (isBlindSightDevice(devName)) {
                discoveredMacs.add(devAddress)
                val resolvedName = if (devName.isNotBlank()) devName else "BlindSight Smart Glasses"
                val found = NearbyDiscoveredDevice(
                    name = resolvedName,
                    identifier = devAddress,
                    targetUrl = "http://192.168.4.1:5000",
                    medium = DiscoveryMedium.BLUETOOTH_BLE,
                    rssi = result.rssi
                )
                mainHandler.post { onDeviceFound(found) }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Log.e(tag, "BLE scan failed with code: $errorCode")
            isScanning = false
        }
    }

    private fun isBlindSightDevice(name: String): Boolean {
        val low = name.lowercase()
        return low.contains("blindsight") ||
                low.contains("blind sight") ||
                low.contains("esp32-cam") ||
                low.contains("esp32_cam") ||
                low.contains("smart glasses") ||
                low.contains("esp32-devkit")
    }
}
