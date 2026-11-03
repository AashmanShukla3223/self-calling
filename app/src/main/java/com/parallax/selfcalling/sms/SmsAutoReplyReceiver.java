package com.parallax.selfcalling.sms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.widget.Toast;

import com.parallax.selfcalling.util.Prefs;

/**
 * Receives incoming SMS and auto-replies when SMS auto-reply is enabled.
 * Works in the background — no activity needed.
 */
public class SmsAutoReplyReceiver extends BroadcastReceiver {

    private static final String SMS_RECEIVED = "android.provider.Telephony.SMS_RECEIVED";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !SMS_RECEIVED.equals(intent.getAction())) return;

        if (!Prefs.smsAutoReplyEnabled(context)) return;

        Bundle extras = intent.getExtras();
        if (extras == null) return;

        Object[] pdus = (Object[]) extras.get("pdus");
        if (pdus == null) return;

        String replyMessage = Prefs.smsAutoReplyMessage(context);
        if (replyMessage == null || replyMessage.isEmpty()) return;

        SmsManager smsManager = SmsManager.getDefault();
        for (Object pdu : pdus) {
            SmsMessage sms = SmsMessage.createFromPdu((byte[]) pdu);
            String originatingAddress = sms.getOriginatingAddress();
            if (originatingAddress == null || originatingAddress.isEmpty()) continue;

            try {
                smsManager.sendTextMessage(originatingAddress, null, replyMessage, null, null);
            } catch (Exception e) {
                android.util.Log.w("SmsAutoReply", "Failed to send auto-reply: " + e.getMessage());
            }
        }

        Toast.makeText(context, "SMS auto-reply sent", Toast.LENGTH_SHORT).show();
    }
}
