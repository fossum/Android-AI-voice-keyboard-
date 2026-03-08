# Android AI Voice Keyboard

An AI-driven voice-to-text keyboard for Android, powered by **Google Gemini AI**.  
Spoken words are converted to text by Android's on-device speech recogniser, then
polished into complete, grammatically correct sentences by Gemini — using the
**user's own Google account** quota (no hardcoded API key required).

---

## Features

| Feature | Details |
|---|---|
| 🎙️ Voice input | Android `SpeechRecognizer` (on-device, no extra cost) |
| 🤖 AI enhancement | Google Gemini Pro via REST API |
| 🔑 Authentication | OAuth2 via Google Sign-In — user's own account tokens |
| ⌨️ IME integration | Full `InputMethodService` — works in any app |
| ⚙️ Settings screen | Account management, sign-in / sign-out |

---

## Architecture

```
MainActivity  ──▶  SignInActivity  (Google Sign-In → OAuth2 token stored in TokenStore)
                                                          │
                                                          ▼
VoiceKeyboardService  (InputMethodService)
  │  1. SpeechRecognizer  ──▶  raw transcript
  │  2. GeminiService     ──▶  enhanced sentence   (uses OAuth2 token from TokenStore)
  └─▶  commitText()  ──▶  focused input field
```

---

## Prerequisites

1. **Android Studio** Hedgehog (2023.1.1) or later.
2. A **Google Cloud project** with the *Generative Language API* enabled.
3. An **OAuth 2.0 Web Client ID** configured in your project (needed for Google Sign-In).

---

## Setup

### 1. Google Cloud Console

1. Go to [console.cloud.google.com](https://console.cloud.google.com).
2. Create or select a project.
3. Enable **Generative Language API** (search for "Generative Language API" in the API library).
4. Go to **APIs & Services → Credentials**.
5. Create an **OAuth 2.0 Client ID** of type **Web application**.
6. Copy the generated Client ID.

### 2. Configure the app

Open `app/src/main/res/values/strings.xml` and replace the placeholder:

```xml
<string name="default_web_client_id" translatable="false">
    YOUR_WEB_CLIENT_ID.apps.googleusercontent.com
</string>
```

> **Note:** `google-services.json` is intentionally excluded from source control
> (see `.gitignore`). Add your own `google-services.json` to `app/` if you also
> want to use Firebase services.

### 3. Build & install

```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 4. Enable the keyboard on your device

1. Open the **AI Voice Keyboard** app.
2. Tap **"Open Input Method Settings"** and toggle the keyboard on.
3. Tap **"Switch Input Method"** and select *AI Voice Keyboard*.
4. Tap **"Sign In with Google"** and sign in — this grants the keyboard
   access to the Gemini API using your own account quota.

### 5. Use the keyboard

Tap the **🎙 microphone** button in the keyboard view.  
Speak naturally.  
The keyboard captures your speech, sends it to Gemini, and types the
AI-enhanced sentence directly into whatever app you're using.

---

## Project structure

```
app/src/main/
├── AndroidManifest.xml
├── java/com/fossum/voicekeyboard/
│   ├── MainActivity.kt          # Setup wizard / launcher
│   ├── SignInActivity.kt        # Google Sign-In + OAuth2 token retrieval
│   ├── VoiceKeyboardService.kt  # InputMethodService (the keyboard itself)
│   ├── GeminiService.kt         # REST client for the Gemini API
│   ├── TokenStore.kt            # SharedPreferences wrapper for the OAuth token
│   └── SettingsActivity.kt      # Keyboard settings screen
└── res/
    ├── layout/
    │   ├── keyboard_view.xml        # Keyboard UI (mic button, preview)
    │   ├── activity_main.xml        # Setup wizard layout
    │   ├── activity_sign_in.xml     # Sign-In layout
    │   └── activity_settings.xml   # Settings layout
    ├── xml/
    │   ├── keyboard_method.xml      # IME descriptor
    │   └── network_security_config.xml
    └── values/
        ├── strings.xml
        ├── colors.xml
        └── themes.xml
```

---

## Running the tests

```bash
./gradlew test
```

Tests cover `GeminiService` (response parsing, error handling) and `TokenStore`
(token persistence) using MockK — no Android framework needed.

---

## Security notes

* The OAuth2 access token is stored in `SharedPreferences` (private mode).
  For production use, consider migrating to
  [EncryptedSharedPreferences](https://developer.android.com/reference/androidx/security/crypto/EncryptedSharedPreferences).
* No API keys are committed to source control.
* All network traffic uses HTTPS only (enforced by `network_security_config.xml`).
