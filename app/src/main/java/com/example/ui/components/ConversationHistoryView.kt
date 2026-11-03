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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
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
import com.example.data.model.ChatMessage
import com.example.ui.theme.BlindSightAmber
import com.example.ui.theme.BlindSightCard
import com.example.ui.theme.BlindSightCardEdge
import com.example.ui.theme.BlindSightTextMuted
import com.example.ui.theme.BlindSightTextPrimary

@Composable
fun ConversationHistoryView(
    messages: List<ChatMessage>,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(BlindSightCard)
            .border(1.dp, BlindSightCardEdge, RoundedCornerShape(18.dp))
            .padding(14.dp)
            .semantics {
                contentDescription = "Conversation history. ${messages.size} messages exchanged."
            }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Recent Conversation",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = BlindSightTextPrimary
            )

            if (messages.isNotEmpty()) {
                IconButton(
                    onClick = onClearHistory,
                    modifier = Modifier.semantics {
                        role = Role.Button
                        contentDescription = "Clear all conversation dialogue history."
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = null,
                        tint = BlindSightTextMuted
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (messages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No messages yet. Speak or type to begin.",
                    color = BlindSightTextMuted,
                    fontSize = 14.sp
                )
            }
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                messages.takeLast(6).forEach { msg ->
                    ChatBubble(msg = msg)
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(msg: ChatMessage) {
    val isUser = msg.isUser
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val bg = if (isUser) Color(0xFF1E293B) else Color(0xFF0B1220)
    val borderColor = if (isUser) BlindSightCardEdge else BlindSightAmber

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(bg)
                .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                .padding(10.dp)
                .semantics {
                    contentDescription = "${if (isUser) "You said" else "BlindSight answered"}: ${msg.text}"
                }
        ) {
            Column {
                Text(
                    text = if (isUser) "YOU" else "BLINDSIGHT",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isUser) BlindSightTextMuted else BlindSightAmber
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = msg.text,
                    fontSize = 15.sp,
                    color = BlindSightTextPrimary
                )
            }
        }
    }
}
