package com.crackednamless.fireos;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.List;

public final class Store {

    // Aptoide TV is installed by tools/install.sh; the Amazon Appstore ships with Fire OS.
    public static final String APTOIDE = "cm.aptoidetv.pt";
    public static final String AMAZON = "com.amazon.venezia";

    private Store() {
    }

    // The Amazon Appstore only declares the phone-style LAUNCHER category, so both are tried.
    public static Intent intent(PackageManager pm, String pkg) {
        for (String category : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            Intent query = new Intent(Intent.ACTION_MAIN).addCategory(category).setPackage(pkg);
            List<ResolveInfo> found = pm.queryIntentActivities(query, 0);
            if (!found.isEmpty()) {
                ResolveInfo info = found.get(0);
                return new Intent(Intent.ACTION_MAIN)
                        .addCategory(category)
                        .setClassName(info.activityInfo.packageName, info.activityInfo.name)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            }
        }
        return null;
    }
}
