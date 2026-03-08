package com.fossum.voicekeyboard

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [TokenStore].
 */
class TokenStoreTest {

    private lateinit var mockContext: Context
    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor

    @Before
    fun setUp() {
        mockEditor = mockk(relaxed = true)
        every { mockEditor.putString(any(), any()) } returns mockEditor
        every { mockEditor.remove(any()) } returns mockEditor
        every { mockEditor.apply() } returns Unit

        mockPrefs = mockk()
        every { mockPrefs.edit() } returns mockEditor

        mockContext = mockk()
        every {
            mockContext.getSharedPreferences("voice_keyboard_prefs", Context.MODE_PRIVATE)
        } returns mockPrefs
    }

    @Test
    fun `hasValidToken returns false when no token is stored`() {
        every { mockPrefs.getString("access_token", null) } returns null

        assertFalse(TokenStore.hasValidToken(mockContext))
    }

    @Test
    fun `hasValidToken returns false when token is empty string`() {
        every { mockPrefs.getString("access_token", null) } returns ""

        assertFalse(TokenStore.hasValidToken(mockContext))
    }

    @Test
    fun `hasValidToken returns true when a token is stored`() {
        every { mockPrefs.getString("access_token", null) } returns "my_token_123"

        assertTrue(TokenStore.hasValidToken(mockContext))
    }

    @Test
    fun `getToken returns stored token`() {
        every { mockPrefs.getString("access_token", null) } returns "token_abc"

        assertEquals("token_abc", TokenStore.getToken(mockContext))
    }

    @Test
    fun `getToken returns null when nothing is stored`() {
        every { mockPrefs.getString("access_token", null) } returns null

        assertNull(TokenStore.getToken(mockContext))
    }

    @Test
    fun `getEmail returns stored email`() {
        every { mockPrefs.getString("account_email", null) } returns "user@example.com"

        assertEquals("user@example.com", TokenStore.getEmail(mockContext))
    }

    @Test
    fun `saveToken stores token and email`() {
        TokenStore.saveToken(mockContext, "tok123", "user@example.com")

        verify { mockEditor.putString("access_token", "tok123") }
        verify { mockEditor.putString("account_email", "user@example.com") }
    }

    @Test
    fun `clearToken removes token and email`() {
        TokenStore.clearToken(mockContext)

        verify { mockEditor.remove("access_token") }
        verify { mockEditor.remove("account_email") }
    }
}
