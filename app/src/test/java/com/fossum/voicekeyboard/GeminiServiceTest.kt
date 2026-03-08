package com.fossum.voicekeyboard

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [GeminiService].
 * OkHttpClient is mocked so no real network calls are made.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GeminiServiceTest {

    private val accessToken = "test_access_token"

    @Before
    fun setUp() {
        mockkConstructor(OkHttpClient::class)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `enhance returns polished sentence on successful Gemini response`() = runTest {
        val geminiResponseJson = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      { "text": "The weather looks nice today." }
                    ],
                    "role": "model"
                  },
                  "finishReason": "STOP"
                }
              ]
            }
        """.trimIndent()

        val mockCall = mockk<Call>()
        val fakeResponse = Response.Builder()
            .request(Request.Builder().url("https://example.com").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(geminiResponseJson.toResponseBody())
            .build()

        every { anyConstructed<OkHttpClient>().newCall(any()) } returns mockCall
        every { mockCall.execute() } returns fakeResponse

        val service = GeminiService(accessToken)
        val result = service.enhance("weather nice today")

        assertEquals("The weather looks nice today.", result)
    }

    @Test
    fun `enhance throws GeminiException on HTTP error response`() = runTest {
        val errorJson = """{"error":{"code":401,"message":"Request had invalid authentication credentials."}}"""

        val mockCall = mockk<Call>()
        val fakeResponse = Response.Builder()
            .request(Request.Builder().url("https://example.com").build())
            .protocol(Protocol.HTTP_1_1)
            .code(401)
            .message("Unauthorized")
            .body(errorJson.toResponseBody())
            .build()

        every { anyConstructed<OkHttpClient>().newCall(any()) } returns mockCall
        every { mockCall.execute() } returns fakeResponse

        val service = GeminiService(accessToken)
        try {
            service.enhance("test input")
            assertTrue("Expected GeminiException to be thrown", false)
        } catch (e: GeminiException) {
            assertTrue(e.message?.contains("401") == true || e.message?.contains("error") == true)
        }
    }

    @Test
    fun `enhance throws GeminiException on network failure`() = runTest {
        val mockCall = mockk<Call>()

        every { anyConstructed<OkHttpClient>().newCall(any()) } returns mockCall
        every { mockCall.execute() } throws java.io.IOException("Network unreachable")

        val service = GeminiService(accessToken)
        try {
            service.enhance("test input")
            assertTrue("Expected GeminiException to be thrown", false)
        } catch (e: GeminiException) {
            assertTrue(e.message?.contains("Network") == true || e.cause is java.io.IOException)
        }
    }

    @Test
    fun `enhance throws GeminiException on malformed JSON response`() = runTest {
        val malformedJson = """{"unexpected":"format"}"""

        val mockCall = mockk<Call>()
        val fakeResponse = Response.Builder()
            .request(Request.Builder().url("https://example.com").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(malformedJson.toResponseBody())
            .build()

        every { anyConstructed<OkHttpClient>().newCall(any()) } returns mockCall
        every { mockCall.execute() } returns fakeResponse

        val service = GeminiService(accessToken)
        try {
            service.enhance("test input")
            assertTrue("Expected GeminiException to be thrown", false)
        } catch (e: GeminiException) {
            assertTrue(e.message?.contains("format") == true || e.cause != null)
        }
    }

    @Test
    fun `enhance trims whitespace from Gemini response`() = runTest {
        val geminiResponseJson = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      { "text": "  Hello world.  \n" }
                    ]
                  }
                }
              ]
            }
        """.trimIndent()

        val mockCall = mockk<Call>()
        val fakeResponse = Response.Builder()
            .request(Request.Builder().url("https://example.com").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(geminiResponseJson.toResponseBody())
            .build()

        every { anyConstructed<OkHttpClient>().newCall(any()) } returns mockCall
        every { mockCall.execute() } returns fakeResponse

        val service = GeminiService(accessToken)
        val result = service.enhance("hello world")

        assertEquals("Hello world.", result)
    }
}
