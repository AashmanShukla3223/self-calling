package com.parallax.selfcalling;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.parallax.selfcalling.databinding.ActivityMainBinding;
import com.parallax.selfcalling.util.EdgeToEdge;
import com.parallax.selfcalling.util.CallScheduler;
import com.parallax.selfcalling.telecom.TelecomHelper;
import com.parallax.selfcalling.util.Prefs;
import com.parallax.selfcalling.util.Preset;

public class MainActivity extends Activity {

    private static final int REQ_CALL = 1;

    /** CALL_PHONE, ANSWER_PHONE_CALLS (+READ_PHONE_NUMBERS on Android 11+, needed to query the telecom account). */
    private static String[] callPerms() {
        if (Build.VERSION.SDK_INT >= 30) {
            return new String[]{
                    Manifest.permission.CALL_PHONE,
                    Manifest.permission.ANSWER_PHONE_CALLS,
                    Manifest.permission.READ_PHONE_NUMBERS,
                    Manifest.permission.RECEIVE_SMS,
                    Manifest.permission.SEND_SMS
            };
        } else if (Build.VERSION.SDK_INT >= 26) {
            return new String[]{
                    Manifest.permission.CALL_PHONE,
                    Manifest.permission.ANSWER_PHONE_CALLS,
                    Manifest.permission.PROCESS_OUTGOING_CALLS,
                    Manifest.permission.RECEIVE_SMS,
                    Manifest.permission.SEND_SMS
            };
        } else {
            // PROCESS_OUTGOING_CALLS lets DialedCallReceiver catch numbers dialed in the
            // phone app; the broadcast it guards is only sent before Android 10.
            return new String[]{
                    Manifest.permission.CALL_PHONE,
                    Manifest.permission.PROCESS_OUTGOING_CALLS,
                    Manifest.permission.RECEIVE_SMS,
                    Manifest.permission.SEND_SMS
            };
        }
    }

    /** Adds CAMERA for video identities, whose local preview the dialer renders in-call. */
    private String[] callPermsForActivePreset() {
        String[] base = callPerms();
        if (!Prefs.activePreset(this).video
                || checkSelfPermission(Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            return base;
        }
        String[] all = new String[base.length + 1];
        System.arraycopy(base, 0, all, 0, base.length);
        all[base.length] = Manifest.permission.CAMERA;
        return all;
    }

    private boolean readyForActivePreset() {
        if (!TelecomHelper.hasCallPermission(this)) return false;
        if (Build.VERSION.SDK_INT < 29
                && checkSelfPermission(Manifest.permission.PROCESS_OUTGOING_CALLS)
                != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        return !Prefs.activePreset(this).video
                || checkSelfPermission(Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private ActivityMainBinding b;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        EdgeToEdge.apply(this);
        setActionBar(b.toolbar);

        TelecomHelper.ensureRegistered(this);

        b.btnCall.setOnClickListener(v -> {
            if (!readyForActivePreset()) {
                requestPermissions(callPermsForActivePreset(), REQ_CALL);
            } else if (!TelecomHelper.accountUsable(this)) {
                Toast.makeText(this, R.string.account_status_legacy, Toast.LENGTH_LONG).show();
                TelecomHelper.openCallingAccounts(this);
            } else {
                TelecomHelper.connectSelfCall(this);
            }
        });

        b.btnIncoming.setOnClickListener(v -> {
            if (!readyForActivePreset()) {
                requestPermissions(callPermsForActivePreset(), REQ_CALL);
            } else if (!TelecomHelper.accountUsable(this)) {
                Toast.makeText(this, R.string.account_status_legacy, Toast.LENGTH_LONG).show();
                TelecomHelper.openCallingAccounts(this);
            } else {
                int wait = Prefs.incomingDelaySec(this);
                if (wait <= 0) {
                    TelecomHelper.simulateIncoming(this);
                    Toast.makeText(this, R.string.toast_scheduled_now, Toast.LENGTH_SHORT).show();
                } else {
                    CallScheduler.fireIncoming(this, wait);
                    long at = CallScheduler.pendingAt(this);
                    Toast.makeText(this, getString(R.string.toast_scheduled,
                            CallScheduler.fmtLeft(at - System.currentTimeMillis())),
                            Toast.LENGTH_LONG).show();
                }
                refreshPending();
            }
        });

        b.txtPending.setOnClickListener(v -> {
            CallScheduler.cancel(this);
            refreshPending();
            Toast.makeText(this, R.string.toast_canceled, Toast.LENGTH_SHORT).show();
        });

        b.presetCard.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, PresetsActivity.class)));

        b.txtWait.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, SettingsActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        Preset p = Prefs.activePreset(this);
        b.presetName.setText(p.name);
        b.presetNumber.setText(p.number);
        refreshStatus();
        refreshPending();
        refreshWaitLabel();
    }

