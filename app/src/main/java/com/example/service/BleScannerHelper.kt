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
                    val address = dev.address ?: ""
                    if (isGlassesOrEspDevice(name, address)) {
                        val resolvedName = if (name.isNotBlank()) name else "BlindSight Smart Glasses"
                        val device = NearbyDiscoveredDevice(
                            name = resolvedName,
                            identifier = "Serial: $address",
                            targetUrl = "http://192.168.4.1:5000",
                            medium = DiscoveryMedium.BLUETOOTH_BLE,
                            rssi = -45
                        )
                        discoveredMacs.add(address)
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

            val isGlasses = isGlassesOrEspDevice(devName, devAddress)
            if (isGlasses) {
                discoveredMacs.add(devAddress)
                val resolvedName = if (devName.isNotBlank()) devName else "BlindSight Smart Glasses"
                val found = NearbyDiscoveredDevice(
                    name = resolvedName,
                    identifier = "Serial: $devAddress",
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

    private fun isGlassesOrEspDevice(name: String, address: String): Boolean {
        val low = name.lowercase().trim()
        val keywords = listOf(
            "blindsight", "blind sight", "smart glasses", "glasses",
            "esp32", "esp-", "esp_", "ai-thinker",
            "raspberry", "raspberrypi", "pi", "jarvis", "openjarvis",
            "camera", "vision"
        )
        if (keywords.any { low.contains(it) }) return true

        // Espressif & Raspberry Pi Bluetooth MAC OUIs
        val macClean = address.replace(":", "").replace("-", "").uppercase()
        if (macClean.length >= 6) {
            val oui = macClean.substring(0, 6)
            val knownOuis = setOf(
                "240AC4", "246F28", "30AEA4", "3C6105", "3C71BF", "4022D8", "409151",
                "4827E2", "4C11AE", "4C7525", "5443B2", "5CCF7F", "600194", "68B6B3",
                "68C63A", "70039F", "70B8F6", "782184", "7C9EBD", "840D8E", "84F3EB",
                "8CAAB5", "94A9A8", "94B555", "94B97E", "98CDAC", "A020A6", "A4CF12",
                "A4E57C", "AC67B2", "B4E62D", "BCDD02", "C44F33", "C4DEE2", "CC50E3",
                "D8A01D", "DC4F22", "DC5475", "E09806", "E831CD", "E86BEA", "E89F6D",
                "EC6260", "EC64C9", "F412FA", "F8B3B7",
                "B827EB", "DCA632", "E45F01", "28CDC1", "D83ADD"
            )
            if (knownOuis.contains(oui)) return true
        }
        return false
    }
}
