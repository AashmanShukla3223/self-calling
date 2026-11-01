package com.parallax.selfcalling;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.Toast;

import com.parallax.selfcalling.databinding.ActivityPresetsBinding;
import com.parallax.selfcalling.util.EdgeToEdge;
import com.parallax.selfcalling.databinding.DialogPresetBinding;
import com.parallax.selfcalling.databinding.ItemPresetBinding;
import com.parallax.selfcalling.util.Prefs;
import com.parallax.selfcalling.util.Preset;
import com.parallax.selfcalling.util.PresetImporter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PresetsActivity extends Activity {

    private static final int REQ_CONTACTS = 21;
    private static final int REQ_VCF = 22;

    private ActivityPresetsBinding b;
    private List<Preset> items;

    private class Adapter extends BaseAdapter {
        @Override public int getCount() { return items.size(); }
        @Override public Object getItem(int position) { return items.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ItemPresetBinding row;
            if (convertView == null) {
                row = ItemPresetBinding.inflate(getLayoutInflater(), parent, false);
            } else {
                row = (ItemPresetBinding) convertView.getTag();
            }
            Preset p = items.get(position);
            row.rowTitle.setText(p.name);
            row.rowSub.setText(p.number);
            int active = Prefs.activeIndex(PresetsActivity.this);
            if (position == active) {
                row.rowCheck.setImageResource(R.drawable.ic_check_circle);
                row.rowCheck.setVisibility(View.VISIBLE);
            } else {
                row.rowCheck.setImageResource(R.drawable.ic_clear);
                row.rowCheck.setVisibility(View.INVISIBLE);
            }
            row.getRoot().setTag(row);
            return row.getRoot();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityPresetsBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        EdgeToEdge.apply(this);
        setActionBar(b.toolbar);
        if (getActionBar() != null) {
            getActionBar().setDisplayHomeAsUpEnabled(true);
        }
        reload();
        b.list.setOnItemClickListener((parent, view, position, id) -> {
            Prefs.setActiveIndex(PresetsActivity.this, position);
            ((BaseAdapter) b.list.getAdapter()).notifyDataSetChanged();
        });
        b.list.setOnItemLongClickListener((parent, view, position, id) -> {
            String[] opts = {getString(R.string.preset_edit), getString(R.string.preset_voice),
                    getString(R.string.preset_delete)};
            new AlertDialog.Builder(PresetsActivity.this)
                    .setItems(opts, (d, w) -> {
                        if (w == 0) {
                            showEditor(position);
                        } else if (w == 1) {
                            Intent v = new Intent(PresetsActivity.this, VoiceActivity.class);
                            v.putExtra("preset_index", position);
                            startActivity(v);
                        } else {
                            delete(position);
                        }
                    })
                    .show();
            return true;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    private void reload() {
        items = Prefs.presets(this);
        if (b.list.getAdapter() == null) {
            b.list.setAdapter(new Adapter());
        } else {
            ((BaseAdapter) b.list.getAdapter()).notifyDataSetChanged();
        }
        b.empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        b.list.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void showEditor(final int position) {
        DialogPresetBinding dlg = DialogPresetBinding.inflate(getLayoutInflater());
        final EditText name = dlg.dlgName;
        final EditText number = dlg.dlgNumber;
        if (position >= 0) {
            name.setText(items.get(position).name);
            number.setText(items.get(position).number);
        }
        new AlertDialog.Builder(this)
                .setTitle(position >= 0 ? R.string.preset_edit : R.string.preset_add)
                .setView(dlg.getRoot())
                .setPositiveButton(R.string.preset_save, (d, w) -> {
                    String n = name.getText().toString().trim();
                    String m = number.getText().toString().replaceAll("[^0-9+*#]", "").trim();
                    if (n.isEmpty() || m.isEmpty()) {
                        Toast.makeText(this, R.string.preset_invalid, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    List<Preset> list = new ArrayList<>(items);
                    if (position >= 0) {
                        Preset old = items.get(position);
                        list.set(position, new Preset(n, m, old.voice, old.voiceFile));
                    } else {
                        list.add(new Preset(n, m));
                    }
                    Prefs.savePresets(this, list);
                    if (position < 0) {
                        Prefs.setActiveIndex(this, list.size() - 1);
                    }
                    reload();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void delete(int position) {
        if (items.size() <= 1) {
            Toast.makeText(this, R.string.preset_need_one, Toast.LENGTH_SHORT).show();
            return;
        }
        List<Preset> list = new ArrayList<>(items);
        list.remove(position);
        int active = Prefs.activeIndex(this);
        if (position < active) {
            active--;
        } else if (position == active) {
            active = Math.max(0, Math.min(active, list.size() - 1));
        }
        Prefs.savePresets(this, list);
        Prefs.setActiveIndex(this, active);
        reload();
    }

    // ---- import from contacts / vCard ----

    private void showImportDialog() {
        CharSequence[] options = {
                getString(R.string.preset_import_contacts),
                getString(R.string.preset_import_vcf)
        };
        new AlertDialog.Builder(this)
                .setTitle(R.string.preset_import_title)
                .setItems(options, (d, which) -> {
                    if (which == 0) importContacts();
                    else pickVcfFile();
                })
                .show();
    }

    private void importContacts() {
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, REQ_CONTACTS);
            return;
        }
        runImport(true, null);
    }

    private void pickVcfFile() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "text/x-vcard", "text/vcard", "text/directory",
                "text/plain", "application/octet-stream"
        });
        try {
            startActivityForResult(
                    Intent.createChooser(i, getString(R.string.preset_import_vcf)), REQ_VCF);
        } catch (ActivityNotFoundException e) {
            Intent fallback = new Intent(Intent.ACTION_GET_CONTENT);
            fallback.addCategory(Intent.CATEGORY_OPENABLE);
            fallback.setType("*/*");
            startActivityForResult(
                    Intent.createChooser(fallback, getString(R.string.preset_import_vcf)), REQ_VCF);
        }
    }

    /** Reads contacts or a vCard on a worker thread, then merges on the UI thread. */
    private void runImport(final boolean contacts, final Uri uri) {
        Toast.makeText(this, R.string.preset_import_reading, Toast.LENGTH_SHORT).show();
        final Context app = getApplicationContext();
        new Thread(() -> {
            List<Preset> imported = null;
            try {
                imported = contacts
                        ? PresetImporter.fromContacts(app)
                        : PresetImporter.fromVcf(app, uri);
            } catch (Exception ignored) {
                // Leave imported == null and show the failure toast below.
            }
            final List<Preset> result = imported;
            runOnUiThread(() -> {
                if (result == null) {
                    Toast.makeText(this, R.string.preset_import_failed, Toast.LENGTH_SHORT).show();
                } else if (result.isEmpty()) {
                    Toast.makeText(this, R.string.preset_import_empty, Toast.LENGTH_SHORT).show();
                } else {
                    mergeImported(result);
                }
            });
        }, "preset-import").start();
    }

    /** Appends imported presets, skipping numbers that are already in the list. */
    private void mergeImported(List<Preset> imported) {
        List<Preset> list = new ArrayList<>(Prefs.presets(this));
        Set<String> numbers = new HashSet<>();
        for (Preset p : list) {
            numbers.add(PresetImporter.normalizeNumber(p.number));
        }
        int added = 0;
        for (Preset p : imported) {
            String number = PresetImporter.normalizeNumber(p.number);
            if (number.isEmpty() || !numbers.add(number)) continue;
            list.add(new Preset(p.name, number, p.voice, p.voiceFile));
            added++;
        }
        if (added == 0) {
            Toast.makeText(this, R.string.preset_import_none, Toast.LENGTH_SHORT).show();
            return;
        }
        Prefs.savePresets(this, list);
        reload();
        Toast.makeText(this,
                getResources().getQuantityString(R.plurals.preset_imported, added, added),
                Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_VCF && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                runImport(false, uri);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQ_CONTACTS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                runImport(true, null);
            } else {
                Toast.makeText(this, R.string.preset_import_contacts_denied,
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_presets, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_add) {
            showEditor(-1);
            return true;
        }
        if (id == R.id.action_import) {
            showImportDialog();
            return true;
        }
        if (id == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
