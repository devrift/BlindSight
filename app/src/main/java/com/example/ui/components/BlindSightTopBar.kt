package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.example.data.model.SupportedLanguage
import com.example.ui.theme.BlindSightAmber
import com.example.ui.theme.BlindSightBad
import com.example.ui.theme.BlindSightCard
import com.example.ui.theme.BlindSightCardEdge
import com.example.ui.theme.BlindSightOk
import com.example.ui.theme.BlindSightTextMuted
import com.example.ui.theme.BlindSightTextPrimary

@Composable
fun BlindSightTopBar(
    language: SupportedLanguage,
    hasUpdateAvailable: Boolean = false,
    onCheckUpdates: () -> Unit,
    onStopClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // App title & eye icon
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(BlindSightAmber.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Hearing,
                    contentDescription = null,
                    tint = BlindSightAmber,
                    modifier = Modifier.size(24.dp)
                )
            }
            Text(
                text = "BlindSight",
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = BlindSightTextPrimary,
                letterSpacing = 0.5.sp
            )
        }

        // Action chips
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Self-updater button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (hasUpdateAvailable) BlindSightOk.copy(alpha = 0.2f) else BlindSightCard)
                    .border(
                        1.dp,
                        if (hasUpdateAvailable) BlindSightOk else BlindSightCardEdge,
                        RoundedCornerShape(20.dp)
                    )
                    .clickable { onCheckUpdates() }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .semantics {
                        role = Role.Button
                        contentDescription = if (hasUpdateAvailable) "Update available! Double tap to install." else "Check for app updates."
                    }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription = null,
                        tint = if (hasUpdateAvailable) BlindSightOk else BlindSightTextMuted,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = if (hasUpdateAvailable) "Update!" else "v${com.example.BuildConfig.VERSION_NAME}",
                        color = if (hasUpdateAvailable) BlindSightOk else BlindSightTextMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Quick silence stop button
            IconButton(
                onClick = onStopClick,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(BlindSightBad.copy(alpha = 0.2f))
                    .semantics {
                        role = Role.Button
                        contentDescription = "Silence assistant immediately."
                    }
            ) {
                Icon(
                    imageVector = Icons.Default.StopCircle,
                    contentDescription = null,
                    tint = BlindSightBad,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Settings button
            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(BlindSightCard)
                    .semantics {
                        role = Role.Button
                        contentDescription = "Open BlindSight settings."
                    }
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = BlindSightAmber,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
