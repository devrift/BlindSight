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
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import com.example.data.model.ListenMode
import com.example.data.model.SupportedLanguage
import com.example.ui.theme.BlindSightAmber
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
    onDismiss: () -> Unit,
    geminiApiKey: String = "",
    onGeminiApiKeyChange: (String) -> Unit = {}
) {
    var keyInput by remember { mutableStateOf(geminiApiKey) }

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

            // Google Gemini API Key Input
            Text(
                text = "Gemini API Key (Vision & Deep QA)",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = BlindSightTextPrimary
            )
            Text(
                text = "Used for camera scene descriptions. Falls back to offline local mode if empty.",
                fontSize = 12.sp,
                color = BlindSightTextMuted
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = keyInput,
                onValueChange = { keyInput = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Paste Gemini API Key", color = BlindSightTextMuted, fontSize = 12.sp) },
                singleLine = true,
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Key, contentDescription = null, tint = BlindSightAmber)
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BlindSightAmber,
                    unfocusedBorderColor = BlindSightCardEdge,
                    focusedTextColor = BlindSightTextPrimary,
                    unfocusedTextColor = BlindSightTextPrimary,
                    cursorColor = BlindSightAmber
                ),
                shape = RoundedCornerShape(10.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Button(
                onClick = { onGeminiApiKeyChange(keyInput) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = BlindSightAmber),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(text = "Save Key", color = Color.Black, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Language Selection
            Text(
                text = "Spoken Language",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = BlindSightTextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            SupportedLanguage.entries.forEach { lang ->
                val isSelected = currentLanguage == lang
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) BlindSightAmber.copy(alpha = 0.2f) else Color.Transparent)
                        .clickable { onLanguageChange(lang) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = lang.label,
                        fontSize = 14.sp,
                        color = if (isSelected) BlindSightAmber else BlindSightTextPrimary,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                    if (isSelected) {
                        Text(text = "Active", fontSize = 12.sp, color = BlindSightAmber, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Listening Mode
            Text(
                text = "Listening Mode",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = BlindSightTextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            ListenMode.entries.forEach { mode ->
                val isSelected = currentMode == mode
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) BlindSightAmber.copy(alpha = 0.2f) else Color.Transparent)
                        .clickable { onModeChange(mode) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = mode.displayName,
                            fontSize = 14.sp,
                            color = if (isSelected) BlindSightAmber else BlindSightTextPrimary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                        Text(
                            text = mode.description,
                            fontSize = 11.sp,
                            color = BlindSightTextMuted
                        )
                    }
                    if (isSelected) {
                        Text(text = "Active", fontSize = 12.sp, color = BlindSightAmber, fontWeight = FontWeight.Bold)
                    }
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
