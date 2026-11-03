package com.parallax.selfcalling;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.Toast;
import androidx.annotation.Nullable;

import com.parallax.selfcalling.databinding.ActivityBackupRestoreBinding;
import com.parallax.selfcalling.util.BackupRestore;
import com.parallax.selfcalling.util.EdgeToEdge;
import com.parallax.selfcalling.util.Prefs;

import java.io.OutputStream;
import java.io.InputStream;

/** Backup and restore presets + shortcut sets to/from a JSON file. */
public class BackupRestoreActivity extends Activity {

    private static final int REQ_EXPORT = 301;
    private static final int REQ_IMPORT = 302;

    private ActivityBackupRestoreBinding b;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityBackupRestoreBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        EdgeToEdge.apply(this);
        setActionBar(b.toolbar);
        if (getActionBar() != null) getActionBar().setDisplayHomeAsUpEnabled(true);

        b.btnExport.setOnClickListener(v -> {
            String json = BackupRestore.export(this);
            if (json.isEmpty()) {
                Toast.makeText(this, "Nothing to export", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("application/json");
            i.putExtra(Intent.EXTRA_TITLE, "selfcalling_backup.json");
            startActivityForResult(i, REQ_EXPORT);
        });

        b.btnImport.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("application/json");
            startActivityForResult(i, REQ_IMPORT);
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;

        Uri uri = data.getData();
        if (requestCode == REQ_EXPORT) {
            new Thread(() -> {
                try {
                    OutputStream out = getContentResolver().openOutputStream(uri);
                    if (out != null) {
                        String json = BackupRestore.export(this);
                        out.write(json.getBytes("UTF-8"));
                        out.close();
                        new Handler(Looper.getMainLooper()).post(() ->
                                Toast.makeText(this, "Exported", Toast.LENGTH_SHORT).show());
                    }
                } catch (Exception e) {
                    new Handler(Looper.getMainLooper()).post(() ->
                            Toast.makeText(this, "Export failed", Toast.LENGTH_SHORT).show());
                }
            }, "backup-export").start();
        } else if (requestCode == REQ_IMPORT) {
            new Thread(() -> {
                try {
                    InputStream in = getContentResolver().openInputStream(uri);
                    if (in != null) {
                        byte[] buf = new byte[in.available()];
                        int read = in.read(buf);
                        in.close();
                        String json = new String(buf, 0, read, "UTF-8");
                        boolean ok = BackupRestore.importData(this, json);
                        new Handler(Looper.getMainLooper()).post(() ->
                                Toast.makeText(this, ok ? "Imported" : "Import failed",
                                        Toast.LENGTH_SHORT).show());
                    }
                } catch (Exception e) {
                    new Handler(Looper.getMainLooper()).post(() ->
                            Toast.makeText(this, "Import failed", Toast.LENGTH_SHORT).show());
                }
            }, "backup-import").start();
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
