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

/** 歌词讲解：直接返回一段可读文本，不再强制分段。 */
sealed interface AiLyricsState {
    data object Idle : AiLyricsState
    data object Loading : AiLyricsState
    data class Success(val text: String) : AiLyricsState
    data class Failure(val code: AiErrorCode) : AiLyricsState
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

    /** 歌词讲解的人设：只讲理解，不复述原文，避免模型「补」出不存在的歌词。 */
    private fun lyricsSystemPrompt(): String =
        context.getString(R.string.ai_lyrics_system_prompt)

    fun buildLyricsMessage(track: Track, lyrics: String): String {
        val persona = lyricsSystemPrompt()
        val trimmed = lyrics.trim()
        // 没有歌词原文时照样能讲：退化成「这首歌大致在唱什么」，并明确告诉模型别编歌词。
        val question = if (trimmed.isEmpty()) {
            context.getString(
                R.string.ai_lyrics_user_question_no_text,
                track.title,
                track.displayArtist,
            )
        } else {
            context.getString(
                R.string.ai_lyrics_user_question,
                track.title,
                track.displayArtist,
                trimmed.take(MAX_LYRICS_CHARS),
            )
        }
        return context.getString(R.string.ai_intro_message_template, persona, question)
    }

    suspend fun explainLyrics(track: Track, lyrics: String): AiLyricsState {
        val result = client.chat(
            message = buildLyricsMessage(track, lyrics),
            systemPrompt = lyricsSystemPrompt(),
        )
        return when (result) {
            is AiChatResult.Success -> AiLyricsState.Success(result.reply.trim())
            is AiChatResult.Failure -> AiLyricsState.Failure(result.code)
        }
    }

    private companion object {
        const val MAX_LYRICS_CHARS = 1_200
    }
}
