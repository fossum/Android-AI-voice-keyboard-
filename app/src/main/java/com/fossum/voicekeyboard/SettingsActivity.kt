package com.fossum.voicekeyboard

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.fossum.voicekeyboard.databinding.ActivitySettingsBinding

/**
 * Settings screen for the Voice Keyboard.
 * Allows the user to sign in / sign out and view their account status.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.btnManageAccount.setOnClickListener {
            startActivity(
                android.content.Intent(this, SignInActivity::class.java)
            )
        }

        binding.btnClearToken.setOnClickListener {
            TokenStore.clearToken(this)
            updateAccountStatus()
            Toast.makeText(this, getString(R.string.token_cleared), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        updateAccountStatus()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun updateAccountStatus() {
        val email = TokenStore.getEmail(this)
        val hasToken = TokenStore.hasValidToken(this)

        if (hasToken && email != null) {
            binding.tvAccountStatus.text = getString(R.string.signed_in_as, email)
            binding.btnClearToken.visibility = View.VISIBLE
        } else {
            binding.tvAccountStatus.text = getString(R.string.status_not_signed_in)
            binding.btnClearToken.visibility = View.GONE
        }
    }
}
