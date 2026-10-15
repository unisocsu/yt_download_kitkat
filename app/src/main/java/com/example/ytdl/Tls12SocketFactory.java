package com.example.ytdl;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/** KitKat has TLS 1.1/1.2 disabled on client sockets by default; this turns them on. */
public class Tls12SocketFactory extends SSLSocketFactory {
    private final SSLSocketFactory d;

    public Tls12SocketFactory(SSLSocketFactory delegate) { this.d = delegate; }

    private Socket on(Socket s) {
        if (s instanceof SSLSocket) {
            ((SSLSocket) s).setEnabledProtocols(new String[]{"TLSv1.2", "TLSv1.1", "TLSv1"});
        }
        return s;
    }

    @Override public String[] getDefaultCipherSuites() { return d.getDefaultCipherSuites(); }
    @Override public String[] getSupportedCipherSuites() { return d.getSupportedCipherSuites(); }
    @Override public Socket createSocket(Socket s, String h, int p, boolean a) throws IOException { return on(d.createSocket(s, h, p, a)); }
    @Override public Socket createSocket(String h, int p) throws IOException { return on(d.createSocket(h, p)); }
    @Override public Socket createSocket(String h, int p, InetAddress la, int lp) throws IOException { return on(d.createSocket(h, p, la, lp)); }
    @Override public Socket createSocket(InetAddress h, int p) throws IOException { return on(d.createSocket(h, p)); }
    @Override public Socket createSocket(InetAddress a, int p, InetAddress la, int lp) throws IOException { return on(d.createSocket(a, p, la, lp)); }
}
