package com.example.ytdl;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.MediaScannerConnection;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

public class MainActivity extends Activity {
    private EditText urlInput;
    private CheckBox audioOnly;
    private Button btn;
    private ProgressBar bar;
    private TextView status;
    private volatile boolean busy;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        urlInput = (EditText) findViewById(R.id.urlInput);
        audioOnly = (CheckBox) findViewById(R.id.audioOnly);
        btn = (Button) findViewById(R.id.downloadBtn);
        bar = (ProgressBar) findViewById(R.id.progress);
        status = (TextView) findViewById(R.id.status);

        btn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { start(); }
        });
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        handleIntent(i);
    }

    private void handleIntent(Intent i) {
        if (i != null && Intent.ACTION_SEND.equals(i.getAction())) {
            String t = i.getStringExtra(Intent.EXTRA_TEXT);
            if (t != null) urlInput.setText(t);
        }
    }

    private void start() {
        if (busy) return;
        final String id = YouTube.parseId(urlInput.getText().toString());
        if (id == null) {
            Toast.makeText(this, "כתובת לא תקינה", Toast.LENGTH_SHORT).show();
            return;
        }
        final boolean audio = audioOnly.isChecked();
        final Context app = getApplicationContext();
        busy = true;
        btn.setEnabled(false);
        bar.setProgress(0);

        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    setStatus("מאתר את הסרטון...");
                    YouTube.Info info = YouTube.resolve(app, id, audio);

                    File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    dir.mkdirs();
                    File out = new File(dir, safeName(info.title) + "." + info.ext);

                    setStatus("מוריד: " + info.title);
                    Downloader.download(app, info, out, new Downloader.Progress() {
                        @Override public void onProgress(final long done, final long total) {
                            runOnUiThread(new Runnable() {
                                @Override public void run() {
                                    if (total > 0) bar.setProgress((int) (done * 100 / total));
                                    status.setText((done / 1024 / 1024) + "MB"
                                            + (total > 0 ? " / " + (total / 1024 / 1024) + "MB" : ""));
                                }
                            });
                        }
                    });

                    MediaScannerConnection.scanFile(app, new String[]{out.getAbsolutePath()}, null, null);
                    setStatus("הסתיים: " + out.getAbsolutePath());
                } catch (Exception e) {
                    setStatus("שגיאה: " + e.getMessage());
                } finally {
                    busy = false;
                    runOnUiThread(new Runnable() {
                        @Override public void run() { btn.setEnabled(true); }
                    });
                }
            }
        }).start();
    }

    private void setStatus(final String s) {
        runOnUiThread(new Runnable() {
            @Override public void run() { status.setText(s); }
        });
    }

    private static String safeName(String t) {
        String s = t.replaceAll("[\\\\/:*?\"<>|\\n\\r]", "_").trim();
        if (s.length() > 80) s = s.substring(0, 80);
        return s.length() == 0 ? "video" : s;
    }
}
