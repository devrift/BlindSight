package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.ui.theme.BlindSightCard
import com.example.ui.theme.BlindSightCardEdge
import com.example.ui.theme.BlindSightTextMuted
import com.example.ui.theme.BlindSightTextPrimary

@Composable
fun LanguageSelectorRow(
    currentLanguage: SupportedLanguage,
    onLanguageSelect: (SupportedLanguage) -> Unit,
    activeColor: Color = BlindSightAmber,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(BlindSightCard)
            .border(1.dp, BlindSightCardEdge, RoundedCornerShape(14.dp))
            .padding(4.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SupportedLanguage.entries.forEach { lang ->
            val isSelected = currentLanguage == lang

            val pillBg by animateColorAsState(
                targetValue = if (isSelected) activeColor else Color.Transparent,
                animationSpec = tween(200),
                label = "pillBg"
            )

            val textColor by animateColorAsState(
                targetValue = if (isSelected) Color.Black else BlindSightTextMuted,
                animationSpec = tween(200),
                label = "textColor"
            )

            val displayName = when (lang) {
                SupportedLanguage.ENGLISH -> "English"
                SupportedLanguage.HINDI -> "हिंदी-उर्दू"
                SupportedLanguage.PUNJABI -> "ਪੰਜਾਬੀ"
                SupportedLanguage.BENGALI -> "বাংলা"
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(pillBg)
                    .clickable { onLanguageSelect(lang) }
                    .padding(horizontal = 14.dp, vertical = 9.dp)
                    .semantics {
                        role = Role.Button
                        contentDescription = "${lang.label} language. ${if (isSelected) "Active" else "Double tap to switch"}"
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = displayName,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                    color = textColor
                )
            }
        }
    }
}
