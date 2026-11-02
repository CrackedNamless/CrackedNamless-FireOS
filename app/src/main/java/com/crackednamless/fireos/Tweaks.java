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
            "com.amazon.wirelessmetrics.service", "com.amazon.stillwatching.activity", "com.amazon.tv.website_launcher",
            "com.amazon.whad", "com.amazon.whasettings", "com.amazon.wha.mediabrowserservice",
            "com.amazon.client.metrics.api", "com.amazon.neo.minerva", "com.amazon.neo.minerva.logcat",
            "com.amazon.perfc", "com.amazon.dp.logger", "com.amazon.tv.developer.dataservice",
            "com.amazon.livedeviceservice", "com.amazon.wifilocker", "com.amazon.kso.blackbird",
            "com.amazon.uxnotification", "com.amazon.systemnotices", "com.amazon.cloud9",
            "com.amazon.whisperjoin.middleware.np"
    };

    // Svuota tutte le cache delle app: lo spazio richiesto e' enorme, cosi' il sistema le libera tutte.
    static final String TRIM_CACHES = "pm trim-caches 100G";

    static List<String> frameRateCommands(boolean on) {
        return java.util.Collections.singletonList("settings put global match_content_frame_rate " + (on ? "1" : "0"));
    }

    static final String ADGUARD_DNS = "dns.adguard-dns.com";

    static List<String> adblockDnsCommands(boolean on) {
        List<String> commands = new ArrayList<>();
        if (on) {
            commands.add("settings put global private_dns_specifier " + ADGUARD_DNS);
            commands.add("settings put global private_dns_mode hostname");
        } else {
            commands.add("settings put global private_dns_mode opportunistic");
            commands.add("settings delete global private_dns_specifier");
        }
        return commands;
    }

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

    // Impedisce a Prime Video di restare attivo in background (sempre "ignore", mai un interruttore).
    static final String PRIME_BLOCK = "for p in com.amazon.avod com.amazon.firebat; do "
            + "cmd appops set $p RUN_IN_BACKGROUND ignore; cmd appops set $p RUN_ANY_IN_BACKGROUND ignore; "
            + "am force-stop $p; done; echo ignore";

    // Essenziali: restano sempre libere di partire da sole (Home, Appstore, profili, impostazioni, voce Alexa, sistema Android).
    private static final String[] ESSENTIAL_EXACT = {
            "android", "com.amazon.tv.launcher", "com.amazon.venezia", "com.amazon.ftv.profilepicker", "com.amazon.ssm"
    };
    private static final String[] ESSENTIAL_PREFIX = {
            "com.android.", "com.amazon.tv.settings", "com.amazon.device.", "com.amazon.vizzini", "com.amazon.alexa",
            "com.amazon.firetv.inputmethod", "com.amazon.bluetooth", "com.google.android."
    };

    static boolean isEssential(String pkg, String self) {
        if (pkg.equals(self)) {
            return true;
        }
        for (String exact : ESSENTIAL_EXACT) {
            if (pkg.equals(exact)) {
                return true;
            }
        }
        for (String prefix : ESSENTIAL_PREFIX) {
            if (pkg.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    // App con icona che non sono essenziali: non possono piu' avviarsi o restare attive da sole (si aprono solo quando le scegli tu).
    static List<String> nonEssentialApps(PackageManager pm, String self) {
        java.util.Set<String> found = new java.util.TreeSet<>();
        for (String category : new String[]{android.content.Intent.CATEGORY_LEANBACK_LAUNCHER, android.content.Intent.CATEGORY_LAUNCHER}) {
            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(category);
            for (android.content.pm.ResolveInfo info : pm.queryIntentActivities(intent, 0)) {
                String pkg = info.activityInfo.packageName;
                if (!isEssential(pkg, self)) {
                    found.add(pkg);
                }
            }
        }
        return new ArrayList<>(found);
    }

    static String autoStartBlockCommand(List<String> packages) {
        return "for p in " + String.join(" ", packages) + "; do "
                + "cmd appops set $p RUN_IN_BACKGROUND ignore; cmd appops set $p RUN_ANY_IN_BACKGROUND ignore; "
                + "am force-stop $p; done; echo ignore";
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
