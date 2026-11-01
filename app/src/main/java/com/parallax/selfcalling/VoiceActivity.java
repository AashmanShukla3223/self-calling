package com.parallax.selfcalling;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.MenuItem;
import android.widget.CheckBox;
import android.widget.Toast;

import com.parallax.selfcalling.databinding.ActivityVoiceBinding;
import com.parallax.selfcalling.util.EdgeToEdge;
import com.parallax.selfcalling.util.Prefs;
import com.parallax.selfcalling.util.Preset;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.List;

/** Configures what is heard during the call for one identity: silence, live mic, or an audio file. */
public class VoiceActivity extends Activity {

    private static final int REQ_PICK = 11;
    private static final int REQ_MIC = 12;
    private static final int REQ_RECORD = 13;

    private ActivityVoiceBinding b;
    private int index;
    private Preset preset;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityVoiceBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        EdgeToEdge.apply(this);
        setActionBar(b.toolbar);
        if (getActionBar() != null) {
            getActionBar().setDisplayHomeAsUpEnabled(true);
        }

        index = getIntent().getIntExtra("preset_index", -1);
        if (index < 0) {
            index = Prefs.activeIndex(this);
        }
        sync();

        b.voiceGroup.setOnCheckedChangeListener((g, id) -> {
            String target = id == R.id.radioMic ? Preset.VOICE_MIC
                    : id == R.id.radioFile ? Preset.VOICE_FILE
                    : Preset.VOICE_NONE;
            if (target.equals(preset.voice)) return;
            if (Preset.VOICE_FILE.equals(target) && !hasFile()) {
                Toast.makeText(this, R.string.voice_need_file, Toast.LENGTH_SHORT).show();
                sync();
                return;
            }
            save(target, preset.chainedFiles(), preset.video);
        });

        b.chkVideo.setOnCheckedChangeListener((v, checked) -> {
            if (checked == preset.video) return;
            save(preset.voice, preset.chainedFiles(), checked);
        });

        b.btnImport.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("audio/*");
            startActivityForResult(Intent.createChooser(i, getString(R.string.voice_import)), REQ_PICK);
        });

        b.btnRecord.setOnClickListener(v -> {
            if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, REQ_MIC);
            } else {
                startActivityForResult(new Intent(this, RecordActivity.class), REQ_RECORD);
            }
        });

        b.btnClearAudio.setOnClickListener(v -> {
            save(Preset.VOICE_FILE, new java.util.ArrayList<>(), preset.video);
            Toast.makeText(this, "Audio cleared", Toast.LENGTH_SHORT).show();
        });
    }

    private boolean hasFile() {
        return !preset.chainedFiles().isEmpty();
    }

    private void sync() {
        List<Preset> all = Prefs.presets(this);
        index = Math.max(0, Math.min(index, all.size() - 1));
        preset = all.get(index);
        if (Preset.VOICE_FILE.equals(preset.voice)) {
            b.radioFile.setChecked(true);
        } else if (Preset.VOICE_NONE.equals(preset.voice)) {
            b.radioNone.setChecked(true);
        } else {
            b.radioMic.setChecked(true);
        }
        java.util.List<String> cf = preset.chainedFiles();
        if (cf.isEmpty()) {
            b.fileLabel.setText(getString(R.string.voice_no_file));
        } else if (cf.size() == 1) {
            b.fileLabel.setText(new File(cf.get(0)).getName());
        } else {
            b.fileLabel.setText(cf.size() + " files chained");
        }
        b.chkVideo.setChecked(preset.video);
    }

    private void save(String mode, List<String> files, boolean video) {
        List<Preset> all = Prefs.presets(this);
        Preset old = all.get(index);
        String first = (files == null || files.isEmpty()) ? null : files.get(0);
        all.set(index, new Preset(old.name, old.number, mode, first, files, video));
        Prefs.savePresets(this, all);
        preset = all.get(index);
        sync();
        Toast.makeText(this, R.string.voice_saved, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_RECORD && resultCode == RESULT_OK && data != null) {
            String path = data.getStringExtra("path");
            if (path != null) {
                java.util.List<String> chain = new java.util.ArrayList<>(preset.chainedFiles());
                chain.add(path);
                save(Preset.VOICE_FILE, chain, preset.video);
            }
            return;
        }
        if (requestCode == REQ_PICK && resultCode == RESULT_OK
                && data != null && data.getData() != null) {
            importAudio(data.getData());
        }
    }

    private void importAudio(Uri uri) {
        try {
            File dir = new File(getFilesDir(), "audio");
            dir.mkdirs();
            File out = new File(dir, "imported_" + queryName(uri));
            InputStream in = getContentResolver().openInputStream(uri);
            FileOutputStream fout = new FileOutputStream(out);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                fout.write(buf, 0, n);
            }
            fout.close();
            in.close();
            java.util.List<String> chain = new java.util.ArrayList<>(preset.chainedFiles());
            chain.add(out.getAbsolutePath());
            save(Preset.VOICE_FILE, chain, preset.video);
            Toast.makeText(this, R.string.voice_imported, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, R.string.voice_import_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private String queryName(Uri uri) {
        String name = "audio";
        Cursor cur = null;
        try {
            cur = getContentResolver().query(uri, null, null, null, null);
            if (cur != null && cur.moveToFirst()) {
                int col = cur.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (col >= 0 && cur.getString(col) != null) {
                    name = cur.getString(col);
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (cur != null) cur.close();
        }
        name = name.replaceAll("[^A-Za-z0-9._-]", "_");
        if (name.length() > 60) {
            name = name.substring(name.length() - 60);
        }
        return name;
    }

    @Override
    protected void onResume() {
        super.onResume();
        sync();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQ_MIC && grantResults.length > 0
                && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            startActivity(new Intent(this, RecordActivity.class));
        }
    }
}
