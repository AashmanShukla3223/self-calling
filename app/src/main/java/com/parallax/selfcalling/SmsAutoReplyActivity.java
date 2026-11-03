package com.parallax.selfcalling;

import android.app.Activity;
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

    private ActivitySmsAutoreplyBinding b;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivitySmsAutoreplyBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        EdgeToEdge.apply(this);
        setActionBar(b.toolbar);
        if (getActionBar() != null) getActionBar().setDisplayHomeAsUpEnabled(true);

        b.chkEnable.setChecked(Prefs.smsAutoReplyEnabled(this));
        b.edtReply.setText(Prefs.smsAutoReplyMessage(this));

        b.chkEnable.setOnCheckedChangeListener((buttonView, isChecked) ->
                Prefs.setSmsAutoReplyEnabled(this, isChecked));

        b.btnSave.setOnClickListener(v -> {
            Prefs.setSmsAutoReplyMessage(this, b.edtReply.getText().toString());
            Toast.makeText(this, R.string.sms_autoreply_saved, Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        b.chkEnable.setChecked(Prefs.smsAutoReplyEnabled(this));
        b.edtReply.setText(Prefs.smsAutoReplyMessage(this));
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
