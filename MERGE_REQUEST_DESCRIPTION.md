# New App: Self Calling (com.parallax.selfcalling)

## Summary
Call yourself through the native dialer with shortcut automation.

Self Calling places simulated incoming calls and outgoing self-calls through the system Telecom stack so they render in the native dialer UI. The app only places calls; it does not route them through a SIM.

## Package
- **Package:** `com.parallax.selfcalling`
- **Version:** 1.3.1
- **Version Code:** 4
- **License:** MIT
- **Source:** https://github.com/AashmanShukla3223/self-calling
- **Tag:** v1.3.1

## Description

Self Calling places simulated incoming calls and outgoing self-calls through the system Telecom stack so they render in the native dialer UI. The app only places calls; it does not route them through a SIM.

**Shortcut Sets** let you build ordered chains of simulated incoming calls, SMS messages, and outgoing calls — one tap runs the whole chain. Instead of selecting person a → simulate incoming → person b → simulate incoming → etc., build one set and run it once.

**SMS Auto-Reply** automatically replies to incoming SMS messages with a configured message, per-contact or globally, with optional keyword triggers.

**Home screen widgets** run any shortcut set with a single tap.

## Key Features

- Simulated incoming calls via Telecom (native dialer UI)
- Outgoing self-calls through Telecom
- Shortcut Sets: mixed call / SMS / outgoing-call chains with per-step delays
- SMS Auto-Reply with per-contact messages and keyword triggers
- SMS log and backup/restore
- Permission center
- Home screen widgets for one-tap shortcut runs

## Build Information

- **Gradle version:** 9.6.0
- **AGP version:** 9.4.1
- **Compile SDK:** 37
- **Build tools:** 36.0.0
- **NDK:** r25c
- **Min SDK:** 23 (Android 6.0)
- **Target SDK:** 37
- **Architectures:** armeabi-v7a, arm64-v8a, x86, x86_64
- **Build variant:** release
- **External libraries:** none

## Metadata File

The F-Droid metadata file is `metadata/com.parallax.selfcalling.yml` in this merge request.

## Screenshots

Available in the GitHub repo:
- https://github.com/AashmanShukla3223/self-calling (root directory: shot_home.png, shot_presets.png, shot_settings.png)

## Author

- **Name:** Aashman Shukla
- **GitHub:** https://github.com/AashmanShukla3223/self-calling

## Notes

- No external libraries or proprietary dependencies
- Signed release APK built with `./gradlew assembleRelease`
- Full build script included: `./build.sh`
