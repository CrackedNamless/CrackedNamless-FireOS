package com.crackednamless.fireos;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.List;

public final class Store {

    // In ordine di preferenza: Aptoide TV e' lo store installato da tools/install.sh.
    static final String[] PACKAGES = {"cm.aptoidetv.pt"};

    private Store() {
    }

    public static Intent intent(PackageManager pm) {
        for (String pkg : PACKAGES) {
            Intent query = new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                    .setPackage(pkg);
            List<ResolveInfo> found = pm.queryIntentActivities(query, 0);
            if (!found.isEmpty()) {
                ResolveInfo info = found.get(0);
                return new Intent(Intent.ACTION_MAIN)
                        .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                        .setClassName(info.activityInfo.packageName, info.activityInfo.name)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            }
        }
        return null;
    }
}
