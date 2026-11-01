package com.parallax.selfcalling;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.parallax.selfcalling.databinding.ActivityLogBinding;
import com.parallax.selfcalling.util.EdgeToEdge;
import com.parallax.selfcalling.databinding.ItemCallBinding;
import com.parallax.selfcalling.util.CallEntry;
import com.parallax.selfcalling.util.CallLogStore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class LogActivity extends Activity {

    private ActivityLogBinding b;
    private List<CallEntry> items;
    private final SimpleDateFormat fmt = new SimpleDateFormat("EEE, MMM d · HH:mm", Locale.getDefault());

    private class Adapter extends BaseAdapter {
        @Override public int getCount() { return items.size(); }
        @Override public Object getItem(int position) { return items.get(position); }
        @Override public long getItemId(int position) { return items.get(position).ts; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ItemCallBinding row;
            if (convertView == null) {
                row = ItemCallBinding.inflate(getLayoutInflater(), parent, false);
            } else {
                Object tag = convertView.getTag();
                row = (ItemCallBinding) tag;
            }
            CallEntry e = items.get(position);
            boolean out = "out".equals(e.kind);
            row.rowIcon.setImageResource(out ? R.drawable.ic_call_made : R.drawable.ic_call_received);
            row.rowTitle.setText(e.video
                    ? (out ? R.string.log_video_out : R.string.log_video_in)
                    : (out ? R.string.log_out : R.string.log_in));
            String sub = fmt.format(new Date(e.ts));
            if (e.autoAnswered) {
                sub += " · " + getString(R.string.log_auto);
            }
            row.rowSub.setText(sub);
            if (e.answered) {
                row.rowDur.setText(CallEntry.fmtDuration(e.durationSec));
            } else {
                row.rowDur.setText(out ? R.string.log_canceled : R.string.log_missed);
            }
            row.getRoot().setTag(row);
            return row.getRoot();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityLogBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        EdgeToEdge.apply(this);
        setActionBar(b.toolbar);
        if (getActionBar() != null) {
            getActionBar().setDisplayHomeAsUpEnabled(true);
        }
        items = CallLogStore.all(this);
        b.list.setAdapter(new Adapter());
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    private void reload() {
        items = CallLogStore.all(this);
        ((BaseAdapter) b.list.getAdapter()).notifyDataSetChanged();
        b.empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        b.list.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_log, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_clear) {
            new AlertDialog.Builder(this)
                    .setMessage(R.string.log_clear_confirm)
                    .setPositiveButton(R.string.log_clear, (d, w) -> {
                        CallLogStore.clear(LogActivity.this);
                        reload();
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            return true;
        }
        if (id == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
