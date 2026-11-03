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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import com.example.ui.theme.BlindSightAmber
import com.example.ui.theme.BlindSightCard
import com.example.ui.theme.BlindSightCardEdge
import com.example.ui.theme.BlindSightTextMuted
import com.example.ui.theme.BlindSightTextPrimary

@Composable
fun VisionHelperView(
    isScanning: Boolean,
    onDescribeScene: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(BlindSightCard)
            .border(1.dp, BlindSightCardEdge, RoundedCornerShape(18.dp))
            .padding(16.dp)
            .semantics {
                contentDescription = "Camera vision assistant panel. Double tap describe button to inspect what is ahead."
            }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Visibility,
                contentDescription = null,
                tint = BlindSightAmber,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = "Tier 1 & 2: Camera Vision",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = BlindSightTextPrimary
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Main action button (large touch area for blind users)
        Button(
            onClick = onDescribeScene,
            enabled = !isScanning,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .semantics {
                    role = Role.Button
                    contentDescription = "Describe current scene. Takes a camera snapshot and speaks the visual layout."
                },
            colors = ButtonDefaults.buttonColors(
                containerColor = BlindSightAmber,
                contentColor = Color.Black
            ),
            shape = RoundedCornerShape(14.dp)
        ) {
            if (isScanning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Color.Black,
                    strokeWidth = 3.dp
                )
                Spacer(modifier = Modifier.size(10.dp))
                Text(
                    text = "Analyzing Scene…",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = null,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.size(10.dp))
                Text(
                    text = "Describe What's In Front Of Me",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Ambient lighting and perception indicator
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF05070C))
                .border(1.dp, BlindSightCardEdge, RoundedCornerShape(10.dp))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(BlindSightAmber.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.LightMode,
                    contentDescription = null,
                    tint = BlindSightAmber,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column {
                Text(
                    text = "Ambient Light: Good Daylight",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = BlindSightTextPrimary
                )
                Text(
                    text = "High clarity for object and doorway detection.",
                    fontSize = 12.sp,
                    color = BlindSightTextMuted
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Voice shortcut: Say \"describe\" or \"what is around me\" at any time.",
            fontSize = 13.sp,
            color = BlindSightTextMuted
        )
    }
}
