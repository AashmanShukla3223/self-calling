package com.parallax.selfcalling;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Toast;

import com.parallax.selfcalling.databinding.ActivityShortcutsBinding;
import com.parallax.selfcalling.databinding.ItemShortcutBinding;
import com.parallax.selfcalling.util.EdgeToEdge;
import com.parallax.selfcalling.util.ShortcutSets;
import com.parallax.selfcalling.util.ShortcutSet;

import java.util.List;

public class ShortcutSetsActivity extends Activity {

    private ActivityShortcutsBinding b;
    private List<ShortcutSet> items;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityShortcutsBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        EdgeToEdge.apply(this);
        setActionBar(b.toolbar);
        if (getActionBar() != null) getActionBar().setDisplayHomeAsUpEnabled(true);

        b.btnAdd.setOnClickListener(v -> {
            Intent i = new Intent(this, ShortcutEditorActivity.class);
            i.putExtra("set_id", -1);
            startActivity(i);
        });
        reload();
        b.list.setOnItemClickListener((parent, view, position, id) -> {
            // tap runs
            int sid = items.get(position).id;
            if (com.parallax.selfcalling.util.ShortcutRunner.run(this, sid)
                    == com.parallax.selfcalling.util.ShortcutRunner.RunResult.OK) {
                Toast.makeText(this, "Scheduled", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    private void reload() {
        items = ShortcutSets.all(this);
        if (b.list.getAdapter() == null) {
            b.list.setAdapter(new Adapter());
        } else {
            ((BaseAdapter) b.list.getAdapter()).notifyDataSetChanged();
        }
        b.empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private class Adapter extends BaseAdapter {
        @Override public int getCount() { return items.size(); }
        @Override public Object getItem(int position) { return items.get(position); }
        @Override public long getItemId(int position) { return items.get(position).id; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ItemShortcutBinding row;
            if (convertView == null) {
                row = ItemShortcutBinding.inflate(getLayoutInflater(), parent, false);
            } else {
                row = (ItemShortcutBinding) convertView.getTag();
            }
            ShortcutSet s = items.get(position);
            row.rowTitle.setText(s.name);
            row.rowSub.setText(s.steps.size() + " steps");
            row.btnRun.setOnClickListener(v -> {
                if (com.parallax.selfcalling.util.ShortcutRunner.run(ShortcutSetsActivity.this, s.id)
                        == com.parallax.selfcalling.util.ShortcutRunner.RunResult.OK) {
                    Toast.makeText(ShortcutSetsActivity.this, "Scheduled", Toast.LENGTH_SHORT).show();
                }
            });
            row.btnEdit.setOnClickListener(v -> {
                Intent i = new Intent(ShortcutSetsActivity.this, ShortcutEditorActivity.class);
                i.putExtra("set_id", s.id);
                startActivity(i);
            });
            row.btnDelete.setOnClickListener(v -> {
                ShortcutSets.delete(ShortcutSetsActivity.this, s.id);
                reload();
            });
            row.getRoot().setTag(row);
            return row.getRoot();
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
