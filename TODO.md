# Self Calling — Build Tasks

> **App:** Self Calling (`com.parallax.selfcalling`) · Android 6.0+ (minSdk 23)
> **UI:** Classic Material Design (2015 spec), dark premium — XML views, framework `Theme.Material`, zero external libraries
> **Trick:** Calls are placed through Telecom (`ConnectionService` + `PhoneAccount`), so ringing/connected/ended render on the **real native dialer UI**
> **Toolchain:** Gradle 9.6.0 + AGP 9.4.1 + JDK 25 (Android Studio JBR) · SDK at `D:\Games\Android\Sdk`

## Tasks

| # | Task | Status |
|---|------|--------|
| 1 | Project scaffold — Gradle config, manifest, permissions, package `com.parallax.selfcalling` | ✅ |
| 2 | Logo & launcher icons — adaptive icon (dark gradient, handset + "call-back-to-yourself" loop), monochrome layer, legacy PNG mipmaps for Android 6–7 | ✅ |
| 3 | Telecom engine — `SelfCallService` (ConnectionService), `SelfCallConnection` (dial→active→hang-up, ring→answer→reject), `TelecomHelper` (register account, place self-call, simulate incoming, auto-answer) | ✅ |
| 4 | Mic loopback — `LoopbackEngine` (AudioRecord → AudioTrack in `MODE_IN_COMMUNICATION`) so you hear yourself during the call | ✅ |
| 5 | UI (2015 Material, dark premium) — Home (status card, active-identity card, big green call button, incoming simulator), Settings (identity, call timing, auto-end, auto-answer, voice, account manager), Presets, Voice, Recording screens, Self-call log | ✅ |
| 6 | Build APK — `assembleDebug` | ✅ (after 2 fixes: API 37 `getHandle()`→`getAddress()`, `READ_PHONE_NUMBERS` guard) |
| 7 | Pixel_10a (Android 17 / API 37) test | ✅ **user-tested** — app runs clean after fixes; both emulators shut down by user afterward |
| 13 | Android 17 crash fixes — (1) Telecom forbids changing an account's kind in place: `ensureRegistered` now unregisters the old self-managed account and registers fresh, catching all exceptions; (2) `READ_PHONE_NUMBERS` now requested at runtime on Android 11+; (3) account switched to `CAPABILITY_CALL_PROVIDER` on **all** APIs so it shows in the system Calling accounts page like on Android 6 (self-managed accounts are hidden there by design) | ✅ built, installed, verified (app alive, account registered + enabled) |
| 8 | Galaxy_Nexus (API 23) test — outgoing → native AOSP call screen → auto-connect → end **PASS**; presets add/switch/edit **PASS**; log screen **PASS** (8 entries incl. user's own test calls) | ✅ |
| 9 | Voice in call — per-identity source: silence / live mic / audio file (imported via picker or recorded via mic); loops into the call's voice path while connected | ✅ record → auto-assign → **verified playing in-call** (audio_flinger: active 44.1 kHz track; standby restored after hang-up) |
| 11 | **Java + Kotlin codebase** — convert data/util/engines (`Preset`, `Prefs`, `CallEntry`, `CallLogStore`, `CallFilePlayer`, `LoopbackEngine`, `TelecomHelper`) to Kotlin via AGP 9 built-in Kotlin (no KGP); Java keeps the UI + connection service | ✅ BUILD SUCCESSFUL 32 s; API 23 smoke test PASS (presets + status via Kotlin) |
| 12 | **Android 17 (API 37) adaptation** — targetSdk 37; edge-to-edge insets handling (Android 15+ enforcement) via `EdgeToEdge` helper in all 6 screens; no-op below API 35 | ✅ built & shipped |
| 10 | Final report — results, screenshots, how-to | ✅ (updated after Kotlin/Android 17 round) |
| 14 | **v2 feature batch** — (1) 5 new seed presets: Teacher / Worker / Delivery Driver / Boss / Newspaper Deliverer (editable numbers); (2) incoming-call **waiting time**: Immediately / 3/5/10/30 s / 1/5/15/30 min / 1/2/3 h via `AlarmManager` exact alarms (`CallScheduler` + `CallAlarmReceiver` + `FgsIncomingService` phoneCall-FGS) so the ring fires even if the process dies while waiting; (3) **ring timeout**: Never / 15/30/45/60 s auto-dismiss (configurable, default 45 s); (4) **in-call controls** on the native dialer: Hold (`CAPABILITY_HOLD`+`SUPPORT_HOLD`, pauses/resumes the voice engine), Mute (`CAPABILITY_MUTE`, stops/starts mic loopback), speaker + Bluetooth via `setAudioModeIsVoip(true)` routing | ✅ built, installed, verified (app alive, account registered + enabled) |
| 15 | **Video calls** — per-identity video toggle (Voice); connection advertises `VIDEO_STATE_BIDIRECTIONAL` + VT capabilities and supplies a standard Telecom `Connection.VideoProvider` (Camera2 local preview + simulated remote feed) so the dialer derives video from its own in-call UI; `CAMERA` requested at runtime; call log marks video entries. J7 check: Telecom/InCallUI see `videoState:3` + VT caps, but Samsung's InCallUI never queries the provider (hard-coded `enable_pstn_video_provider=false`, `isSecVT()=true`) and stays on its voice screen — OEM IMS-only VT; standard AOSP/Google dialers use the provider | ⚠️ builds/installs; native video panes unreachable on this Samsung firmware |

## Feature decisions (locked)
- **Identity presets**: saved name + mobile-number pairs; add/edit/delete; tap to switch active; outgoing + simulated incoming calls use the active preset
- **Voice in call (per identity)**: `none` (silence) / `mic` (live loopback) / `file` (looping audio file). Import via system picker (`ACTION_GET_CONTENT audio/*`) or record in-app (MediaRecorder → AAC/m4a). Playback: `MediaPlayer` with `USAGE_VOICE_COMMUNICATION` + `MODE_IN_COMMUNICATION` → call/earpiece audio path. Starts when the call connects, stops on hang-up
- **Call timing (settable)**: connect delay 2 s / 5 s / 10 s; auto-end once connected: never / 30 s / 1 min / 5 min
- **Incoming call (v2)**: waiting time Immediately / 3/5/10/30 s / 1/5/15/30 min / 1/2/3 h (alarm-based, survives process death); ring timeout Never / 15/30/45/60 s (default 45 s, like telcos); Hold + Mute + Speaker + Bluetooth from the native dialer's in-call screen (connection capabilities + engine pause/resume hooks)
- Auto-answer delay: Off / at once / 3 s / 5 s / 10 s — only on Android 8+ (`ANSWER_PHONE_CALLS`); on 6–7 the native UI's answer button is used
- Self-call log: local history (out/in, connected/missed/canceled, duration) — no external DB
- ~~Speakerphone auto-on~~ — not in v1
- ~~Settings loopback switch~~ — replaced by per-identity Voice configuration (Settings shows a pointer to it)

## Known quirks handled in design
- First Gradle build may download a few artifacts (network confirmed working)
- Android 6–7: Telecom requires the user to **enable the app's calling account** once (Settings → guide built in)
- Auto-answer is impossible on Android 6/7 (no `acceptRingingCall`) — by OS design
- Samsung InCallUI (API 23 J7) cannot render third-party video: `InCallUIFeature.makeFeatureForVT()` hard-codes `enable_pstn_video_provider=false` and `SecVideoCallUtils.isSecVT()` returns `true`, so `Call.getVideoCall()` is null and the dialer never calls the video provider's surface/camera callbacks; its VT layout is IMS/data-gated and the phone is `user`/`release-keys` (no root to patch the flag). Video self-calls therefore render on standard (AOSP/Google) dialers, not this firmware
