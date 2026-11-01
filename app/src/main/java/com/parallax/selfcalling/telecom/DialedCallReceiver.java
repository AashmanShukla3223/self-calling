package com.parallax.selfcalling.telecom;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.widget.Toast;

import com.parallax.selfcalling.R;

/**
 * Routes numbers dialed in the phone app through this app's calling account.
 *
 * <p>Telecom sends {@code ACTION_NEW_OUTGOING_CALL} before a PSTN call connects; returning a
 * null result aborts that call. The dialed number is then re-placed over sip: on the self-call
 * account, so the native in-call UI shows the simulated call instead of failing on the SIM slot
 * (this phone has no SIM, and Samsung's dialer always targets the SIM account).
 */
public class DialedCallReceiver extends BroadcastReceiver {

    /** Emergency numbers must keep going to the platform, never to a simulation. */
    private static final String[] EMERGENCY = {
            "112", "911", "999", "000", "110", "102", "108", "100", "101"
    };

    @Override
    public void onReceive(Context context, Intent intent) {
        String number = getResultData();
        if (TextUtils.isEmpty(number)) {
            number = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER);
        }
        if (!isDialable(number)) return;

        if (!TelecomHelper.accountUsable(context)) {
            // Leave the dialer's call alone; say why nothing was simulated.
            Toast.makeText(context, R.string.account_status_legacy, Toast.LENGTH_LONG).show();
            return;
        }

        setResultData(null);
        TelecomHelper.connectDialedCall(context, number);
    }

    private static boolean isDialable(String number) {
        if (TextUtils.isEmpty(number)) return false;
        // sip: re-placed calls (ours) carry the number@host form.
        if (number.indexOf('@') >= 0) return false;
        if (!number.matches("[0-9+*#,]+")) return false;
        // MMI/USSD service codes are not calls.
        if (number.startsWith("*") || number.startsWith("#")) return false;
        for (String emergency : EMERGENCY) {
            if (number.equals(emergency)) return false;
        }
        return true;
    }
}
