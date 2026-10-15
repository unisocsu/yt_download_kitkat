package com.example.ytdl;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

/** Downloads in ~9.5MB range chunks (googlevideo throttles/refuses long single requests). */
public final class Downloader {
    private static final long CHUNK = 10L * 1024 * 1024 - 1;

    public interface Progress {
        void onProgress(long done, long total);
    }

    private Downloader() {}

    public static void download(Context ctx, YouTube.Info info, File out, Progress p) throws Exception {
        FileOutputStream fos = new FileOutputStream(out);
        long done = 0;
        try {
            if (info.length <= 0) {
                done = copy(open(ctx, info.url), fos, done, 0, p);
            } else {
                long pos = 0;
                while (pos < info.length) {
                    long end = Math.min(pos + CHUNK, info.length - 1);
                    done = copy(open(ctx, info.url + "&range=" + pos + "-" + end), fos, done, info.length, p);
                    pos = end + 1;
                }
            }
        } catch (Exception e) {
            fos.close();
            out.delete();
            throw e;
        }
        fos.close();
    }

    private static InputStream open(Context ctx, String u) throws Exception {
        HttpsURLConnection c = (HttpsURLConnection) new URL(u).openConnection();
        c.setSSLSocketFactory(CertStore.get(ctx));
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setRequestProperty("User-Agent", YouTube.USER_AGENT);
        int code = c.getResponseCode();
        if (code != 200 && code != 206) throw new Exception("HTTP " + code + " בהורדה");
        return c.getInputStream();
    }

    private static long copy(InputStream in, FileOutputStream out, long done, long total, Progress p)
            throws Exception {
        byte[] buf = new byte[32 * 1024];
        int n;
        try {
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                done += n;
                p.onProgress(done, total);
            }
        } finally {
            in.close();
        }
        return done;
    }
}
