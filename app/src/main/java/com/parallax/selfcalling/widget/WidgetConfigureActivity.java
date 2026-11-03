package com.parallax.selfcalling.widget;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.RemoteViews;
import android.widget.TextView;
import android.widget.Toast;

import com.parallax.selfcalling.R;
import com.parallax.selfcalling.databinding.ActivityWidgetConfigureBinding;
import com.parallax.selfcalling.util.EdgeToEdge;
import com.parallax.selfcalling.util.Prefs;
import com.parallax.selfcalling.util.Preset;
import com.parallax.selfcalling.util.ShortcutSet;
import com.parallax.selfcalling.util.ShortcutSets;

import java.util.ArrayList;
import java.util.List;

/** Configures which shortcut set a widget instance runs. */
public class WidgetConfigureActivity extends Activity {

    private static final String PREFS = "widget_shortcut";
    private static final String SET_ID_KEY = "set_id_";

    private ActivityWidgetConfigureBinding b;
    private int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private List<ShortcutSet> items;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityWidgetConfigureBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        EdgeToEdge.apply(this);
        setActionBar(b.toolbar);
        if (getActionBar() != null) getActionBar().setDisplayHomeAsUpEnabled(true);

        // Get the app widget id from the intent
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            appWidgetId = extras.getInt(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID);
        }
        if (appWidgetId == android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }

        // Set result to CANCELED so if user backs out, widget is not added
        setResult(RESULT_CANCELED);

        b.btnCancel.setOnClickListener(v -> finish());
        reload();
        b.list.setOnItemClickListener((parent, view, position, id) -> {
            ShortcutSet set = items.get(position);
            saveSetId(appWidgetId, set.id);

            // Update the widget immediately
            android.appwidget.AppWidgetManager manager = android.appwidget.AppWidgetManager.getInstance(this);
            // onUpdate won't be called automatically after configure; call update directly
            manager.updateAppWidget(appWidgetId, buildRemoteViews());

            setResult(RESULT_OK, (new Intent()).putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId));
            finish();
        });
    }

    private void reload() {
        items = ShortcutSets.all(this);
        if (b.list.getAdapter() == null) {
            b.list.setAdapter(new Adapter());
        } else {
            ((BaseAdapter) b.list.getAdapter()).notifyDataSetChanged();
        }
        b.empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        b.list.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void saveSetId(int appWidgetId, int setId) {
        SharedPreferences.Editor editor = getSharedPreferences(PREFS, MODE_PRIVATE).edit();
        editor.putInt(SET_ID_KEY + appWidgetId, setId);
        editor.apply();
    }

    private RemoteViews buildRemoteViews() {
        RemoteViews views = new RemoteViews(getPackageName(), R.layout.widget_shortcut);
        int setId = getSharedPreferences(PREFS, MODE_PRIVATE).getInt(SET_ID_KEY + appWidgetId, -1);
        if (setId >= 0) {
            ShortcutSet set = ShortcutSets.get(this, setId);
            if (set != null) {
                views.setTextViewText(R.id.widgetTitle, set.name);
                views.setTextViewText(R.id.widgetSub, set.steps.size() + " steps · tap to run");
            } else {
                views.setTextViewText(R.id.widgetTitle, "Shortcut set");
                views.setTextViewText(R.id.widgetSub, "Not configured");
            }
        } else {
            views.setTextViewText(R.id.widgetTitle, "Shortcut set");
            views.setTextViewText(R.id.widgetSub, "Tap to configure");
        }
        return views;
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

            ShortcutSet set = items.get(position);
            h.txt1.setText(set.name);
            h.txt2.setText(set.steps.size() + " steps");
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
