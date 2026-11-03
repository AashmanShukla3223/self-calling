package com.parallax.selfcalling;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Toast;

import com.parallax.selfcalling.databinding.ActivitySettingsBinding;
import com.parallax.selfcalling.databinding.DialogBrandingBinding;
import com.parallax.selfcalling.util.EdgeToEdge;
import com.parallax.selfcalling.util.CallScheduler;
import com.parallax.selfcalling.telecom.TelecomHelper;
import com.parallax.selfcalling.util.Prefs;
import com.parallax.selfcalling.util.Preset;

public class SettingsActivity extends Activity {

    private ActivitySettingsBinding b;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        EdgeToEdge.apply(this);
        setActionBar(b.toolbar);
        if (getActionBar() != null) {
            getActionBar().setDisplayHomeAsUpEnabled(true);
        }

        b.btnManagePresets.setOnClickListener(v ->
                startActivity(new Intent(this, PresetsActivity.class)));

        int cd = Prefs.connectDelayMs(this);
        if (cd <= 2000) b.radioC2.setChecked(true);
        else if (cd <= 5000) b.radioC5.setChecked(true);
        else b.radioC10.setChecked(true);
        b.timingGroup.setOnCheckedChangeListener((g, id) -> {
            int ms = id == R.id.radioC5 ? 5000 : id == R.id.radioC10 ? 10000 : 2000;
            Prefs.setConnectDelayMs(SettingsActivity.this, ms);
        });

        int ae = Prefs.autoEndSec(this);
        b.radioEOff.setChecked(ae <= 0);
        b.radioE30.setChecked(ae == 30);
        b.radioE60.setChecked(ae == 60);
        b.radioE300.setChecked(ae == 300);
        b.endGroup.setOnCheckedChangeListener((g, id) -> {
            int s = id == R.id.radioE30 ? 30 : id == R.id.radioE60 ? 60 : id == R.id.radioE300 ? 300 : -1;
            Prefs.setAutoEndSec(SettingsActivity.this, s);
        });

        int delay = Prefs.autoAnswerDelay(this);
        if (delay == 0) b.radio0.setChecked(true);
        else if (delay == 3) b.radio3.setChecked(true);
        else if (delay == 5) b.radio5.setChecked(true);
        else if (delay == 10) b.radio10.setChecked(true);
        else b.radioOff.setChecked(true);
        b.autoGroup.setOnCheckedChangeListener((g, id) -> {
            int d = -1;
            if (id == R.id.radio0) d = 0;
            else if (id == R.id.radio3) d = 3;
            else if (id == R.id.radio5) d = 5;
            else if (id == R.id.radio10) d = 10;
            Prefs.setAutoAnswerDelay(SettingsActivity.this, d);
        });

        // ---- incoming call: waiting time (now … 3 hours) before the phone rings ----
        int wd = Prefs.incomingDelaySec(this);
        if (wd == 1) b.radioW1.setChecked(true);
        else if (wd == 2) b.radioW2.setChecked(true);
        else if (wd == 3) b.radioW3.setChecked(true);
        else if (wd == 5) b.radioW5.setChecked(true);
        else if (wd == 10) b.radioW10.setChecked(true);
        else if (wd == 30) b.radioW30.setChecked(true);
        else if (wd == 60) b.radioW60.setChecked(true);
        else if (wd == 300) b.radioW300.setChecked(true);
        else if (wd == 900) b.radioW900.setChecked(true);
        else if (wd == 1800) b.radioW1800.setChecked(true);
        else if (wd == 3600) b.radioW3600.setChecked(true);
        else if (wd == 7200) b.radioW7200.setChecked(true);
        else if (wd == 10800) b.radioW10800.setChecked(true);
        else b.radioWNow.setChecked(true);
        b.waitGroup.setOnCheckedChangeListener((g, id) -> {
            int s = 0;
            if (id == R.id.radioW1) s = 1;
            else if (id == R.id.radioW2) s = 2;
            else if (id == R.id.radioW3) s = 3;
            else if (id == R.id.radioW5) s = 5;
            else if (id == R.id.radioW10) s = 10;
            else if (id == R.id.radioW30) s = 30;
            else if (id == R.id.radioW60) s = 60;
            else if (id == R.id.radioW300) s = 300;
            else if (id == R.id.radioW900) s = 900;
            else if (id == R.id.radioW1800) s = 1800;
            else if (id == R.id.radioW3600) s = 3600;
            else if (id == R.id.radioW7200) s = 7200;
            else if (id == R.id.radioW10800) s = 10800;
            Prefs.setIncomingDelaySec(SettingsActivity.this, s);
            CallScheduler.cancel(SettingsActivity.this);
            if (s > 180) CallScheduler.ensureExactAlarms(SettingsActivity.this);
        });

