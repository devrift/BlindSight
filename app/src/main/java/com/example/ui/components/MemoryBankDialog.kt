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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import com.example.data.model.MemoryFact
import com.example.ui.theme.BlindSightAmber
import com.example.ui.theme.BlindSightBad
import com.example.ui.theme.BlindSightCard
import com.example.ui.theme.BlindSightCardEdge
import com.example.ui.theme.BlindSightTextMuted
import com.example.ui.theme.BlindSightTextPrimary

@Composable
fun MemoryBankDialog(
    facts: List<MemoryFact>,
    onAddFact: (String) -> Unit,
    onForgetFact: (String) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit
) {
    var newFactText by remember { mutableStateOf("") }

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
                        imageVector = Icons.Default.Bookmark,
                        contentDescription = null,
                        tint = BlindSightAmber,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Memory Bank",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = BlindSightTextPrimary
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.semantics {
                        role = Role.Button
                        contentDescription = "Close memory bank dialog."
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        tint = BlindSightTextMuted
                    )
                }
            }

            Text(
                text = "Facts you teach BlindSight (up to 20) persist on this device and guide all answers.",
                fontSize = 13.sp,
                color = BlindSightTextMuted,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Add new fact input
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newFactText,
                    onValueChange = { newFactText = it },
                    placeholder = { Text("e.g. My keys are on the hook", color = BlindSightTextMuted) },
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BlindSightAmber,
                        unfocusedBorderColor = BlindSightCardEdge,
                        focusedTextColor = BlindSightTextPrimary,
                        unfocusedTextColor = BlindSightTextPrimary
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                Button(
                    onClick = {
                        if (newFactText.isNotBlank()) {
                            onAddFact(newFactText)
                            newFactText = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BlindSightAmber),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.semantics {
                        role = Role.Button
                        contentDescription = "Save fact to memory."
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = Color.Black
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Saved facts list
            if (facts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No saved memories yet.\nSay: \"remember that my dog is Bruno\"",
                        color = BlindSightTextMuted,
                        fontSize = 14.sp
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    facts.forEach { fact ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF05070C))
                                .border(1.dp, BlindSightCardEdge, RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = fact.text,
                                fontSize = 14.sp,
                                color = BlindSightTextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { onForgetFact(fact.text) },
                                modifier = Modifier.semantics {
                                    role = Role.Button
                                    contentDescription = "Forget fact: ${fact.text}"
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = BlindSightBad,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = onClearAll,
                    colors = ButtonDefaults.buttonColors(containerColor = BlindSightBad.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            role = Role.Button
                            contentDescription = "Forget all saved memories."
                        }
                ) {
                    Text(text = "Forget All Memories", color = BlindSightBad, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
