package com.crackednamless.fireos;

import java.lang.reflect.Field;

// Lanciato una volta con app_process come utente shell: stampa il codice di transazione di resumeAppSwitches di questo sistema.
public final class AppSwitchProbe {
    private AppSwitchProbe() {
    }

    public static void main(String[] args) throws Exception {
        Field f = Class.forName("android.app.IActivityManager$Stub").getDeclaredField("TRANSACTION_resumeAppSwitches");
        f.setAccessible(true);
        System.out.println("CODE=" + f.getInt(null));
    }
}
