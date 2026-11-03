# Self Calling

Simulated incoming calls and outgoing self-calls that go through Android's Telecom
stack, so they appear in the real dialer — call screen, notifications, and call log.
Includes SMS auto-reply, chained shortcut sets, and home-screen widgets.

MIT licensed. No ads, no tracking, no third-party libraries. ~840 KB.

## Download & install

Grab the latest signed APK from
[Releases](https://github.com/AashmanShukla3223/self-calling/releases/latest)
(`app-release.apk`).

- Requires Android 6.0 (API 23) or newer. Works without a SIM.
- Sideload: open the APK on the phone and allow install from unknown sources,
  or `adb install -r app-release.apk`.
- Release builds are signed with the same key, so updates install over each other.

## First run

1. Launch the app once so it registers its Telecom account.
2. Grant the call, SMS, and microphone permissions it asks for.
3. **Android 6–7 only:** enable the calling account under
   Settings → Calling accounts → Self Calling. Or via adb, after launching once:

   ```bash
   adb shell telecom set-phone-account-enabled \
     com.parallax.selfcalling/com.parallax.selfcalling.telecom.SelfCallService selfcall
   ```

   Android 8+ needs no manual toggle.
4. Use **Place call** for an outgoing self-call or **Simulate incoming** for a
   ringing incoming call (optionally after a delay).

## Worth testing

- Outgoing self-call renders in the native dialer and call log.
- Delayed incoming calls survive the app being killed or the phone rebooting.
- Shortcut sets (mixed call / SMS / outgoing steps) and home-screen widgets.
- SMS auto-reply with per-contact messages and keyword triggers.
- Video identities on Google/AOSP dialers. Samsung's InCallUI never queries the
  video provider, so video is expected to be inert there.

Report problems via [Issues](https://github.com/AashmanShukla3223/self-calling/issues)
with your device model, Android version, dialer app, and steps to reproduce.

## Known limitations

- OEM dialer behavior varies; older Samsung and MIUI dialers are the most quirky.
- Calls never leave the device — nothing is routed through a SIM or network.
- Sideload only for now: F-Droid submission is pending review; not on Google Play.

## Build from source

```bash
./build.sh          # signed release APK, requires keystore.properties + release.keystore
```

Or `./gradlew assembleRelease` for an unsigned build. Every push to `main` is built
and published automatically as a GitHub Release.
