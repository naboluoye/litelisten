package com.litelisten.player.core.ai

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AiChatClientTest {

    @Test
    fun `success envelope returns reply`() {
        val body = """{"code":0,"data":{"reply":"hello world"},"msg":""}"""
        val result = AiChatClient.parseEnvelope(200, body)
        assertThat(result).isInstanceOf(AiChatResult.Success::class.java)
        assertThat((result as AiChatResult.Success).reply).isEqualTo("hello world")
    }

    @Test
    fun `code 403 maps to auth failure`() {
        val result = AiChatClient.parseEnvelope(200, """{"code":403,"msg":"bad key","data":null}""")
        assertThat((result as AiChatResult.Failure).code).isEqualTo(AiErrorCode.AUTH)
    }

    @Test
    fun `code 429 maps to rate failure`() {
        val result = AiChatClient.parseEnvelope(200, """{"code":429,"msg":"too fast","data":null}""")
        assertThat((result as AiChatResult.Failure).code).isEqualTo(AiErrorCode.RATE)
    }

    @Test
    fun `code 400 maps to bad request`() {
        val result = AiChatClient.parseEnvelope(200, """{"code":400,"msg":"too long","data":null}""")
        assertThat((result as AiChatResult.Failure).code).isEqualTo(AiErrorCode.BAD_REQUEST)
    }

    @Test
    fun `code 500 maps to server failure`() {
        val result = AiChatClient.parseEnvelope(200, """{"code":500,"msg":"boom","data":null}""")
        assertThat((result as AiChatResult.Failure).code).isEqualTo(AiErrorCode.SERVER)
    }

    @Test
    fun `http 502 with non json body maps to server failure`() {
        val result = AiChatClient.parseEnvelope(502, "Bad Gateway")
        assertThat((result as AiChatResult.Failure).code).isEqualTo(AiErrorCode.SERVER)
    }

    @Test
    fun `http 200 with non json body maps to network failure`() {
        val result = AiChatClient.parseEnvelope(200, "not json at all")
        assertThat((result as AiChatResult.Failure).code).isEqualTo(AiErrorCode.NETWORK)
    }

    @Test
    fun `empty reply maps to empty failure`() {
        val result = AiChatClient.parseEnvelope(200, """{"code":0,"data":{"reply":""},"msg":""}""")
        assertThat((result as AiChatResult.Failure).code).isEqualTo(AiErrorCode.EMPTY)
    }

    @Test
    fun `unknown code below 500 maps to network failure`() {
        val result = AiChatClient.parseEnvelope(200, """{"code":418,"msg":"teapot","data":null}""")
        assertThat((result as AiChatResult.Failure).code).isEqualTo(AiErrorCode.NETWORK)
    }
}
