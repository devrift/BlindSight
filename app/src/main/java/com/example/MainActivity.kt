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

                    if (!hasAudio) {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.RECORD_AUDIO,
                                Manifest.permission.CAMERA
                            )
                        )
                    } else {
                        viewModel.onMicrophonePermissionGranted()
                    }
                }

                BlindSightScreen(viewModel = viewModel)
            }
        }
    }
}