    /** Shows how long the phone stays quiet before a simulated incoming call rings. */
    private void refreshWaitLabel() {
        int wait = Prefs.incomingDelaySec(this);
        if (wait <= 0) {
            b.txtWait.setText(R.string.wait_home_now);
        } else {
            b.txtWait.setText(getString(R.string.wait_home,
                    CallScheduler.fmtLeft(wait * 1000L)));
        }
    }

    /** Shows/hides the "Incoming call in N min — tap to cancel" chip. */
    private void refreshPending() {
        int count = CallScheduler.pendingCount(this);
        long at = CallScheduler.pendingAt(this);
        long now = System.currentTimeMillis();
        if (count > 0 && at > now) {
            String label = count > 1
                    ? count + " incoming calls scheduled — tap to cancel all"
                    : getString(R.string.pending_chip,
                            CallScheduler.fmtLeft(at - now));
            b.txtPending.setVisibility(android.view.View.VISIBLE);
            b.txtPending.setText(label);
        } else {
            b.txtPending.setVisibility(android.view.View.GONE);
        }
    }

    private void refreshStatus() {
        boolean usable = TelecomHelper.accountUsable(this);
        boolean reg = TelecomHelper.isRegistered(this);
        b.rowAccountIcon.setImageResource(usable ? R.drawable.ic_check_circle : R.drawable.ic_error_outline);
        if (usable) {
            b.rowAccountSub.setText(R.string.status_account_ok);
        } else if (reg) {
            b.rowAccountSub.setText(R.string.account_status_legacy);
        } else {
            b.rowAccountSub.setText(R.string.status_account_fail);
        }

        boolean call = TelecomHelper.hasCallPermission(this);
        b.rowCallIcon.setImageResource(call ? R.drawable.ic_check_circle : R.drawable.ic_error_outline);
        b.rowCallSub.setText(call ? R.string.status_perm_ok : R.string.status_call_missing);

        if (Build.VERSION.SDK_INT >= 26) {
            boolean ans = TelecomHelper.hasAnswerPermission(this);
            b.rowAnswerIcon.setImageResource(ans ? R.drawable.ic_check_circle : R.drawable.ic_error_outline);
            b.rowAnswerSub.setText(ans ? R.string.status_perm_ok : R.string.status_answer_missing);
        } else {
            b.rowAnswer.setVisibility(android.view.View.GONE);
        }

        boolean mic = TelecomHelper.hasMicPermission(this);
        b.rowMicIcon.setImageResource(mic ? R.drawable.ic_check_circle : R.drawable.ic_error_outline);
        b.rowMicSub.setText(mic ? R.string.status_mic_ok : R.string.status_mic_missing);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        if (id == R.id.action_log) {
            startActivity(new Intent(this, LogActivity.class));
            return true;
        }
        if (id == R.id.action_shortcuts) {
            startActivity(new Intent(this, ShortcutSetsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQ_CALL) {
            refreshStatus();
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, R.string.perm_granted, Toast.LENGTH_SHORT).show();
            }
        }
    }
}
