package com.parallax.selfcalling;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import com.parallax.selfcalling.util.ShortcutRunner;
import com.parallax.selfcalling.util.ShortcutSets;

public class ShortcutRunActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent i = getIntent();
        int id = -1;
        if (i != null) {
            if (i.hasExtra("set_id")) id = i.getIntExtra("set_id", -1);
            else if (i.hasExtra("shortcut_set_id")) id = i.getIntExtra("shortcut_set_id", -1);
            else if (i.getData() != null) {
                try { id = Integer.parseInt(i.getData().getLastPathSegment()); } catch (Exception ignored) {}
            }
        }
        boolean ok = id >= 0 && ShortcutRunner.run(this, id);
        if (!ok) {
            Toast.makeText(this, "Shortcut set not found or empty", Toast.LENGTH_SHORT).show();
        }
        finish();
    }
}
