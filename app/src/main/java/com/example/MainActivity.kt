package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat
import com.example.ui.BlindSightScreen
import com.example.ui.BlindSightViewModel
import com.example.ui.theme.BlindSightTheme

class MainActivity : ComponentActivity() {

    private val viewModel: BlindSightViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            BlindSightTheme {
                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
                    if (audioGranted) {
                        viewModel.onMicrophonePermissionGranted()
                        viewModel.speak("Microphone access ready. Say Hey BlindSight or tap the center button to speak.")
                    }
                }

                LaunchedEffect(Unit) {
                    val hasAudio = ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED

                    val hasLocation = ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED

                    val hasCamera = ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED

                    if (!hasAudio || !hasLocation || !hasCamera) {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.RECORD_AUDIO,
                                Manifest.permission.CAMERA,
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    } else {
                        viewModel.onMicrophonePermissionGranted()
                    }
                }

                BlindSightScreen(viewModel = viewModel)
            }
        }
        checkAssistantLaunch(intent)
    }

    override fun onResume() {
        super.onResume()
        checkAssistantLaunch(intent)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        checkAssistantLaunch(intent)
    }

    private fun checkAssistantLaunch(intent: android.content.Intent?) {
        if (intent == null) return
        if (intent.action == android.content.Intent.ACTION_ASSIST ||
            intent.action == android.content.Intent.ACTION_VOICE_COMMAND ||
            intent.getBooleanExtra("START_ASSISTANT_LISTENING", false)
        ) {
            intent.removeExtra("START_ASSISTANT_LISTENING")
            viewModel.onTactileSingleTap()
        }
    }
}
