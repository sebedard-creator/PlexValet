package com.seb.sdpr.ui.filter

enum class AiChatRole {
    USER,
    ASSISTANT,
    NOTICE
}

data class AiChatMessage(
    val role: AiChatRole,
    val text: String
)
