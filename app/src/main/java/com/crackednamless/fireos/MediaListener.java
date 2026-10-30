package com.crackednamless.fireos;

import android.service.notification.NotificationListenerService;

public class MediaListener extends NotificationListenerService {

    @Override
    public void onListenerConnected() {
        MediaMonitor.refreshNow();
    }
}
