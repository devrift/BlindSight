package com.example

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
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
                    val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
                    if (audioGranted) {
                        viewModel.speak("Microphone access ready. Double-tap the giant center button or say Hey BlindSight to speak.")
                    }
                }

                LaunchedEffect(Unit) {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.RECORD_AUDIO,
                            Manifest.permission.CAMERA
                        )
                    )
                }

                BlindSightScreen(viewModel = viewModel)
            }
        }
    }
}
