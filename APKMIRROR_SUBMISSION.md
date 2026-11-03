================================================================================
SELF CALLING v1.3.1 — APKMirror Submission Package
================================================================================

APK FILE TO UPLOAD:
  /home/aashmanshukla323/Self Calling/app/build/outputs/apk/release/app-release.apk
  Size: ~830 KB
  SHA-256: (compute with `sha256sum app-release.apk`)

================================================================================
SUBMISSION DETAILS (fill in on APKMirror website)
================================================================================

App Name: Self Calling
Package Name: com.parallax.selfcalling
Version: 1.3.1
Version Code: 4
Android Requirement: Android 6.0 (API 23) and up
Developer: Aashman Shukla
Developer Website: https://github.com/AashmanShukla3223/self-calling

Description:

Self Calling places simulated incoming calls and outgoing self-calls through the
system Telecom stack so they render in the native dialer UI. The app only places
calls; it does not route them through a SIM.

Shortcut Sets let you build ordered chains of simulated incoming calls, SMS
messages, and outgoing calls — one tap runs the whole chain. Great for automating
repetitive simulate-incoming workflows (person a → b → c → …) without tapping
each one individually.

SMS Auto-Reply automatically replies to incoming SMS messages with a configured
message, per-contact or globally, with optional keyword triggers.

Home screen widgets run any shortcut set with a single tap.

Key Features:
- Simulated incoming calls via Telecom (native dialer UI)
- Outgoing self-calls through Telecom
- Shortcut Sets: mixed call / SMS / outgoing-call chains with per-step delays
- SMS Auto-Reply with per-contact messages and keyword triggers
- SMS log and backup/restore
- Permission center
- Home screen widgets for one-tap shortcut runs

Permissions used:
- CALL_PHONE — place self-calls
- ANSWER_PHONE_CALLS — auto-answer simulated calls (Android 8+)
- READ_PHONE_NUMBERS — query telecom account (Android 11+)
- RECEIVE_SMS / SEND_SMS — SMS auto-reply and SMS shortcut steps
- RECORD_AUDIO — live mic voice in calls
- READ_CONTACTS — import presets from contacts
- CAMERA — video call identities
- SCHEDULE_EXACT_ALARM — delayed incoming calls after long waits
- POST_NOTIFICATIONS — pending-call notifications
- FOREGROUND_SERVICE — incoming-call delivery
- MANAGE_OWN_CALLS — manage own calls

Screenshots to provide (from your existing shots):
- shot_home.png (home screen with preset + shortcuts)
- shot_presets.png (presets management)
- shot_settings.png (settings screen)

License: MIT (https://github.com/AashmanShukla3223/self-calling/blob/main/LICENSE)

================================================================================
SUBMISSION PROCESS
================================================================================
1. Go to https://www.apkmirror.com/
2. Click "Submit APK" (you may need to create an account first)
3. Fill in the details above
4. Upload the signed APK from app/build/outputs/apk/release/app-release.apk
5. Add screenshots (shot_home.png, shot_presets.png, shot_settings.png)
6. Submit for review — APKMirror team verifies and publishes

================================================================================
F-DROID SUBMISSION (requires separate account on GitLab)
================================================================================
F-Droid metadata is prepared in fdroid_metadata.yml in this repo.
Submit via their GitLab data repo: https://gitlab.com/fdroid/fdroiddata
Create an issue/merge request adding the metadata for com.parallax.selfcalling.

F-Droid builds from source — they do not accept pre-built APKs directly.
They will compile from the GitHub repo tag v1.3.1.

================================================================================
IMPORTANT
================================================================================
- This is a SIGNED RELEASE APK only — NO DEBUG APK anywhere in the project.
- Build script: ./build.sh (runs ./gradlew assembleRelease offline)
- To rebuild: ./build.sh
