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
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
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
import com.example.ui.theme.AlertWarningRed
import com.example.ui.theme.ElectricCobalt
import com.example.ui.theme.LiquidGlassBorder
import com.example.ui.theme.MidnightBlueGlass
import com.example.ui.theme.TextPrimaryWhite
import com.example.ui.theme.SlateTextMuted
import java.io.File

@Composable
fun UpdateDialog(
    updateState: UpdateDownloadState,
    onStartDownload: (UpdateInfo) -> Unit,
    onOpenSettings: () -> Unit,
    onInstallNow: (File) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(MidnightBlueGlass)
                .border(1.5.dp, LiquidGlassBorder, RoundedCornerShape(22.dp))
                .padding(22.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(
                        imageVector = when (updateState) {
                            is UpdateDownloadState.PermissionRequired -> Icons.Default.Security
                            else -> Icons.Default.SystemUpdate
                        },
                        contentDescription = null,
                        tint = ElectricCobalt,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = when (updateState) {
                            is UpdateDownloadState.PermissionRequired -> "Install Permission Needed"
                            else -> "BlindSight Updater"
                        },
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimaryWhite
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = SlateTextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            when (updateState) {
                is UpdateDownloadState.Checking -> {
                    Text(
                        text = "Checking for new updates on Vercel…",
                        color = SlateTextMuted,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = ElectricCobalt,
                        trackColor = Color(0x33FFFFFF)
                    )
                }

                is UpdateDownloadState.Available -> {
                    val info = updateState.info
                    Text(
                        text = "Version ${info.versionName} is Available",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = ElectricCobalt
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = info.releaseNotes,
                        fontSize = 13.sp,
                        color = TextPrimaryWhite
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0x22FFFFFF)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Later", color = SlateTextMuted)
                        }

                        Button(
                            onClick = { onStartDownload(info) },
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricCobalt),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1.3f)
                        ) {
                            Icon(imageVector = Icons.Default.Download, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("Update Now", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                is UpdateDownloadState.Downloading -> {
                    Text(
                        text = "Downloading APK: ${updateState.progressPercent}%",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = ElectricCobalt
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { updateState.progressPercent / 100f },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = ElectricCobalt,
                        trackColor = Color(0x33FFFFFF)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "The installer will prompt automatically once the download completes.",
                        fontSize = 12.sp,
                        color = SlateTextMuted
                    )
                }

                is UpdateDownloadState.PermissionRequired -> {
                    Text(
                        text = "Android requires permission to install updates from unknown sources for this app.",
                        fontSize = 13.sp,
                        color = TextPrimaryWhite,
                        lineHeight = 19.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Tap 'Open Settings' below, enable 'Allow from this source', and return here to install the update.",
                        fontSize = 12.sp,
                        color = SlateTextMuted,
                        lineHeight = 17.sp
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onOpenSettings,
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricCobalt),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(imageVector = Icons.Default.Settings, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("Open Settings", color = Color.White, fontWeight = FontWeight.Bold)
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = onDismiss,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0x22FFFFFF)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Later", color = SlateTextMuted)
                            }
                            Button(
                                onClick = { onInstallNow(updateState.apkFile) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0x442563EB)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1.2f)
                            ) {
                                Text("Install Now", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                is UpdateDownloadState.ReadyToInstall -> {
                    Text(
                        text = "Download complete! Opening Android Package Installer…",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = ElectricCobalt
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = { onInstallNow(updateState.apkFile) },
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCobalt),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Tap to Install", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }

                is UpdateDownloadState.Error -> {
                    Text(
                        text = "Update error: ${updateState.message}",
                        color = AlertWarningRed,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0x22FFFFFF)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Dismiss", color = TextPrimaryWhite)
                    }
                }

                else -> {
                    Text(
                        text = "Your BlindSight app is up to date!",
                        color = ElectricCobalt,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0x22FFFFFF)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("OK", color = TextPrimaryWhite)
                    }
                }
            }
        }
    }
}
