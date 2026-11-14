package com.example

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
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
                val voiceIntentLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    if (result.resultCode == Activity.RESULT_OK) {
                        val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                        val text = matches?.firstOrNull()?.trim()
                        if (!text.isNullOrEmpty()) {
                            viewModel.onSpeechResult(text)
                        } else {
                            viewModel.onVoiceIntentCancelled()
                        }
                    } else {
                        viewModel.onVoiceIntentCancelled()
                    }
                }

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
                    viewModel.launchVoiceIntent = {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, viewModel.uiState.value.language.code)
                            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to BlindSight…")
                            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
                        }
                        try {
                            voiceIntentLauncher.launch(intent)
                        } catch (e: Exception) {
                            viewModel.fallbackStartListening()
                        }
                    }

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
