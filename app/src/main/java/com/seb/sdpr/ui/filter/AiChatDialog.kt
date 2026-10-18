package com.seb.sdpr.ui.filter

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.seb.sdpr.R
import com.seb.sdpr.ui.theme.PlexBorderLight
import com.seb.sdpr.ui.theme.PlexOrange
import com.seb.sdpr.ui.theme.PlexOrangeDim
import com.seb.sdpr.ui.theme.PlexSurface
import com.seb.sdpr.ui.theme.PlexSurface2
import com.seb.sdpr.ui.theme.PlexTextHint
import com.seb.sdpr.ui.theme.PlexTextMuted
import com.seb.sdpr.ui.theme.PlexTextPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatDialog(
    messages: List<AiChatMessage>,
    draft: String,
    isLoading: Boolean,
    candidateCount: Int,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onDismiss: () -> Unit
) {
    val listState = rememberLazyListState()
    val itemCount = messages.size + if (isLoading) 1 else 0

    LaunchedEffect(itemCount) {
        if (itemCount > 0) {
            listState.animateScrollToItem(itemCount - 1)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .heightIn(min = 420.dp, max = 720.dp),
            shape = RoundedCornerShape(20.dp),
            color = PlexSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = PlexOrange,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                stringResource(R.string.app_name),
                                color = PlexTextPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        if (candidateCount > 0) {
                            Text(
                                "$candidateCount films dans la preselction AI",
                                color = PlexTextHint,
                                fontSize = 11.sp
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fermer", tint = PlexTextHint)
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(PlexSurface2)
                        .border(0.5.dp, PlexBorderLight, RoundedCornerShape(14.dp))
                        .padding(12.dp)
                ) {
                    if (messages.isEmpty() && isLoading) {
                        Row(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = PlexOrange,
                                strokeWidth = 2.dp
                            )
                            Text("Analyse de la preselction...", color = PlexTextMuted, fontSize = 13.sp)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            state = listState,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(messages) { message ->
                                AiMessageBubble(message = message)
                            }
                            if (isLoading) {
                                item {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            color = PlexOrange,
                                            strokeWidth = 2.dp
                                        )
                                        Text("Claude reflechit...", color = PlexTextMuted, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = onDraftChange,
                        modifier = Modifier.weight(1f),
                        minLines = 2,
                        maxLines = 5,
                        placeholder = {
                            Text("Ex: j'ai le gout d'avoir peur ce soir", color = PlexTextHint)
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = PlexBorderLight,
                            focusedBorderColor = PlexOrange,
                            unfocusedTextColor = PlexTextPrimary,
                            focusedTextColor = PlexTextPrimary,
                            unfocusedContainerColor = PlexSurface2,
                            focusedContainerColor = PlexSurface2,
                            cursorColor = PlexOrange
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )

                    IconButton(
                        onClick = onSend,
                        enabled = draft.isNotBlank() && !isLoading,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (draft.isNotBlank() && !isLoading) PlexOrange else PlexSurface2)
                            .border(
                                width = 0.5.dp,
                                color = if (draft.isNotBlank() && !isLoading) PlexOrange else PlexBorderLight,
                                shape = RoundedCornerShape(12.dp)
                            )
                    ) {
                        Icon(
                            Icons.Default.ArrowUpward,
                            contentDescription = "Envoyer",
                            tint = if (draft.isNotBlank() && !isLoading) Color(0xFF111111) else PlexTextHint
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AiMessageBubble(message: AiChatMessage) {
    val isUser = message.role == AiChatRole.USER
    val background = when (message.role) {
        AiChatRole.USER -> PlexOrangeDim
        AiChatRole.ASSISTANT -> PlexSurface
        AiChatRole.NOTICE -> PlexSurface
    }
    val border = if (isUser) PlexOrange else PlexBorderLight
    val textColor = if (message.role == AiChatRole.NOTICE) PlexTextMuted else PlexTextPrimary
    val align = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    val label = when (message.role) {
        AiChatRole.USER -> "TOI"
        AiChatRole.ASSISTANT -> "AI"
        AiChatRole.NOTICE -> "INFO"
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = align
    ) {
        Column(
            modifier = Modifier.widthIn(max = 320.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                label,
                color = if (isUser) PlexOrange else PlexTextHint,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = background,
                modifier = Modifier.border(0.5.dp, border, RoundedCornerShape(14.dp))
            ) {
                Text(
                    text = message.text,
                    color = textColor,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                )
            }
        }
    }
}
