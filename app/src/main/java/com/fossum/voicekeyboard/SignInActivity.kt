package com.fossum.voicekeyboard

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.fossum.voicekeyboard.databinding.ActivitySignInBinding
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Handles Google Sign-In and stores the OAuth2 access token so the
 * [VoiceKeyboardService] can call the Gemini API on behalf of the user.
 *
 * The scope [SCOPE_GENERATIVE_LANGUAGE] grants access to the Gemini
 * Generative Language API using the user's own Google account quota.
 */
class SignInActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "SignInActivity"
        private const val RC_SIGN_IN = 9001

        /** OAuth2 scope required to call the Gemini Generative Language API. */
        const val SCOPE_GENERATIVE_LANGUAGE =
            "https://www.googleapis.com/auth/generative-language"
    }

    private lateinit var binding: ActivitySignInBinding
    private lateinit var googleSignInClient: GoogleSignInClient
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignInBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(SCOPE_GENERATIVE_LANGUAGE))
            .requestServerAuthCode(getString(R.string.default_web_client_id), false)
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // If already signed in, show current account and offer sign-out
        val account = GoogleSignIn.getLastSignedInAccount(this)
        if (account != null && TokenStore.hasValidToken(this)) {
            showSignedInUi(account.email ?: "")
        } else {
            showSignedOutUi()
        }

        binding.btnGoogleSignIn.setOnClickListener {
            startSignIn()
        }

        binding.btnSignOut.setOnClickListener {
            signOut()
        }
    }

    private fun startSignIn() {
        binding.progressBar.visibility = View.VISIBLE
        binding.btnGoogleSignIn.isEnabled = false
        val signInIntent = googleSignInClient.signInIntent
        @Suppress("DEPRECATION")
        startActivityForResult(signInIntent, RC_SIGN_IN)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_SIGN_IN) {
            handleSignInResult(data)
        }
    }

    private fun handleSignInResult(data: Intent?) {
        scope.launch {
            try {
                val task = GoogleSignIn.getSignedInAccountFromIntent(data)
                val account = task.getResult(ApiException::class.java)
                val email = account?.email ?: ""

                // Retrieve the OAuth2 access token for the signed-in account.
                // GoogleAuthUtil.getToken is a blocking call; run on IO dispatcher.
                val token = fetchAccessToken(account)
                if (token != null) {
                    TokenStore.saveToken(this@SignInActivity, token, email)
                    showSignedInUi(email)
                    Toast.makeText(
                        this@SignInActivity,
                        getString(R.string.sign_in_success, email),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    showError(getString(R.string.sign_in_token_error))
                }
            } catch (e: ApiException) {
                val msg = when (e.statusCode) {
                    GoogleSignInStatusCodes.SIGN_IN_CANCELLED ->
                        getString(R.string.sign_in_cancelled)
                    GoogleSignInStatusCodes.NETWORK_ERROR ->
                        getString(R.string.sign_in_network_error)
                    else -> getString(R.string.sign_in_failed, e.statusCode)
                }
                Log.w(TAG, "Sign-in failed: code=${e.statusCode}", e)
                showError(msg)
            } finally {
                binding.progressBar.visibility = View.GONE
                binding.btnGoogleSignIn.isEnabled = true
            }
        }
    }

    private suspend fun fetchAccessToken(account: GoogleSignInAccount?): String? {
        return try {
            kotlinx.coroutines.withContext(Dispatchers.IO) {
                val googleAccount = account?.account ?: return@withContext null
                com.google.android.gms.auth.GoogleAuthUtil.getToken(
                    applicationContext,
                    googleAccount,
                    "oauth2:$SCOPE_GENERATIVE_LANGUAGE"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch access token", e)
            null
        }
    }

    private fun signOut() {
        scope.launch {
            try {
                googleSignInClient.signOut().await()
                TokenStore.clearToken(this@SignInActivity)
                showSignedOutUi()
                Toast.makeText(
                    this@SignInActivity,
                    getString(R.string.signed_out),
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Log.e(TAG, "Sign-out failed", e)
            }
        }
    }

    private fun showSignedInUi(email: String) {
        binding.tvAccountEmail.text = getString(R.string.signed_in_as, email)
        binding.tvAccountEmail.visibility = View.VISIBLE
        binding.btnGoogleSignIn.visibility = View.GONE
        binding.btnSignOut.visibility = View.VISIBLE
        binding.tvSignInPrompt.visibility = View.GONE
    }

    private fun showSignedOutUi() {
        binding.tvAccountEmail.visibility = View.GONE
        binding.btnGoogleSignIn.visibility = View.VISIBLE
        binding.btnSignOut.visibility = View.GONE
        binding.tvSignInPrompt.visibility = View.VISIBLE
    }

    private fun showError(message: String) {
        binding.progressBar.visibility = View.GONE
        binding.btnGoogleSignIn.isEnabled = true
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
