package com.crackednamless.fireos;

import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

// Client minimo del protocollo ADB: autenticazione con chiave RSA e comandi "shell:" eseguiti uno alla volta.
final class AdbClient {

    static final class AuthException extends IOException {
        AuthException(String message) {
            super(message);
        }
    }

    private static final int CNXN = 0x4e584e43;
    private static final int AUTH = 0x48545541;
    private static final int OPEN = 0x4e45504f;
    private static final int OKAY = 0x59414b4f;
    private static final int CLSE = 0x45534c43;
    private static final int WRTE = 0x45545257;
    private static final int VERSION = 0x01000001;
    private static final int MAX_DATA = 4096;
    private static final int COMMAND_TIMEOUT_MS = 30_000;
    private static final byte[] SHA1_PREFIX = {0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05,
            0x00, 0x04, 0x14};

    private final Socket socket = new Socket();
    private int nextId = 1;
    private DataInputStream in;
    private OutputStream out;

    private static final class Message {
        final int command;
        final int arg0;
        final int arg1;
        final byte[] data;

        Message(int command, int arg0, int arg1, byte[] data) {
            this.command = command;
            this.arg0 = arg0;
            this.arg1 = arg1;
            this.data = data;
        }
    }

    void connect(String host, int port, KeyPair keys, int authWaitMs) throws IOException {
        socket.connect(new InetSocketAddress(host, port), 4000);
        socket.setTcpNoDelay(true);
        in = new DataInputStream(socket.getInputStream());
        out = socket.getOutputStream();
        send(CNXN, VERSION, MAX_DATA, "host::\0".getBytes(StandardCharsets.UTF_8));
        socket.setSoTimeout(5000);
        boolean signed = false;
        boolean keySent = false;
        try {
            while (true) {
                Message m = read();
                if (m.command == CNXN) {
                    socket.setSoTimeout(COMMAND_TIMEOUT_MS);
                    return;
                }
                if (m.command != AUTH || m.arg0 != 1) {
                    throw new IOException("risposta inattesa " + Integer.toHexString(m.command));
                }
                if (!signed) {
                    signed = true;
                    send(AUTH, 2, 0, sign(keys, m.data));
                } else if (!keySent) {
                    keySent = true;
                    send(AUTH, 3, 0, publicKeyText(keys));
                    socket.setSoTimeout(authWaitMs);
                } else {
                    throw new AuthException("chiave rifiutata");
                }
            }
        } catch (SocketTimeoutException e) {
            if (keySent) {
                throw new AuthException("autorizzazione non concessa in tempo");
            }
            throw e;
        }
    }

    // Esegue un comando shell e restituisce l'output (stdout e stderr insieme).
    String shell(String command) throws IOException {
        int id = nextId++;
        send(OPEN, id, 0, ("shell:" + command + "\0").getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        while (true) {
            Message m = read();
            if (m.arg1 != id) {
                continue;
            }
            if (m.command == WRTE) {
                result.write(m.data);
                send(OKAY, id, m.arg0, new byte[0]);
            } else if (m.command == CLSE) {
                return result.toString("UTF-8").trim();
            }
        }
    }

    void close() {
        try {
            socket.close();
        } catch (IOException ignored) {
            // Gia' chiuso.
        }
    }

    private void send(int command, int arg0, int arg1, byte[] data) throws IOException {
        int sum = 0;
        for (byte x : data) {
            sum += x & 0xff;
        }
        ByteBuffer b = ByteBuffer.allocate(24 + data.length).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(command).putInt(arg0).putInt(arg1).putInt(data.length).putInt(sum).putInt(command ^ 0xffffffff);
        b.put(data);
        out.write(b.array());
        out.flush();
    }

    private Message read() throws IOException {
        byte[] header = new byte[24];
        in.readFully(header);
        ByteBuffer b = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
        int command = b.getInt();
        int arg0 = b.getInt();
        int arg1 = b.getInt();
        int length = b.getInt();
        if (length < 0 || length > (1 << 20)) {
            throw new IOException("messaggio troppo grande");
        }
        byte[] data = new byte[length];
        in.readFully(data);
        return new Message(command, arg0, arg1, data);
    }

    private static byte[] sign(KeyPair keys, byte[] token) throws IOException {
        try {
            Signature s = Signature.getInstance("NONEwithRSA");
            s.initSign(keys.getPrivate());
            s.update(SHA1_PREFIX);
            s.update(token);
            return s.sign();
        } catch (GeneralSecurityException e) {
            throw new IOException("firma non riuscita", e);
        }
    }

    // Formato della chiave pubblica di adb: struttura RSAPublicKey (len, n0inv, n, rr, e) in base64.
    private static byte[] publicKeyText(KeyPair keys) {
        RSAPublicKey pub = (RSAPublicKey) keys.getPublic();
        BigInteger n = pub.getModulus();
        BigInteger r32 = BigInteger.ONE.shiftLeft(32);
        int n0inv = r32.subtract(n.mod(r32).modInverse(r32)).intValue();
        BigInteger rr = BigInteger.ONE.shiftLeft(4096).mod(n);
        ByteBuffer b = ByteBuffer.allocate(4 + 4 + 256 + 256 + 4).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(64).putInt(n0inv);
        putLittleEndian(b, n);
        putLittleEndian(b, rr);
        b.putInt(pub.getPublicExponent().intValue());
        String text = Base64.encodeToString(b.array(), Base64.NO_WRAP) + " fireos@launcher\0";
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static void putLittleEndian(ByteBuffer b, BigInteger value) {
        for (int i = 0; i < 256; i++) {
            b.put((byte) (value.shiftRight(8 * i).intValue() & 0xff));
        }
    }

    static KeyPair loadKeys(File dir) throws IOException, GeneralSecurityException {
        File priv = new File(dir, "adb_key.pk8");
        File pub = new File(dir, "adb_key.x509");
        KeyFactory factory = KeyFactory.getInstance("RSA");
        if (priv.exists() && pub.exists()) {
            try {
                PrivateKey sk = factory.generatePrivate(new PKCS8EncodedKeySpec(read(priv)));
                PublicKey pk = factory.generatePublic(new X509EncodedKeySpec(read(pub)));
                return new KeyPair(pk, sk);
            } catch (GeneralSecurityException | IOException ignored) {
                // File corrotti: si rigenera la chiave.
            }
        }
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keys = generator.generateKeyPair();
        write(priv, keys.getPrivate().getEncoded());
        write(pub, keys.getPublic().getEncoded());
        return keys;
    }

    private static byte[] read(File f) throws IOException {
        try (FileInputStream stream = new FileInputStream(f)) {
            byte[] data = new byte[(int) f.length()];
            int done = 0;
            while (done < data.length) {
                int n = stream.read(data, done, data.length - done);
                if (n < 0) {
                    throw new IOException("file troncato");
                }
                done += n;
            }
            return data;
        }
    }

    private static void write(File f, byte[] data) throws IOException {
        try (FileOutputStream stream = new FileOutputStream(f)) {
            stream.write(data);
        }
    }
}
