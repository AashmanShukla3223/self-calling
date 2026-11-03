package com.parallax.selfcalling;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.parallax.selfcalling.databinding.ActivityPermissionCenterBinding;
import com.parallax.selfcalling.util.EdgeToEdge;
import com.parallax.selfcalling.util.Prefs;

import java.util.ArrayList;
import java.util.List;

/** Permission center: shows status of each permission the app uses. */
public class PermissionCenterActivity extends Activity {

    // permission groups: label, manifest permission, rationale
    private static final String[][] PERMS = {
            {"Place calls", android.Manifest.permission.CALL_PHONE, "Needed to place self-calls through Telecom"},
            {"Answer calls", android.Manifest.permission.ANSWER_PHONE_CALLS, "Needed to auto-answer simulated incoming calls (Android 8+)"},
            {"Read phone numbers", android.Manifest.permission.READ_PHONE_NUMBERS, "Needed to query the telecom account (Android 11+)"},
            {"Receive SMS", android.Manifest.permission.RECEIVE_SMS, "Needed for SMS auto-reply to work"},
            {"Send SMS", android.Manifest.permission.SEND_SMS, "Needed to send auto-replies and SMS shortcut steps"},
            {"Record audio", android.Manifest.permission.RECORD_AUDIO, "Needed for live mic voice in calls"},
            {"Read contacts", android.Manifest.permission.READ_CONTACTS, "Needed to import presets from contacts"},
            {"Camera", android.Manifest.permission.CAMERA, "Needed for video call identities"},
            {"Exact alarms", android.Manifest.permission.SCHEDULE_EXACT_ALARM, "Needed for delayed incoming calls after long waits"},
            {"Post notifications", android.Manifest.permission.POST_NOTIFICATIONS, "Needed for pending-call notifications"},
    };

    private ActivityPermissionCenterBinding b;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityPermissionCenterBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        EdgeToEdge.apply(this);
        setActionBar(b.toolbar);
        if (getActionBar() != null) getActionBar().setDisplayHomeAsUpEnabled(true);

        reload();

        b.btnOpenSettings.setOnClickListener(v -> {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            i.setData(Uri.parse("package:" + getPackageName()));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    private void reload() {
        b.permList.removeAllViews();
        for (String[] p : PERMS) {
            String label = p[0];
            String perm = p[1];
            String rationale = p[2];
            boolean granted = checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED;

            View row = LayoutInflater.from(this).inflate(android.R.layout.simple_list_item_2, b.permList, false);
            TextView t1 = row.findViewById(android.R.id.text1);
            TextView t2 = row.findViewById(android.R.id.text2);
            t1.setText(label + "  —  " + (granted ? "GRANTED" : "NOT GRANTED"));
            t1.setTextColor(granted
                    ? getResources().getColor(R.color.text_primary)
                    : getResources().getColor(R.color.text_secondary));
            t2.setText(rationale);
            row.setPadding(8, 8, 8, 8);
            row.setClickable(true);
            row.setFocusable(true);
            row.setOnClickListener(v -> {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    requestPermissions(new String[]{perm}, 1001);
                }
            });
            b.permList.addView(row);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        reload();
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Permission granted", Toast.LENGTH_SHORT).show();
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
