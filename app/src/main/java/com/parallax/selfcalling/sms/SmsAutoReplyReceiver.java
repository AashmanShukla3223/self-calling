package com.parallax.selfcalling.sms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.util.Log;

import com.parallax.selfcalling.util.Prefs;
import com.parallax.selfcalling.util.Preset;
import com.parallax.selfcalling.util.SmsEntry;
import com.parallax.selfcalling.util.SmsLogStore;

import java.util.List;

/**
 * Receives incoming SMS and auto-replies per-contact when SMS auto-reply is enabled.
 * Works in the background — no activity needed.
 * Logs every sent/received message to the SMS log.
 */
public class SmsAutoReplyReceiver extends BroadcastReceiver {

    private static final String SMS_RECEIVED = "android.provider.Telephony.SMS_RECEIVED";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !SMS_RECEIVED.equals(intent.getAction())) return;

        boolean globalEnabled = Prefs.smsAutoReplyEnabled(context);

        Bundle extras = intent.getExtras();
        if (extras == null) return;

        Object[] pdus = (Object[]) extras.get("pdus");
        if (pdus == null) return;

        List<Preset> presets = Prefs.presets(context);
        SmsManager smsManager = SmsManager.getDefault();

        for (Object pdu : pdus) {
            try {
                SmsMessage sms = SmsMessage.createFromPdu((byte[]) pdu);
                String originatingAddress = sms.getOriginatingAddress();
                String messageBody = sms.getMessageBody();
                if (originatingAddress == null || originatingAddress.isEmpty()) continue;

                // Log the received message
                SmsLogStore.add(context, new SmsEntry(
                    System.currentTimeMillis(), "in", originatingAddress,
                    messageBody != null ? messageBody : "", "received"));

                // Find matching preset by number
                Preset matched = null;
                for (Preset p : presets) {
                    if (p.number.equals(originatingAddress)) {
                        matched = p;
                        break;
                    }
                }

                // Decide whether to auto-reply
                boolean shouldReply = false;
                String replyMessage = "";
                if (matched != null && matched.smsAutoReplyEnabled()) {
                    if (matched.smsKeywordMatches(messageBody != null ? messageBody : "")) {
                        shouldReply = true;
                        replyMessage = matched.smsAutoReplyMessage;
                    }
                } else if (globalEnabled) {
                    String globalMsg = Prefs.smsAutoReplyMessage(context);
                    if (globalMsg != null && !globalMsg.isEmpty()) {
                        shouldReply = true;
                        replyMessage = globalMsg;
                    }
                }

                if (shouldReply && !replyMessage.isEmpty()) {
                    smsManager.sendTextMessage(originatingAddress, null, replyMessage, null, null);
                    SmsLogStore.add(context, new SmsEntry(
                        System.currentTimeMillis(), "out", originatingAddress,
                        replyMessage, "sent"));
                }
            } catch (Exception e) {
                Log.w("SmsAutoReply", "Failed to process SMS: " + e.getMessage());
            }
        }
    }
}
