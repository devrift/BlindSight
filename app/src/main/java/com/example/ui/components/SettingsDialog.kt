package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.SupportedLanguage
import com.example.service.VoicePersona
import com.example.ui.theme.BlindSightAmber
import com.example.ui.theme.BlindSightCard
import com.example.ui.theme.BlindSightCardEdge
import com.example.ui.theme.BlindSightTextMuted
import com.example.ui.theme.BlindSightTextPrimary

@Composable
fun SettingsDialog(
    currentLanguage: SupportedLanguage,
    onLanguageChange: (SupportedLanguage) -> Unit,
    currentVoicePersona: VoicePersona = VoicePersona.AUTO,
    onVoicePersonaChange: (VoicePersona) -> Unit = {},
    currentTextSize: Float,
    onTextSizeChange: (Float) -> Unit,
    highContrastYellow: Boolean,
    onToggleHighContrast: () -> Unit,
    isPhoneCameraFallbackEnabled: Boolean = true,
    onTogglePhoneCameraFallback: () -> Unit = {},
    currentGatewayUrl: String = "",
    onGatewayUrlChange: (String) -> Unit = {},
    onCheckUpdates: () -> Unit = {},
    onReadHelp: () -> Unit,
    onDismiss: () -> Unit
) {
    var gatewayText by remember { mutableStateOf(currentGatewayUrl) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(BlindSightCard)
                .border(2.dp, BlindSightCardEdge, RoundedCornerShape(20.dp))
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = BlindSightAmber,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Settings",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = BlindSightTextPrimary
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.semantics {
                        role = Role.Button
                        contentDescription = "Close settings dialog."
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = BlindSightTextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Automatic Language & Voice Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0F172A))
                    .border(1.dp, BlindSightCardEdge, RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF10B981))
                        )
                        Text(
                            text = "Auto Language & Voice Active",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = BlindSightTextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "BlindSight automatically detects whether you speak in English or Hindi, and responds using clear, professional human speech.",
                        fontSize = 12.sp,
                        color = BlindSightTextMuted,
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Text Size
            Text(
                text = "Text Size",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = BlindSightTextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val sizes = listOf(1.0f to "A Normal", 1.2f to "A+ Large", 1.45f to "A++ Extra")
                sizes.forEach { (scale, label) ->
                    val isSelected = currentTextSize == scale
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) BlindSightAmber else Color(0xFF05070C))
                            .border(1.dp, if (isSelected) BlindSightAmber else BlindSightCardEdge, RoundedCornerShape(10.dp))
                            .clickable { onTextSizeChange(scale) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color.Black else BlindSightTextPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // High Contrast Yellow mode
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF05070C))
                    .border(1.dp, BlindSightCardEdge, RoundedCornerShape(10.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Yellow-on-Black Mode",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = BlindSightTextPrimary
                    )
                    Text(
                        text = "High luminance yellow for severe cataracts/low vision",
                        fontSize = 12.sp,
                        color = BlindSightTextMuted
                    )
                }

                Switch(
                    checked = highContrastYellow,
                    onCheckedChange = { onToggleHighContrast() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = BlindSightAmber,
                        checkedTrackColor = BlindSightAmber.copy(alpha = 0.5f)
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Mobile Back Camera Fallback & Privacy Setting
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF05070C))
                    .border(1.dp, BlindSightCardEdge, RoundedCornerShape(10.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Mobile Camera Fallback",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = BlindSightTextPrimary
                    )
                    Text(
                        text = "Uses phone's back camera when glasses are disconnected, announcing 'Using your mobile back camera'.",
                        fontSize = 12.sp,
                        color = BlindSightTextMuted
                    )
                }

                Switch(
                    checked = isPhoneCameraFallbackEnabled,
                    onCheckedChange = { onTogglePhoneCameraFallback() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = BlindSightAmber,
                        checkedTrackColor = BlindSightAmber.copy(alpha = 0.5f)
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Hardware Glasses Gateway IP
            Text(
                text = "Glasses Gateway URL (v1.5 Cam)",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = BlindSightTextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "IP where ESP32 uploads frames (e.g. http://192.168.29.173:5000)",
                fontSize = 12.sp,
                color = BlindSightTextMuted
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = gatewayText,
                    onValueChange = { gatewayText = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF05070C),
                        unfocusedContainerColor = Color(0xFF05070C),
                        focusedTextColor = BlindSightTextPrimary,
                        unfocusedTextColor = BlindSightTextPrimary,
                        focusedIndicatorColor = BlindSightAmber,
                        unfocusedIndicatorColor = BlindSightCardEdge
                    )
                )
                Button(
                    onClick = { onGatewayUrlChange(gatewayText) },
                    colors = ButtonDefaults.buttonColors(containerColor = BlindSightAmber, contentColor = Color.Black),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Check for updates
            Button(
                onClick = {
                    onDismiss()
                    onCheckUpdates()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = BlindSightAmber.copy(alpha = 0.2f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = BlindSightAmber,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(text = "Check for App Updates", color = BlindSightAmber, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Read help commands out loud
            Button(
                onClick = onReadHelp,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = BlindSightCardEdge),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.HelpOutline,
                    contentDescription = null,
                    tint = BlindSightAmber,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(text = "Speak All Commands Help", color = BlindSightTextPrimary, fontWeight = FontWeight.Bold)
            }
        }
    }
}
