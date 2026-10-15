package com.example.ytdl;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.HttpsURLConnection;

/** Minimal InnerTube client. Client name/version live in constants: update them if YouTube changes behaviour. */
public final class YouTube {
    public static final String CLIENT_NAME = "ANDROID";
    public static final String CLIENT_VERSION = "19.09.37";
    public static final String USER_AGENT =
            "com.google.android.youtube/" + CLIENT_VERSION + " (Linux; U; Android 11) gzip";

    public static class Info {
        public String title;
        public String url;
        public long length;   // bytes, 0 if unknown
        public String ext;    // mp4 / m4a
    }

    private static final Pattern[] ID_PATTERNS = {
            Pattern.compile("[?&]v=([A-Za-z0-9_-]{11})"),
            Pattern.compile("youtu\\.be/([A-Za-z0-9_-]{11})"),
            Pattern.compile("/(?:shorts|embed|live|v)/([A-Za-z0-9_-]{11})"),
            Pattern.compile("^\\s*([A-Za-z0-9_-]{11})\\s*$")
    };

    private YouTube() {}

    public static String parseId(String text) {
        if (text == null) return null;
        for (Pattern p : ID_PATTERNS) {
            Matcher m = p.matcher(text);
            if (m.find()) return m.group(1);
        }
        return null;
    }

    public static Info resolve(Context ctx, String videoId, boolean audioOnly) throws Exception {
        JSONObject root = new JSONObject(fetchPlayer(ctx, videoId));

        JSONObject ps = root.optJSONObject("playabilityStatus");
        if (ps != null && !"OK".equals(ps.optString("status"))) {
            throw new Exception("YouTube: " + ps.optString("status") + " - " + ps.optString("reason"));
        }
        JSONObject sd = root.optJSONObject("streamingData");
        if (sd == null) throw new Exception("לא התקבלו נתוני סטרים");

        JSONObject details = root.optJSONObject("videoDetails");
        String title = details != null ? details.optString("title", videoId) : videoId;

        JSONObject f = audioOnly ? pickAudio(sd) : pickVideo(sd);
        if (f == null) throw new Exception("לא נמצא פורמט עם קישור ישיר (ייתכן שנדרש עדכון ה-client)");

        Info info = new Info();
        info.title = title;
        info.url = f.getString("url");
        info.length = f.optLong("contentLength", 0);
        info.ext = audioOnly ? "m4a" : "mp4";
        return info;
    }

    private static JSONObject pickVideo(JSONObject sd) throws JSONException {
        JSONArray formats = sd.optJSONArray("formats");   // progressive: video+audio together
        if (formats == null) return null;
        JSONObject fallback = null;
        for (int i = 0; i < formats.length(); i++) {
            JSONObject f = formats.getJSONObject(i);
            if (!f.has("url")) continue;
            if (f.optInt("itag") == 18) return f;          // 360p mp4
            if (fallback == null && f.optString("mimeType").startsWith("video/mp4")) fallback = f;
        }
        return fallback;
    }

    private static JSONObject pickAudio(JSONObject sd) throws JSONException {
        JSONArray fmts = sd.optJSONArray("adaptiveFormats");
        if (fmts == null) return null;
        JSONObject fallback = null;
        for (int i = 0; i < fmts.length(); i++) {
            JSONObject f = fmts.getJSONObject(i);
            if (!f.has("url")) continue;
            if (f.optInt("itag") == 140) return f;          // m4a 128kbps
            if (fallback == null && f.optString("mimeType").startsWith("audio/mp4")) fallback = f;
        }
        return fallback;
    }

    private static String fetchPlayer(Context ctx, String videoId) throws Exception {
        URL url = new URL("https://www.youtube.com/youtubei/v1/player?prettyPrint=false");
        HttpsURLConnection c = (HttpsURLConnection) url.openConnection();
        c.setSSLSocketFactory(CertStore.get(ctx));
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setRequestMethod("POST");
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("User-Agent", USER_AGENT);
        c.setDoOutput(true);

        JSONObject client = new JSONObject()
                .put("clientName", CLIENT_NAME)
                .put("clientVersion", CLIENT_VERSION)
                .put("androidSdkVersion", 30)
                .put("hl", "en");
        JSONObject body = new JSONObject()
                .put("context", new JSONObject().put("client", client))
                .put("videoId", videoId);

        OutputStream os = c.getOutputStream();
        os.write(body.toString().getBytes("UTF-8"));
        os.close();

        int code = c.getResponseCode();
        InputStream is = code >= 400 ? c.getErrorStream() : c.getInputStream();
        BufferedReader r = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line);
        r.close();
        if (code >= 400) throw new Exception("HTTP " + code);
        return sb.toString();
    }
}
