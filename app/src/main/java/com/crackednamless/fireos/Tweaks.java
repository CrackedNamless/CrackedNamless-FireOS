package com.crackednamless.fireos;

import android.content.pm.PackageManager;

import java.util.ArrayList;
import java.util.List;

// Le regolazioni del menu strumenti, condivise con la prima configurazione automatica.
final class Tweaks {

    // Gli stessi pacchetti dei nostri script firestick_clean_extra.sh e firestick_optimize.sh.
    static final String[] PACKAGES = {
            "com.amazon.hybridadidservice", "com.amazon.sneakpeek", "com.amazon.tv.turnstile", "com.amazon.logan",
            "com.amazon.fireos.cirruscloud", "com.amazon.ods.kindleconnect", "com.amazon.aria", "com.amazon.hedwig",
            "com.amazon.tv.support", "com.amazon.ceviche", "com.amazon.d3", "com.amazon.spiderpork",
            "com.amazon.tv.easyupgrade", "com.amazon.sharingservice.android.client.proxy",
            "com.amazon.privacypassservice", "com.amazon.tv.legal.notices", "com.amazon.whisperplay.service.install",
            "com.amazon.bueller.music", "com.amazon.bueller.photos", "com.amazon.gamehub",
            "com.amazon.imdb.tv.android.app", "com.amazon.minitv.android.app", "com.amazon.naatyam",
            "com.amazon.perfcollection", "com.amazon.recess", "com.amazon.shoptv.client",
            "com.amazon.shoptv.firetv.client", "com.amazon.storm.lightning.tutorial", "com.amazon.tmm.tutorial",
            "com.amazon.tv.acr", "com.amazon.tv.fw.metrics", "com.amazon.tv.releasenotes",
            "com.amazon.wirelessmetrics.service"
    };

    private Tweaks() {
    }

    static List<String> performanceCommands(boolean on) {
        String anim = on ? "0" : "0.5";
        String ads = on ? "1" : "0";
        List<String> commands = new ArrayList<>();
        commands.add("settings put global window_animation_scale " + anim);
        commands.add("settings put global transition_animation_scale " + anim);
        commands.add("settings put secure amz_limit_ad_tracking " + ads);
        commands.add("settings put secure non_amz_limit_ad_tracking " + ads);
        return commands;
    }

    // Un comando pm disable-user per ogni servizio presente su questo Stick e ancora attivo.
    static List<String> disableCommands(PackageManager pm) {
        List<String> commands = new ArrayList<>();
        for (String pkg : PACKAGES) {
            try {
                pm.getApplicationInfo(pkg, PackageManager.GET_DISABLED_COMPONENTS);
                int state = pm.getApplicationEnabledSetting(pkg);
                if (state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                        && state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER) {
                    commands.add("pm disable-user --user 0 " + pkg);
                }
            } catch (PackageManager.NameNotFoundException ignored) {
                // Pacchetto non presente su questo Stick.
            }
        }
        return commands;
    }
}
