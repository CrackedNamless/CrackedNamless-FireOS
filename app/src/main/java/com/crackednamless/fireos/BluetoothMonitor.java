package com.crackednamless.fireos;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public final class BluetoothMonitor {

    public static final class Device {
        public final String name;
        public final int kind;
        public final boolean connected;
        final BluetoothDevice raw;
        public final String address;

        Device(BluetoothDevice raw, String name, int kind, boolean connected) {
            this.raw = raw;
            this.address = raw.getAddress();
            this.name = name;
            this.kind = kind;
            this.connected = connected;
        }
    }

    public static final class State {
        public final boolean supported;
        public final boolean enabled;
        public final List<Device> devices;
        public final int connectedCount;

        State(boolean supported, boolean enabled, List<Device> devices) {
            this.supported = supported;
            this.enabled = enabled;
            this.devices = devices;
            int n = 0;
            for (Device d : devices) {
                if (d.connected) {
                    n++;
                }
            }
            this.connectedCount = n;
        }
    }

    public interface Listener {
        void onChanged(State state);
    }

    private static final int HID_HOST = 4;

    private final Context context;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean running;

    private final Runnable publish = new Runnable() {
        @Override
        public void run() {
            if (running) {
                listener.onChanged(read());
            }
        }
    };

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent intent) {
            // Lo stato ACL si aggiorna un attimo dopo l'evento.
            main.removeCallbacks(publish);
            main.postDelayed(publish, 400L);
        }
    };

    public BluetoothMonitor(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    public void start() {
        if (running) {
            return;
        }
        running = true;
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        context.registerReceiver(receiver, filter);
        main.post(publish);
    }

    public void stop() {
        if (!running) {
            return;
        }
        running = false;
        main.removeCallbacks(publish);
        try {
            context.unregisterReceiver(receiver);
        } catch (IllegalArgumentException ignored) {
            // Gia' rimosso.
        }
    }

    // I profili Bluetooth non espongono connect() nelle API pubbliche: si usa la riflessione.
    public void connect(Device device, Runnable onFailed) {
        BluetoothDevice target = device.raw;
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            main.post(onFailed);
            return;
        }
        int[] profiles = profilesFor(target.getBluetoothClass());
        int[] pending = {profiles.length};
        boolean[] accepted = {false};
        for (int profile : profiles) {
            boolean asked = adapter.getProfileProxy(context, new BluetoothProfile.ServiceListener() {
                @Override
                public void onServiceConnected(int p, BluetoothProfile proxy) {
                    try {
                        Method m = proxy.getClass().getMethod("connect", BluetoothDevice.class);
                        Object r = m.invoke(proxy, target);
                        if (!(r instanceof Boolean) || (Boolean) r) {
                            accepted[0] = true;
                        }
                    } catch (ReflectiveOperationException | RuntimeException e) {
                        // Profilo non utilizzabile per questo dispositivo.
                    } finally {
                        adapter.closeProfileProxy(p, proxy);
                        finish(pending, accepted, onFailed);
                    }
                }

                @Override
                public void onServiceDisconnected(int p) {
                }
            }, profile);
            if (!asked) {
                finish(pending, accepted, onFailed);
            }
        }
    }

    private void finish(int[] pending, boolean[] accepted, Runnable onFailed) {
        if (--pending[0] == 0 && !accepted[0]) {
            main.post(onFailed);
        }
    }

    private static int[] profilesFor(BluetoothClass c) {
        int major = c == null ? -1 : c.getMajorDeviceClass();
        if (major == BluetoothClass.Device.Major.AUDIO_VIDEO) {
            return new int[]{BluetoothProfile.A2DP, BluetoothProfile.HEADSET};
        }
        if (major == BluetoothClass.Device.Major.PERIPHERAL) {
            return new int[]{HID_HOST};
        }
        return new int[]{BluetoothProfile.A2DP, BluetoothProfile.HEADSET, HID_HOST};
    }

    public void refreshNow() {
        main.removeCallbacks(publish);
        main.post(publish);
    }

    private static State read() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            return new State(false, false, Collections.<Device>emptyList());
        }
        boolean enabled = adapter.isEnabled();
        List<Device> out = new ArrayList<>();
        if (enabled) {
            Set<BluetoothDevice> bonded = adapter.getBondedDevices();
            if (bonded != null) {
                for (BluetoothDevice d : bonded) {
                    String name = d.getName() != null ? d.getName() : d.getAddress();
                    out.add(new Device(d, name, kind(d.getBluetoothClass()), isConnected(d)));
                }
            }
            Collections.sort(out, (a, b) -> a.connected == b.connected
                    ? a.name.compareToIgnoreCase(b.name) : (a.connected ? -1 : 1));
        }
        return new State(true, enabled, out);
    }

    private static boolean isConnected(BluetoothDevice d) {
        try {
            Method m = BluetoothDevice.class.getMethod("isConnected");
            return (Boolean) m.invoke(d);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    static int kind(BluetoothClass c) {
        if (c == null) {
            return R.string.bt_kind_device;
        }
        switch (c.getMajorDeviceClass()) {
            case BluetoothClass.Device.Major.AUDIO_VIDEO:
                return R.string.bt_kind_audio;
            case BluetoothClass.Device.Major.PERIPHERAL:
                return R.string.bt_kind_input;
            case BluetoothClass.Device.Major.PHONE:
                return R.string.bt_kind_phone;
            case BluetoothClass.Device.Major.COMPUTER:
                return R.string.bt_kind_computer;
            default:
                return R.string.bt_kind_device;
        }
    }
}
