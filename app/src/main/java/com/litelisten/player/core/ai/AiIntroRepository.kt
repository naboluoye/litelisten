package com.litelisten.player.core.ai

import android.content.Context
import com.litelisten.player.R
import com.litelisten.player.core.model.Track
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** One AI request = song background + artist intro + lyrics overview, three sections. */
sealed interface AiIntroState {
    data object Idle : AiIntroState
    data object Loading : AiIntroState
    data class Success(val sections: AiIntroParser.Sections) : AiIntroState
    data class Failure(val code: AiErrorCode) : AiIntroState
}

@Singleton
class AiIntroRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: AiChatClient,
) {
    /** Double-delivery of the persona: system_prompt field AND prefixed into the message. */
    private fun systemPrompt(): String = context.getString(R.string.ai_intro_system_prompt)

    fun buildMessage(track: Track): String {
        val persona = systemPrompt()
        val question = context.getString(
            R.string.ai_intro_user_question,
            track.title,
            track.displayArtist,
        )
        return context.getString(R.string.ai_intro_message_template, persona, question)
    }

    suspend fun fetchIntro(track: Track): AiIntroState {
        val result = client.chat(
            message = buildMessage(track),
            systemPrompt = systemPrompt(),
        )
        return when (result) {
            is AiChatResult.Success -> AiIntroState.Success(
                AiIntroParser.parse(
                    reply = result.reply,
                    backgroundMarker = context.getString(R.string.ai_section_background),
                    artistMarker = context.getString(R.string.ai_section_artist),
                    lyricsMarker = context.getString(R.string.ai_section_lyrics),
                ),
            )
            is AiChatResult.Failure -> AiIntroState.Failure(result.code)
        }
    }
}
