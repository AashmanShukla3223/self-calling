package com.parallax.selfcalling;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.parallax.selfcalling.databinding.ActivityShortcutEditorBinding;
import com.parallax.selfcalling.util.EdgeToEdge;
import com.parallax.selfcalling.util.Prefs;
import com.parallax.selfcalling.util.Preset;
import com.parallax.selfcalling.util.ShortcutSet;
import com.parallax.selfcalling.util.ShortcutSets;
import com.parallax.selfcalling.util.ShortcutStep;

import java.util.ArrayList;
import java.util.List;

public class ShortcutEditorActivity extends Activity {

    private ActivityShortcutEditorBinding b;
    private int setId = -1;
    private final List<ShortcutStep> steps = new ArrayList<>();
    private String name = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityShortcutEditorBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        EdgeToEdge.apply(this);
        setActionBar(b.toolbar);
        if (getActionBar() != null) getActionBar().setDisplayHomeAsUpEnabled(true);

        setId = getIntent().getIntExtra("set_id", -1);
        if (setId >= 0) {
            ShortcutSet existing = ShortcutSets.get(this, setId);
            if (existing != null) {
                name = existing.name;
                steps.addAll(existing.steps);
            }
        } else {
            setId = ShortcutSets.nextId(this);
        }
        b.edtName.setText(name);

