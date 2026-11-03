package com.parallax.selfcalling;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

import com.parallax.selfcalling.databinding.ActivitySmsAutoreplyBinding;
import com.parallax.selfcalling.util.EdgeToEdge;
import com.parallax.selfcalling.util.Prefs;

/** Configure automatic SMS replies to incoming messages. */
public class SmsAutoReplyActivity extends Activity {

    private static final int REQ_SMS = 201;

    private ActivitySmsAutoreplyBinding b;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivitySmsAutoreplyBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        EdgeToEdge.apply(this);
        setActionBar(b.toolbar);
        if (getActionBar() != null) getActionBar().setDisplayHomeAsUpEnabled(true);

        boolean smsGranted = checkSelfPermission(android.Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(android.Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED;

        b.chkEnable.setChecked(smsGranted && Prefs.smsAutoReplyEnabled(this));
        b.chkEnable.setEnabled(smsGranted);
        b.edtReply.setText(Prefs.smsAutoReplyMessage(this));
        b.edtReply.setEnabled(smsGranted);

        if (!smsGranted) {
            requestPermissions(new String[]{
                    android.Manifest.permission.RECEIVE_SMS,
                    android.Manifest.permission.SEND_SMS
            }, REQ_SMS);
        }

        b.chkEnable.setOnCheckedChangeListener((buttonView, isChecked) ->
                Prefs.setSmsAutoReplyEnabled(this, isChecked));

        b.btnSave.setOnClickListener(v -> {
            if (!smsGranted) {
                Toast.makeText(this, "SMS permission not granted", Toast.LENGTH_SHORT).show();
                return;
            }
            Prefs.setSmsAutoReplyMessage(this, b.edtReply.getText().toString());
            Toast.makeText(this, R.string.sms_autoreply_saved, Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean smsGranted = checkSelfPermission(android.Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(android.Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED;
        b.chkEnable.setChecked(smsGranted && Prefs.smsAutoReplyEnabled(this));
        b.chkEnable.setEnabled(smsGranted);
        b.edtReply.setText(Prefs.smsAutoReplyMessage(this));
        b.edtReply.setEnabled(smsGranted);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_SMS) {
            boolean granted = grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED
                    && grantResults.length > 1
                    && grantResults[1] == PackageManager.PERMISSION_GRANTED;
            b.chkEnable.setEnabled(granted);
            b.edtReply.setEnabled(granted);
            if (granted) {
                b.chkEnable.setChecked(Prefs.smsAutoReplyEnabled(this));
                Toast.makeText(this, "SMS permission granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "SMS permission denied — auto-reply disabled", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
