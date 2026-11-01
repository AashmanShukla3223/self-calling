package com.parallax.selfcalling;

import android.app.Activity;
import android.content.Intent;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.parallax.selfcalling.databinding.ActivityRecordBinding;
import com.parallax.selfcalling.util.EdgeToEdge;

import java.io.File;
import java.util.Locale;

/** Records a voice memo straight into the app's private storage; result is the file path. */
public class RecordActivity extends Activity {

    private static final int REQ_MIC = 20;

    private ActivityRecordBinding b;
    private MediaRecorder rec;
    private File out;
    private boolean recording;
    private boolean finished;
    private int secs;
    private final Handler h = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityRecordBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        EdgeToEdge.apply(this);

        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, REQ_MIC);
        } else {
            startRecording();
        }

        b.recordStop.setOnClickListener(v -> finishRecording());
    }

    private void startRecording() {
        File dir = new File(getFilesDir(), "audio");
        dir.mkdirs();
        out = new File(dir, "recording.m4a");
        try {
            rec = new MediaRecorder();
            rec.setAudioSource(MediaRecorder.AudioSource.MIC);
            rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            rec.setAudioSamplingRate(44100);
            rec.setAudioEncodingBitRate(96000);
            rec.setOutputFile(out.getAbsolutePath());
            rec.prepare();
            rec.start();
            recording = true;
            tick();
        } catch (Exception e) {
            recording = false;
            Toast.makeText(this, R.string.record_failed, Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void tick() {
        if (!recording) return;
        b.recTimer.setText(String.format(Locale.US, "%02d:%02d", secs / 60, secs % 60));
        secs++;
        h.postDelayed(this::tick, 1000);
    }

    private void finishRecording() {
        if (!recording || finished) return;
        finished = true;
        recording = false;
        h.removeCallbacksAndMessages(null);
        boolean ok = true;
        try {
            rec.stop();
        } catch (Exception e) {
            ok = false;
        }
        try {
            rec.release();
        } catch (Exception ignored) {
        }
        rec = null;
        if (!ok) {
            out.delete();
            Toast.makeText(this, R.string.record_failed, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        setResult(RESULT_OK, new Intent().putExtra("path", out.getAbsolutePath()));
        finish();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQ_MIC && grantResults.length > 0
                && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            startRecording();
        } else {
            Toast.makeText(this, R.string.record_failed, Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (!finished) {
            h.removeCallbacksAndMessages(null);
            if (rec != null) {
                try {
                    rec.release();
                } catch (Exception ignored) {
                }
                rec = null;
            }
            if (out != null && out.exists()) {
                out.delete();
            }
        }
    }
}
