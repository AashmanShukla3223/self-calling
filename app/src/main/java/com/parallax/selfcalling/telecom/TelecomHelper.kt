package com.parallax.selfcalling.telecom

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import com.parallax.selfcalling.R
import com.parallax.selfcalling.util.Prefs

/**
 * Telecom plumbing: registers the calling account, places the self-call, and simulates the
 * incoming one — all through the system Telecom stack so the native dialer renders the call.
 * Kotlin.
 */
object TelecomHelper {

    private const val FALLBACK_TEL = "5551234"

    /** Host marking a call re-placed from the phone app's dialer (not the preset identity). */
    const val DIALED_HOST = "dial.selfcall.local"

    /*
     * Boot state must NOT include "sip": TeleService's SipAccountRegistry unregisters every
     * sip-capable account that has no saved SIP profile each time the SIP service starts, which
     * deletes this account ~12 s after every boot and forces the user to re-enable it. "sip" is
     * therefore only swapped in (in place, which keeps the enabled flag) while self-calls are
     * live; see enableSipForCall()/restoreTelOnly().
     */
    private fun supportedSchemes(): List<String> = listOf("tel")

    /** Schemes while self-calls are live; "sip" is what keeps the call off the SIM tel: route. */
    private fun callSchemes(): List<String> = listOf("tel", "sip")

    private fun cleanNumber(raw: String?): String {
        var n = raw?.replace(Regex("[^0-9+*#]"), "") ?: FALLBACK_TEL
        if (n.isEmpty()) n = FALLBACK_TEL
        return n
    }

    /** Sanitizes a number to digits/+*# for a tel: handle. */
    @JvmStatic
    fun telUri(raw: String?): Uri = Uri.fromParts("tel", cleanNumber(raw), null)

    /** Address for the self-call. The '@' makes Telecom canonicalize it to sip:. */
    private fun selfCallUri(raw: String?): Uri =
        Uri.fromParts("sip", "${cleanNumber(raw)}@selfcall.local", null)

    /** Address for a number dialed in the phone app; the host tells it apart from a self-call. */
    private fun dialedCallUri(raw: String?): Uri =
        Uri.fromParts("sip", "${cleanNumber(raw)}@$DIALED_HOST", null)

    @JvmStatic
    fun handle(c: Context): PhoneAccountHandle =
        PhoneAccountHandle(ComponentName(c, SelfCallService::class.java), "selfcall")

    @JvmStatic
    fun isRegistered(c: Context): Boolean {
        val tm = c.getSystemService(TelecomManager::class.java) ?: return false
        return try {
            tm.getPhoneAccount(handle(c)) != null
        } catch (_: SecurityException) {
            false
        }
    }

    /**
     * Registered AND switched on in the system "Calling accounts" page. The toggle matters:
     * Telecom routes calls through the account only once the user has enabled it.
     */
    @JvmStatic
    fun accountUsable(c: Context): Boolean {
        if (!isRegistered(c)) return false
        val tm = c.getSystemService(TelecomManager::class.java) ?: return false
        return try {
            val pa = tm.getPhoneAccount(handle(c))
            pa != null && pa.isEnabled
        } catch (_: Exception) {
            isRegistered(c)
        }
    }

    /**
     * Capabilities for the account. API 26+ self-managed accounts are usable without the user
     * toggling them in Settings; on 6–7 the account must be a call-capable provider so it is
     * listed under "All calling accounts" (Android 6 registers third-party accounts disabled).
     */
    private fun capabilities(): Int =
            PhoneAccount.CAPABILITY_CALL_PROVIDER or PhoneAccount.CAPABILITY_ADHOC_CONFERENCE_CALLING

    @JvmStatic
    fun ensureRegistered(c: Context) {
        try {
            val tm = c.getSystemService(TelecomManager::class.java) ?: return
            val pa = try {
                tm.getPhoneAccount(handle(c))
            } catch (_: SecurityException) {
                null
            }
            android.util.Log.d("SelfCall", "ensureRegistered: pa=${pa?.capabilities ?: "null"} label=${pa?.label} schemes=${pa?.supportedUriSchemes} enabled=${pa?.isEnabled}")
            if (pa != null && pa.capabilities == capabilities()
                    && pa.supportedUriSchemes == supportedSchemes()) {
                android.util.Log.d("SelfCall", "ensureRegistered: capabilities and schemes match, skipping re-register")
                return
            }
            // Replace in place: PhoneAccountRegistrar keeps the previous enabled flag when the
            // handle already exists, whereas unregister+register would come back disabled.
            tm.registerPhoneAccount(build(c, supportedSchemes()))
            android.util.Log.d("SelfCall", "ensureRegistered: registered account schemes=${supportedSchemes()}")
        } catch (e: Exception) {
            android.util.Log.w("SelfCall", "ensureRegistered: ${e.message}")
        }
    }

