package com.example.ytdl;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.HttpsURLConnection;

/**
 * Minimal InnerTube client. Several client identities are tried in order;
 * if YouTube starts rejecting one (HTTP 400/403), the next one is used.
 * Versions below are the part that goes stale - update them when needed.
 */
public final class YouTube {

    public static class Client {
        final String name, version, userAgent, nameId;
        final int sdk;
        final String make, model, osName, osVersion;

        Client(String name, String nameId, String version, String userAgent,
               int sdk, String make, String model, String osName, String osVersion) {
            this.name = name; this.nameId = nameId; this.version = version;
            this.userAgent = userAgent; this.sdk = sdk; this.make = make;
            this.model = model; this.osName = osName; this.osVersion = osVersion;
        }
    }

    public static final Client[] CLIENTS = {
            new Client("ANDROID", "3", "20.10.38",
                    "com.google.android.youtube/20.10.38 (Linux; U; Android 11) gzip",
                    30, null, null, "Android", "11"),
            new Client("ANDROID_VR", "28", "1.60.19",
                    "com.google.android.apps.youtube.vr.oculus/1.60.19 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip",
                    32, "Oculus", "Quest 3", "Android", "12L"),
            new Client("IOS", "5", "20.10.4",
                    "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)",
                    0, "Apple", "iPhone16,2", "iPhone", "18.3.2.22D82")
    };

    public static class Info {
        public String title;
        public String url;
        public long length;   // bytes, 0 if unknown
        public String ext;    // mp4 / m4a
        public String userAgent;
        public String clientName;
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

    public static Info resolve(Context ctx, String videoId, boolean audioOnly, Client client) throws Exception {
        JSONObject root = new JSONObject(fetchPlayer(ctx, videoId, client));

        JSONObject ps = root.optJSONObject("playabilityStatus");
        if (ps != null && !"OK".equals(ps.optString("status"))) {
            throw new Exception(ps.optString("status") + ": " + ps.optString("reason"));
        }
        JSONObject sd = root.optJSONObject("streamingData");
        if (sd == null) throw new Exception("אין streamingData");

        JSONObject details = root.optJSONObject("videoDetails");
        String title = details != null ? details.optString("title", videoId) : videoId;

        JSONObject f = audioOnly ? pickAudio(sd) : pickVideo(sd);
        if (f == null) throw new Exception("אין פורמט עם קישור ישיר");

        Info info = new Info();
        info.title = title;
        info.url = f.getString("url");
        info.length = f.optLong("contentLength", 0);
        info.ext = audioOnly ? "m4a" : "mp4";
        info.userAgent = client.userAgent;
        info.clientName = client.name;
        return info;
    }

    private static JSONObject pickVideo(JSONObject sd) throws JSONException {
        JSONArray formats = sd.optJSONArray("formats");   // progressive: video+audio together
        if (formats == null) return null;
        JSONObject fallback = null;
        for (int i = 0; i < formats.length(); i++) {
            JSONObject f = formats.getJSONObject(i);
            if (!f.has("url")) continue;
            if (f.optInt("itag") == 18) return f;
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
            if (f.optInt("itag") == 140) return f;
            if (fallback == null && f.optString("mimeType").startsWith("audio/mp4")) fallback = f;
        }
        return fallback;
    }

    private static String fetchPlayer(Context ctx, String videoId, Client cl) throws Exception {
        URL url = new URL("https://www.youtube.com/youtubei/v1/player?prettyPrint=false");
        HttpsURLConnection c = (HttpsURLConnection) url.openConnection();
        c.setSSLSocketFactory(CertStore.get(ctx));
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setRequestMethod("POST");
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("User-Agent", cl.userAgent);
        c.setRequestProperty("X-YouTube-Client-Name", cl.nameId);
        c.setRequestProperty("X-YouTube-Client-Version", cl.version);
        c.setDoOutput(true);

        JSONObject client = new JSONObject()
                .put("clientName", cl.name)
                .put("clientVersion", cl.version)
                .put("hl", "en")
                .put("gl", "US");
        if (cl.sdk > 0) client.put("androidSdkVersion", cl.sdk);
        if (cl.make != null) client.put("deviceMake", cl.make);
        if (cl.model != null) client.put("deviceModel", cl.model);
        if (cl.osName != null) client.put("osName", cl.osName);
        if (cl.osVersion != null) client.put("osVersion", cl.osVersion);

        JSONObject body = new JSONObject()
                .put("context", new JSONObject().put("client", client))
                .put("videoId", videoId)
                .put("contentCheckOk", true)
                .put("racyCheckOk", true);

        OutputStream os = c.getOutputStream();
        os.write(body.toString().getBytes("UTF-8"));
        os.close();

        int code = c.getResponseCode();
        InputStream is = code >= 400 ? c.getErrorStream() : c.getInputStream();
        StringBuilder sb = new StringBuilder();
        if (is != null) {
            BufferedReader r = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            r.close();
        }
        if (code >= 400) {
            String msg = "";
            try {
                msg = new JSONObject(sb.toString()).getJSONObject("error").optString("message");
            } catch (Exception ignored) {
                msg = sb.length() > 120 ? sb.substring(0, 120) : sb.toString();
            }
            throw new Exception("HTTP " + code + " " + msg);
        }
        return sb.toString();
    }
}
