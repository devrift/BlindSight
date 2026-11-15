package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.service.UpdateDownloadState
import com.example.service.UpdateInfo
import com.example.ui.theme.BlindSightAmber
import com.example.ui.theme.BlindSightBad
import com.example.ui.theme.BlindSightCard
import com.example.ui.theme.BlindSightCardEdge
import com.example.ui.theme.BlindSightOk
import com.example.ui.theme.BlindSightTextMuted
import com.example.ui.theme.BlindSightTextPrimary

@Composable
fun UpdateDialog(
    updateState: UpdateDownloadState,
    onStartDownload: (UpdateInfo) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(BlindSightCard)
                .border(2.dp, BlindSightAmber, RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription = null,
                        tint = BlindSightAmber,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "App Self-Updater",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = BlindSightTextPrimary
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = BlindSightTextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            when (updateState) {
                is UpdateDownloadState.Checking -> {
                    Text(
                        text = "Checking for new versions on Vercel…",
                        color = BlindSightTextMuted,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = BlindSightAmber,
                        trackColor = BlindSightCardEdge
                    )
                }

                is UpdateDownloadState.Available -> {
                    val info = updateState.info
                    Text(
                        text = "New Version ${info.versionName} Available!",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = BlindSightOk
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = info.releaseNotes,
                        fontSize = 13.sp,
                        color = BlindSightTextPrimary
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(containerColor = BlindSightCardEdge),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Later", color = BlindSightTextMuted)
                        }

                        Button(
                            onClick = { onStartDownload(info) },
                            colors = ButtonDefaults.buttonColors(containerColor = BlindSightAmber),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.Download, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.size(4.dp))
                            Text("Update Now", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                is UpdateDownloadState.Downloading -> {
                    Text(
                        text = "Downloading APK: ${updateState.progressPercent}%",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = BlindSightAmber
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { updateState.progressPercent / 100f },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = BlindSightAmber,
                        trackColor = BlindSightCardEdge
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "The installer will prompt automatically once complete.",
                        fontSize = 12.sp,
                        color = BlindSightTextMuted
                    )
                }

                is UpdateDownloadState.ReadyToInstall -> {
                    Text(
                        text = "Download complete! Opening Android Package Installer…",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = BlindSightOk
                    )
                }

                is UpdateDownloadState.Error -> {
                    Text(
                        text = "Could not check for update: ${updateState.message}",
                        color = BlindSightBad,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = BlindSightCardEdge),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Dismiss", color = BlindSightTextPrimary)
                    }
                }

                else -> {
                    Text(
                        text = "Your BlindSight app is up to date!",
                        color = BlindSightOk,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = BlindSightCardEdge),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("OK", color = BlindSightTextPrimary)
                    }
                }
            }
        }
    }
}
