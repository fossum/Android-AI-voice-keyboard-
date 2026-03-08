package com.fossum.voicekeyboard

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import com.fossum.voicekeyboard.databinding.ActivityMainBinding

/**
 * Launcher activity that guides the user through the three required setup steps:
 *  1. Enable the Voice Keyboard in Android system settings.
 *  2. Select it as the active input method.
 *  3. Sign in with their Google account so Gemini AI tokens are obtained.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnEnableKeyboard.setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }

        binding.btnSelectKeyboard.setOnClickListener {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        }

        binding.btnSignIn.setOnClickListener {
            startActivity(Intent(this, SignInActivity::class.java))
        }

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        updateSetupStatus()
    }

    private fun updateSetupStatus() {
        val isEnabled = isKeyboardEnabled()
        val isSignedIn = TokenStore.hasValidToken(this)

        binding.tvStatusEnabled.text = if (isEnabled)
            getString(R.string.status_enabled)
        else
            getString(R.string.status_not_enabled)

        binding.tvStatusSignedIn.text = if (isSignedIn)
            getString(R.string.status_signed_in)
        else
            getString(R.string.status_not_signed_in)
    }

    private fun isKeyboardEnabled(): Boolean {
        val enabledIds = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_INPUT_METHODS
        ) ?: return false
        val packageName = packageName
        return enabledIds.split(":").any { it.startsWith(packageName) }
    }
}
