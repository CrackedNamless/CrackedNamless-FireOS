package com.crackednamless.fireos;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructPollfd;

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Fire OS rifiuta le connessioni ADB che l'app apre verso se stessa. Una VPN su una subnet privata riscrive gli indirizzi
// dei pacchetti, cosi' adbd vede un peer diverso dallo Stick. Il traffico normale non passa dalla VPN.
public final class TunnelVpnService extends VpnService {
    static final String VIRTUAL_HOST = "10.113.77.8";
    private static final byte[] TUN_ADDR = {10, 113, 77, 1};
    private static final byte[] FAKE_PEER = {10, 113, 77, 7};
    private static final byte[] VIRTUAL = {10, 113, 77, 8};

    private static volatile TunnelVpnService active;

    private final Map<Integer, byte[]> appAddresses = new ConcurrentHashMap<>();
    private volatile boolean running;
    private ParcelFileDescriptor tun;
    private byte[] real;

    static void start(Context context) {
        context.startService(new Intent(context, TunnelVpnService.class));
    }

    // Il sistema tiene un binding sul servizio VPN: stopService non basta, va chiuso il tunnel.
    static void stop(Context context) {
        TunnelVpnService s = active;
        if (s != null) {
            s.shutdown();
        }
        context.stopService(new Intent(context, TunnelVpnService.class));
    }

    static boolean isActive() {
        return active != null && active.running;
    }

    // Vero se un'altra VPN e' attiva: stabilire la nostra la sostituirebbe.
    static boolean otherVpnUp(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        for (Network n : cm.getAllNetworks()) {
            NetworkCapabilities caps = cm.getNetworkCapabilities(n);
            if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (running) {
            return START_NOT_STICKY;
        }
        real = localAddress();
        if (real == null || otherVpnUp(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        Builder builder = new Builder()
                .setSession(getString(R.string.vpn_session))
                .addAddress("10.113.77.1", 24)
                .addRoute("10.113.77.0", 24)
                .setMtu(1500)
                .setBlocking(true);
        tun = builder.establish();
        if (tun == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        running = true;
        active = this;
        Thread relay = new Thread(this::relayLoop, "tunnel-relay");
        relay.setDaemon(true);
        relay.start();
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        shutdown();
    }

    @Override
    public void onRevoke() {
        shutdown();
        stopSelf();
    }

    private void shutdown() {
        running = false;
        if (active == this) {
            active = null;
        }
    }

    private void relayLoop() {
        ParcelFileDescriptor fd = tun;
        if (fd == null) {
            return;
        }
        byte[] p = new byte[32768];
        FileDescriptor tunFd = fd.getFileDescriptor();
        StructPollfd[] poll = {new StructPollfd()};
        poll[0].fd = tunFd;
        poll[0].events = (short) OsConstants.POLLIN;
        try {
            // Una read bloccante terrebbe aperto il tunnel anche dopo la chiusura: si legge solo quando poll lo consente.
            while (running) {
                int n;
                try {
                    if (Os.poll(poll, 500) <= 0) {
                        continue;
                    }
                    n = Os.read(tunFd, p, 0, p.length);
                } catch (ErrnoException e) {
                    if (e.errno == OsConstants.EAGAIN || e.errno == OsConstants.EINTR) {
                        continue;
                    }
                    throw e;
                }
                if (n <= 0) {
                    break;
                }
                int length = rewrite(p, n);
                if (length > 0) {
                    Os.write(tunFd, p, 0, length);
                }
            }
        } catch (ErrnoException | InterruptedIOException ignored) {
            // Tunnel chiuso.
        }
        try {
            fd.close();
        } catch (IOException ignored) {
            // Gia' chiuso.
        }
        if (tun == fd) {
            tun = null;
        }
    }

    // Riscrive indirizzi e checksum del pacchetto in p. Restituisce la lunghezza da scrivere, 0 per scartarlo.
    private int rewrite(byte[] p, int n) {
        if (n < 40 || (p[0] >> 4) != 4 || p[9] != 6) {
            return 0;
        }
        int ihl = (p[0] & 15) * 4;
        int total = u16(p, 2);
        if (total > n || total < ihl + 20 || (p[6] & 0x3f) != 0 || p[7] != 0) {
            return 0;
        }
        int srcPort = u16(p, ihl);
        int dstPort = u16(p, ihl + 2);
        if (equal(p, 16, VIRTUAL)) {
            appAddresses.put(srcPort, Arrays.copyOfRange(p, 12, 16));
            System.arraycopy(FAKE_PEER, 0, p, 12, 4);
            System.arraycopy(real, 0, p, 16, 4);
        } else if (equal(p, 12, real) && equal(p, 16, FAKE_PEER)) {
            byte[] app = appAddresses.get(dstPort);
            if (app == null) {
                return 0;
            }
            System.arraycopy(VIRTUAL, 0, p, 12, 4);
            System.arraycopy(app, 0, p, 16, 4);
        } else {
            return 0;
        }
        ipChecksum(p, ihl);
        tcpChecksum(p, ihl, total);
        return total;
    }

    private static boolean equal(byte[] p, int offset, byte[] address) {
        for (int i = 0; i < 4; i++) {
            if (p[offset + i] != address[i]) {
                return false;
            }
        }
        return true;
    }

    private static int u16(byte[] p, int offset) {
        return ((p[offset] & 0xff) << 8) | (p[offset + 1] & 0xff);
    }

    private static void ipChecksum(byte[] p, int ihl) {
        p[10] = 0;
        p[11] = 0;
        int sum = sum(p, 0, ihl, 0);
        put16(p, 10, ~fold(sum));
    }

    private static void tcpChecksum(byte[] p, int ihl, int total) {
        int tcpLength = total - ihl;
        p[ihl + 16] = 0;
        p[ihl + 17] = 0;
        int sum = sum(p, 12, 8, 0) + 6 + tcpLength;
        sum = sum(p, ihl, tcpLength, sum);
        put16(p, ihl + 16, ~fold(sum));
    }

    private static int sum(byte[] p, int offset, int length, int start) {
        long sum = start & 0xffffffffL;
        int i = offset;
        int end = offset + length;
        while (i + 1 < end) {
            sum += u16(p, i);
            i += 2;
        }
        if (i < end) {
            sum += (p[i] & 0xff) << 8;
        }
        while (sum > 0x7fffffffL) {
            sum = (sum & 0xffff) + (sum >> 16);
        }
        return (int) sum;
    }

    private static int fold(int sum) {
        while ((sum >> 16) != 0) {
            sum = (sum & 0xffff) + (sum >> 16);
        }
        return sum & 0xffff;
    }

    private static void put16(byte[] p, int offset, int value) {
        p[offset] = (byte) (value >> 8);
        p[offset + 1] = (byte) value;
    }

    // Indirizzo IPv4 con cui adbd e' raggiungibile (Wi-Fi o Ethernet).
    private static byte[] localAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                String name = ni.getName();
                if (!ni.isUp() || ni.isLoopback() || !(name.startsWith("wlan") || name.startsWith("eth"))) {
                    continue;
                }
                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress a = addresses.nextElement();
                    if (a instanceof Inet4Address && !a.isLoopbackAddress()) {
                        return a.getAddress();
                    }
                }
            }
        } catch (IOException ignored) {
            // Nessuna rete.
        }
        return null;
    }
}