    /** Swaps "sip" into the account in place; the enabled flag survives the replacement. */
    private fun enableSipForCall(c: Context) {
        val tm = c.getSystemService(TelecomManager::class.java) ?: return
        try {
            tm.registerPhoneAccount(build(c, callSchemes()))
            android.util.Log.d("SelfCall", "enableSipForCall: schemes=${callSchemes()}")
        } catch (e: Exception) {
            android.util.Log.w("SelfCall", "enableSipForCall: ${e.message}")
        }
    }

    /**
     * Returns the account to the boot-safe tel-only state once no self-call is live, so the
     * SIP service does not purge it at the next boot.
     */
    @JvmStatic
    fun restoreTelOnly(c: Context) {
        if (SelfCallRegistry.hasCalls()) return
        val tm = c.getSystemService(TelecomManager::class.java) ?: return
        val pa = try {
            tm.getPhoneAccount(handle(c))
        } catch (_: SecurityException) {
            null
        } ?: return
        if (pa.supportedUriSchemes == supportedSchemes()) return
        try {
            tm.registerPhoneAccount(build(c, supportedSchemes()))
            android.util.Log.d("SelfCall", "restoreTelOnly: schemes=${supportedSchemes()}")
        } catch (e: Exception) {
            android.util.Log.w("SelfCall", "restoreTelOnly: ${e.message}")
        }
    }

    private fun build(c: Context, schemes: List<String>): PhoneAccount {
        val h = handle(c)
        return PhoneAccount.Builder(h, Prefs.branding(c))
            .setAddress(telUri(Prefs.activePreset(c).number))
            .setIcon(Icon.createWithResource(c, R.mipmap.ic_launcher))
            .setShortDescription(c.getString(R.string.account_desc))
            .setCapabilities(capabilities())
            .setSupportedUriSchemes(schemes)
            .build()
    }

    @JvmStatic
    fun hasCallPermission(c: Context): Boolean =
        c.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
                && hasAnswerPermission(c)

    @JvmStatic
    fun hasAnswerPermission(c: Context): Boolean =
        Build.VERSION.SDK_INT < 26
                || c.checkSelfPermission(Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED

    @JvmStatic
    fun hasMicPermission(c: Context): Boolean =
        c.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /**
     * Starts the self-call as an outgoing call through Telecom, so the native dialer shows its
     * outgoing in-call screen. The sip: address keeps Samsung's multi-SIM retarget from moving
     * the call onto a SIM slot (tel: would be sent to SIM1 and fail with no SIM inserted).
     */
    @JvmStatic
    fun connectSelfCall(c: Context) {
        val tm = c.getSystemService(TelecomManager::class.java) ?: return
        ensureRegistered(c)
        enableSipForCall(c)
        val extras = Bundle()
        extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle(c))
        try {
            tm.placeCall(selfCallUri(Prefs.activePreset(c).number), extras)
        } catch (e: SecurityException) {
            android.util.Log.w("SelfCall", "connectSelfCall: ${e.message}")
            restoreTelOnly(c)
        }
    }

    /**
     * Re-places a number dialed in the phone app on this account. The dialer's own SIM call has
     * already been cancelled by DialedCallReceiver; sip: plus the explicit handle keeps the
     * re-placed call here.
     */
    @JvmStatic
    fun connectDialedCall(c: Context, number: String?) {
        val tm = c.getSystemService(TelecomManager::class.java) ?: return
        ensureRegistered(c)
        enableSipForCall(c)
        val extras = Bundle()
        extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle(c))
        try {
            tm.placeCall(dialedCallUri(number), extras)
        } catch (e: SecurityException) {
            android.util.Log.w("SelfCall", "connectDialedCall: ${e.message}")
            restoreTelOnly(c)
        }
    }

    /** Rings a simulated incoming call from yourself through Telecom. */
    @JvmStatic
    fun simulateIncoming(c: Context) {
        val tm = c.getSystemService(TelecomManager::class.java) ?: return
        ensureRegistered(c)
        val uri = telUri(Prefs.activePreset(c).number)
        val extras = Bundle()
        extras.putParcelable(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS, uri)
        try {
            tm.addNewIncomingCall(handle(c), extras)
        } catch (e: SecurityException) {
            // Thrown while the account is disabled (e.g. right after re-registering).
            android.util.Log.w("SelfCall", "simulateIncoming: ${e.message}")
        }
    }

    /** Opens the system "Calling accounts" screen (needed to enable the account on Android 6–7). */
    @JvmStatic
    fun openCallingAccounts(c: Context) {
        val i = Intent(TelecomManager.ACTION_CHANGE_PHONE_ACCOUNTS)
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        c.startActivity(i)
    }
}