        // preset palette
        List<Preset> presets = Prefs.presets(this);
        List<String> names = new ArrayList<>();
        for (Preset p : presets) names.add(p.name + " — " + p.number);
        ArrayAdapter<String> pa = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, names);
        b.presetList.setAdapter(pa);
        b.presetList.setOnItemClickListener((parent, view, position, id) -> {
            Preset snap = presets.get(position);
            int delay = steps.isEmpty() ? 0 : steps.get(steps.size() - 1).delaySec + 60;
            new android.app.AlertDialog.Builder(this)
                    .setTitle("Add step for " + snap.name)
                    .setItems(new String[]{"Simulated incoming call", "Send SMS message", "Outgoing call"},
                            (d, w) -> {
                                if (w == 0) {
                                    steps.add(new com.parallax.selfcalling.util.ShortcutStep(
                                            snap, delay, "call", "", -1, -2));
                                } else if (w == 1) {
                                    steps.add(new com.parallax.selfcalling.util.ShortcutStep(
                                            snap, delay, "message", "", -1, -2));
                                } else {
                                    steps.add(new com.parallax.selfcalling.util.ShortcutStep(
                                            snap, delay, "outgoing", "", -1, -2));
                                }
                                refreshSteps();
                            })
                    .show();
        });

        b.btnBulkAdd.setOnClickListener(v -> {
            boolean[] checked = new boolean[names.size()];
            new AlertDialog.Builder(this)
                    .setTitle("Add multiple presets as steps")
                    .setMultiChoiceItems(names.toArray(new String[0]), checked,
                            (d, w, isChecked) -> checked[w] = isChecked)
                    .setPositiveButton("Add", (d, w) -> {
                        int baseDelay = steps.isEmpty() ? 0 : steps.get(steps.size() - 1).delaySec + 60;
                        int added = 0;
                        for (int i = 0; i < checked.length; i++) {
                            if (checked[i]) {
                                Preset snap = presets.get(i);
                                steps.add(new com.parallax.selfcalling.util.ShortcutStep(
                                        snap, baseDelay + (added * 60), "call", "", -1, -2));
                                added++;
                            }
                        }
                        if (added > 0) {
                            refreshSteps();
                            Toast.makeText(this, added + " steps added", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        });

        b.btnSave.setOnClickListener(v -> {
            if (save()) {
                Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show();
                finish();
            }
        });
        b.btnRun.setOnClickListener(v -> {
            if (save()) {
                if (com.parallax.selfcalling.util.ShortcutRunner.run(this, setId)
                        == com.parallax.selfcalling.util.ShortcutRunner.RunResult.OK) {
                    Toast.makeText(this, "Scheduled", Toast.LENGTH_SHORT).show();
                }
            }
        });
        b.btnPin.setOnClickListener(v -> {
            if (save()) {
                pinShortcut();
            }
        });

        refreshSteps();
    }

    private boolean save() {
        String n = b.edtName.getText().toString().trim();
        if (n.isEmpty()) {
            Toast.makeText(this, "Name required", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (steps.isEmpty()) {
            Toast.makeText(this, "Add at least one step", Toast.LENGTH_SHORT).show();
            return false;
        }
        ShortcutSet set = new ShortcutSet(setId, n, new ArrayList<>(steps));
        ShortcutSets.upsert(this, set);
        name = n;
        return true;
    }

    private void refreshSteps() {
        b.stepsContainer.removeAllViews();
        for (int i = 0; i < steps.size(); i++) {
            final int idx = i;
            ShortcutStep st = steps.get(i);
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(12, 12, 12, 12);
            card.setBackgroundResource(R.drawable.card);

            TextView title = new TextView(this);
            String typeLabel;
            if (st.type.equals(com.parallax.selfcalling.util.ShortcutStep.TYPE_MESSAGE)) {
                typeLabel = "SMS";
            } else if (st.type.equals(com.parallax.selfcalling.util.ShortcutStep.TYPE_OUTGOING)) {
                typeLabel = "OUT";
            } else {
                typeLabel = "CALL";
            }
            title.setText((i + 1) + ". [" + typeLabel + "] " + st.snapshot.name + " (" + st.snapshot.number + ")");
            title.setTextColor(getResources().getColor(R.color.text_primary));
            card.addView(title);

            TextView sub = new TextView(this);
            if (st.type.equals(com.parallax.selfcalling.util.ShortcutStep.TYPE_MESSAGE)) {
                sub.setText("delay " + st.delaySec + "s · \"" + st.messageText + "\"");
            } else if (st.type.equals(com.parallax.selfcalling.util.ShortcutStep.TYPE_OUTGOING)) {
                sub.setText("delay " + st.delaySec + "s · outgoing call");
            } else {
                String rt = st.ringTimeoutSec >= 0 ? (st.ringTimeoutSec + "s ring") : "global ring";
                sub.setText("delay " + st.delaySec + "s · " + rt);
            }
            sub.setTextColor(getResources().getColor(R.color.text_secondary));
            card.addView(sub);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            android.widget.Button edit = new android.widget.Button(this);
            edit.setText("Edit");
            edit.setOnClickListener(v -> editStep(idx));
            android.widget.Button dup = new android.widget.Button(this);
            dup.setText("Dup");
            dup.setOnClickListener(v -> {
                steps.add(idx + 1, new ShortcutStep(st.snapshot, st.delaySec, st.type, st.messageText, st.ringTimeoutSec, st.autoAnswerSec));
                refreshSteps();
            });
            android.widget.Button up = new android.widget.Button(this);
            up.setText("▲");
            up.setOnClickListener(v -> {
                if (idx > 0) {
                    ShortcutStep moved = steps.remove(idx);
                    steps.add(idx - 1, moved);
                    refreshSteps();
                }
            });
            android.widget.Button dn = new android.widget.Button(this);
            dn.setText("▼");
            dn.setOnClickListener(v -> {
                if (idx < steps.size() - 1) {
                    ShortcutStep moved = steps.remove(idx);
                    steps.add(idx + 1, moved);
                    refreshSteps();
                }
            });
            android.widget.Button del = new android.widget.Button(this);
            del.setText("Remove");
            del.setOnClickListener(v -> {
                steps.remove(idx);
                refreshSteps();
            });
            row.addView(edit);
            row.addView(dup);
            row.addView(up);
            row.addView(dn);
            row.addView(del);
            card.addView(row);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 0, 8);
            b.stepsContainer.addView(card, lp);
        }
    }

    private void editStep(int idx) {
        ShortcutStep st = steps.get(idx);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        final EditText delay = new EditText(this);
        delay.setHint("Delay seconds from run start");
        delay.setText(String.valueOf(st.delaySec));
        final EditText msg = new EditText(this);
        msg.setHint("Message text (SMS steps only)");
        msg.setText(st.messageText);
        final android.widget.Spinner typeSpin = new android.widget.Spinner(this);
        ArrayAdapter<String> ta = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item,
                new String[]{"Call", "Message", "Outgoing"});
        ta.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        typeSpin.setAdapter(ta);
        int curSel = st.type.equals(com.parallax.selfcalling.util.ShortcutStep.TYPE_MESSAGE) ? 1
                : st.type.equals(com.parallax.selfcalling.util.ShortcutStep.TYPE_OUTGOING) ? 2 : 0;
        typeSpin.setSelection(curSel);
        container.addView(new android.widget.TextView(this) {{
            setText("Type");
            setTextColor(getResources().getColor(R.color.text_primary));
        }});
        container.addView(typeSpin);
        container.addView(delay);
        final EditText rt = new EditText(this);
        rt.setHint("Ring timeout seconds (-1 = global, 0 = never)");
        rt.setText(String.valueOf(st.ringTimeoutSec));
        container.addView(rt);
        container.addView(msg);

        new AlertDialog.Builder(this)
                .setTitle("Edit step")
                .setView(container)
                .setPositiveButton("Save", (d, w) -> {
                    String t = typeSpin.getSelectedItem().toString();
                    String newType;
                    if (t.equals("Message")) {
                        newType = com.parallax.selfcalling.util.ShortcutStep.TYPE_MESSAGE;
                    } else if (t.equals("Outgoing")) {
                        newType = com.parallax.selfcalling.util.ShortcutStep.TYPE_OUTGOING;
                    } else {
                        newType = com.parallax.selfcalling.util.ShortcutStep.TYPE_CALL;
                    }
                    int dVal = 0;
                    int rVal = st.ringTimeoutSec;
                    try { dVal = Integer.parseInt(delay.getText().toString().trim()); } catch (Exception ignored) {}
                    try { rVal = Integer.parseInt(rt.getText().toString().trim()); } catch (Exception ignored) {}
                    String mText = msg.getText().toString().trim();
                    steps.set(idx, new ShortcutStep(st.snapshot, Math.max(0, dVal),
                            newType, mText, rVal, st.autoAnswerSec));
                    refreshSteps();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void pinShortcut() {
        if (Build.VERSION.SDK_INT < 25) {
            Toast.makeText(this, "Pinning requires Android 7.1+", Toast.LENGTH_SHORT).show();
            return;
        }
        android.content.pm.ShortcutManager sm = getSystemService(android.content.pm.ShortcutManager.class);
        if (sm == null || !sm.isRequestPinShortcutSupported()) {
            Toast.makeText(this, "Pinning not supported", Toast.LENGTH_SHORT).show();
            return;
        }
        String n = b.edtName.getText().toString().trim();
        Intent intent = new Intent(this, ShortcutRunActivity.class);
        intent.putExtra("set_id", setId);
        android.content.pm.ShortcutInfo shortcut = new android.content.pm.ShortcutInfo.Builder(this, "shortcut_set_" + setId)
                .setShortLabel(n)
                .setLongLabel(n)
                .setIcon(android.graphics.drawable.Icon.createWithResource(this, R.mipmap.ic_launcher))
                .setIntent(intent)
                .build();
        sm.requestPinShortcut(shortcut, null);
        Toast.makeText(this, "Pin requested", Toast.LENGTH_SHORT).show();
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
