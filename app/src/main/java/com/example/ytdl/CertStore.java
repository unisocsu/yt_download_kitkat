package com.example.ytdl;

import android.content.Context;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

/**
 * Builds an SSL socket factory that trusts ONLY the root certificates bundled in
 * assets/cacert.pem (Mozilla CA list, includes Google Trust Services roots),
 * so the app works even if the old KitKat system store is outdated.
 */
public final class CertStore {
    private static SSLSocketFactory factory;

    private CertStore() {}

    public static synchronized SSLSocketFactory get(Context ctx) throws Exception {
        if (factory != null) return factory;

        String pem = readAsset(ctx, "cacert.pem");
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        ks.load(null, null);

        String end = "-----END CERTIFICATE-----";
        int count = 0;
        for (String block : pem.split(end)) {
            int start = block.indexOf("-----BEGIN CERTIFICATE-----");
            if (start < 0) continue;
            String one = block.substring(start) + end + "\n";
            try {
                Certificate c = cf.generateCertificate(new ByteArrayInputStream(one.getBytes("US-ASCII")));
                ks.setCertificateEntry("ca" + (count++), c);
            } catch (Exception ignored) {
                // skip a certificate this Android version cannot parse
            }
        }
        if (count == 0) throw new IllegalStateException("No certificates loaded");

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ks);
        SSLContext sc = SSLContext.getInstance("TLSv1.2");
        sc.init(null, tmf.getTrustManagers(), null);
        factory = new Tls12SocketFactory(sc.getSocketFactory());
        return factory;
    }

    private static String readAsset(Context ctx, String name) throws Exception {
        InputStream in = ctx.getAssets().open(name);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString("US-ASCII");
        } finally {
            in.close();
        }
    }
}
