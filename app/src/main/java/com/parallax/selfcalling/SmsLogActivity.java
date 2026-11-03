package com.parallax.selfcalling;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.parallax.selfcalling.databinding.ActivitySmsLogBinding;
import com.parallax.selfcalling.util.EdgeToEdge;
import com.parallax.selfcalling.util.SmsEntry;
import com.parallax.selfcalling.util.SmsLogStore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** View the SMS auto-reply log: sent/received messages. */
public class SmsLogActivity extends Activity {

    private ActivitySmsLogBinding b;
    private List<SmsEntry> items;
    private final SimpleDateFormat df = new SimpleDateFormat("HH:mm dd/MM", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivitySmsLogBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        EdgeToEdge.apply(this);
        setActionBar(b.toolbar);
        if (getActionBar() != null) getActionBar().setDisplayHomeAsUpEnabled(true);

        reload();

        b.btnClear.setOnClickListener(v -> {
            SmsLogStore.clear(this);
            reload();
            Toast.makeText(this, R.string.log_clear_confirm, Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    private void reload() {
        items = SmsLogStore.all(this);
        if (b.list.getAdapter() == null) {
            b.list.setAdapter(new Adapter());
        } else {
            ((BaseAdapter) b.list.getAdapter()).notifyDataSetChanged();
        }
        b.txtCount.setText(items.size() + " messages");
        b.empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        b.list.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private class Adapter extends BaseAdapter {
        @Override public int getCount() { return items.size(); }
        @Override public Object getItem(int position) { return items.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder h;
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(android.R.layout.simple_list_item_2, parent, false);
                h = new ViewHolder();
                h.txt1 = convertView.findViewById(android.R.id.text1);
                h.txt2 = convertView.findViewById(android.R.id.text2);
                convertView.setTag(h);
            } else {
                h = (ViewHolder) convertView.getTag();
            }

            SmsEntry e = items.get(position);
            String dirLabel = "in".equals(e.direction) ? "RECEIVED" : "SENT";
            h.txt1.setText(dirLabel + "  •  " + e.number);
            h.txt2.setText(e.message + "  ·  " + df.format(new Date(e.timestamp))
                    + "  ·  " + e.status);
            int color = "out".equals(e.direction)
                    ? getResources().getColor(R.color.text_primary)
                    : getResources().getColor(R.color.accent);
            h.txt1.setTextColor(color);
            return convertView;
        }

        private static class ViewHolder {
            TextView txt1;
            TextView txt2;
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
