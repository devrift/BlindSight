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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.example.data.model.ListenMode
import com.example.data.model.SupportedLanguage
import com.example.ui.theme.BlindSightAmber
import com.example.ui.theme.BlindSightAmberStrong
import com.example.ui.theme.BlindSightCard
import com.example.ui.theme.BlindSightCardEdge
import com.example.ui.theme.BlindSightTextMuted
import com.example.ui.theme.BlindSightTextPrimary

@Composable
fun SettingsDialog(
    currentLanguage: SupportedLanguage,
    onLanguageChange: (SupportedLanguage) -> Unit,
    currentMode: ListenMode,
    onModeChange: (ListenMode) -> Unit,
    currentTextSize: Float,
    onTextSizeChange: (Float) -> Unit,
    highContrastYellow: Boolean,
    onToggleHighContrast: () -> Unit,
    onReadHelp: () -> Unit,
    onDismiss: () -> Unit
) {
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
                        contentDescription = null,
                        tint = BlindSightTextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Reply Language
            Text(
                text = "Reply Language",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = BlindSightTextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SupportedLanguage.entries.forEach { lang ->
                    val isSelected = lang == currentLanguage
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) BlindSightAmber.copy(alpha = 0.2f) else Color(0xFF05070C))
                            .border(1.dp, if (isSelected) BlindSightAmber else BlindSightCardEdge, RoundedCornerShape(10.dp))
                            .clickable { onLanguageChange(lang) }
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                            .semantics {
                                role = Role.RadioButton
                                contentDescription = "${lang.label}. ${if (isSelected) "Selected" else "Double tap to select"}"
                            },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = lang.label,
                            color = if (isSelected) BlindSightAmberStrong else BlindSightTextPrimary,
                            fontSize = 15.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                        if (isSelected) {
                            Text(text = "✓", color = BlindSightAmber, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Listening Mode
            Text(
                text = "Listening Mode",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = BlindSightTextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ListenMode.entries.forEach { mode ->
                    val isSelected = mode == currentMode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) BlindSightAmber else Color(0xFF05070C))
                            .border(1.dp, if (isSelected) BlindSightAmber else BlindSightCardEdge, RoundedCornerShape(10.dp))
                            .clickable { onModeChange(mode) }
                            .padding(vertical = 10.dp, horizontal = 4.dp)
                            .semantics {
                                role = Role.RadioButton
                                contentDescription = "${mode.displayName} mode. ${mode.hint}."
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = mode.displayName,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color.Black else BlindSightTextPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Text Size switcher
            Text(
                text = "Text Scaling",
                fontSize = 15.sp,
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

            Spacer(modifier = Modifier.height(16.dp))

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
