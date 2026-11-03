package com.parallax.selfcalling;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import com.parallax.selfcalling.util.ShortcutRunner;
import com.parallax.selfcalling.util.ShortcutSets;

public class ShortcutRunActivity extends Activity {

    private int setId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent i = getIntent();
        if (i != null) {
            if (i.hasExtra("set_id")) setId = i.getIntExtra("set_id", -1);
            else if (i.hasExtra("shortcut_set_id")) setId = i.getIntExtra("shortcut_set_id", -1);
            else if (i.getData() != null) {
                try { setId = Integer.parseInt(i.getData().getLastPathSegment()); } catch (Exception ignored) {}
            }
        }
        if (setId < 0) {
            Toast.makeText(this, "Shortcut set not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        ShortcutRunner.RunResult res = ShortcutRunner.run(this, setId);
        if (res == ShortcutRunner.RunResult.OK) {
            Toast.makeText(this, "Scheduled", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Shortcut set not found or empty", Toast.LENGTH_SHORT).show();
        }
        finish();
    }
}
