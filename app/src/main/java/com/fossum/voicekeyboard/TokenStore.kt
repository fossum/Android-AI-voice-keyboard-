package com.fossum.voicekeyboard

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Stores and retrieves the OAuth2 access token obtained from Google Sign-In.
 * Tokens are kept in private SharedPreferences (MODE_PRIVATE). No token is
 * ever committed to source control. For stronger protection consider migrating
 * to EncryptedSharedPreferences in a production build.
 */
object TokenStore {

    private const val PREFS_NAME = "voice_keyboard_prefs"
    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_ACCOUNT_EMAIL = "account_email"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveToken(context: Context, token: String, email: String) {
        prefs(context).edit {
            putString(KEY_ACCESS_TOKEN, token)
            putString(KEY_ACCOUNT_EMAIL, email)
        }
    }

    fun getToken(context: Context): String? =
        prefs(context).getString(KEY_ACCESS_TOKEN, null)

    fun getEmail(context: Context): String? =
        prefs(context).getString(KEY_ACCOUNT_EMAIL, null)

    fun hasValidToken(context: Context): Boolean =
        !getToken(context).isNullOrEmpty()

    fun clearToken(context: Context) {
        prefs(context).edit {
            remove(KEY_ACCESS_TOKEN)
            remove(KEY_ACCOUNT_EMAIL)
        }
    }
}