        // ---- incoming call: ring timeout (never … 1 minute) ----
        int rt = Prefs.ringTimeoutSec(this);
        b.radioRNever.setChecked(rt <= 0);
        b.radioR15.setChecked(rt == 15);
        b.radioR30.setChecked(rt == 30);
        b.radioR45.setChecked(rt == 45);
        b.radioR60.setChecked(rt == 60);
        b.ringGroup.setOnCheckedChangeListener((g, id) -> {
            int s;
            if (id == R.id.radioR15) s = 15;
            else if (id == R.id.radioR30) s = 30;
            else if (id == R.id.radioR45) s = 45;
            else if (id == R.id.radioR60) s = 60;
            else s = 0;
            Prefs.setRingTimeoutSec(SettingsActivity.this, s);
        });

        b.btnConfigureVoice.setOnClickListener(v ->
                startActivity(new Intent(this, VoiceActivity.class)));

        b.btnManageAccounts.setOnClickListener(v -> TelecomHelper.openCallingAccounts(this));

        b.btnBranding.setOnClickListener(v -> showBrandingDialog());

        // ---- SMS auto-reply ----
        b.btnSmsAutoreply.setOnClickListener(v ->
                startActivity(new Intent(this, SmsAutoReplyActivity.class)));
    }

    /** Edits the PhoneAccount label used by the dialer as "Calls via ...". */
    private void showBrandingDialog() {
        DialogBrandingBinding dlg = DialogBrandingBinding.inflate(getLayoutInflater());
        dlg.dlgBranding.setText(Prefs.branding(this));
        dlg.dlgBranding.setSelection(dlg.dlgBranding.length());

        new AlertDialog.Builder(this)
                .setTitle(R.string.branding_dialog_title)
                .setView(dlg.getRoot())
                .setPositiveButton(R.string.branding_save, (d, w) -> {
                    String value = dlg.dlgBranding.getText().toString().trim();
                    Prefs.setBranding(this, value);
                    TelecomHelper.ensureRegistered(this);
                    updateBrandingRow();
                    if (value.isEmpty()) {
                        Toast.makeText(this, getString(R.string.branding_reset_toast,
                                Prefs.branding(this)), Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, getString(R.string.branding_saved,
                                Prefs.branding(this)), Toast.LENGTH_SHORT).show();
                    }
                })
                .setNeutralButton(R.string.branding_reset_button, (d, w) -> {
                    Prefs.setBranding(this, "");
                    TelecomHelper.ensureRegistered(this);
                    updateBrandingRow();
                    Toast.makeText(this, getString(R.string.branding_reset_toast,
                            Prefs.branding(this)), Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void updateBrandingRow() {
        b.brandingValue.setText(getString(R.string.branding_preview, Prefs.branding(this)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateBrandingRow();
        b.identityActive.setText(Prefs.activePreset(this).name);
        b.identityNumber.setText(Prefs.activePreset(this).number);

        Preset vp = Prefs.activePreset(this);
        if (Preset.VOICE_FILE.equals(vp.voice) && vp.voiceFile != null
                && new java.io.File(vp.voiceFile).exists()) {
            b.voiceMode.setText(new java.io.File(vp.voiceFile).getName());
        } else if (Preset.VOICE_NONE.equals(vp.voice)) {
            b.voiceMode.setText(R.string.voice_none);
        } else if (Preset.VOICE_FILE.equals(vp.voice)) {
            b.voiceMode.setText(R.string.voice_no_file);
        } else {
            b.voiceMode.setText(R.string.voice_mic);
        }

        boolean usable = TelecomHelper.accountUsable(this);
        if (usable) {
            b.accountStatus.setText(R.string.account_status_ready);
        } else if (TelecomHelper.isRegistered(this)) {
            b.accountStatus.setText(R.string.account_status_legacy);
        } else {
            b.accountStatus.setText(R.string.account_status_fail);
        }

        // SMS auto-reply status
        boolean smsEnabled = Prefs.smsAutoReplyEnabled(this);
        String smsMsg = Prefs.smsAutoReplyMessage(this);
        String status = smsEnabled
                ? "Auto-reply ON: \"" + (smsMsg.isEmpty() ? "(no message set)" : smsMsg) + "\""
                : "Auto-reply OFF";
        b.smsAutoreplyStatus.setText(status);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
