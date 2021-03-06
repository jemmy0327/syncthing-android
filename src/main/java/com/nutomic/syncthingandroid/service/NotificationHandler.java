package com.nutomic.syncthingandroid.service;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.support.annotation.StringRes;
import android.support.v4.app.NotificationCompat;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.SyncthingApp;
import com.nutomic.syncthingandroid.activities.FirstStartActivity;
import com.nutomic.syncthingandroid.activities.LogActivity;
import com.nutomic.syncthingandroid.activities.MainActivity;

import javax.inject.Inject;

public class NotificationHandler {

    private static final int ID_PERSISTENT = 1;
    private static final int ID_RESTART = 2;
    private static final int ID_STOP_BACKGROUND_WARNING = 3;
    private static final int ID_CRASH = 9;

    private final Context mContext;
    @Inject SharedPreferences mPreferences;
    private final NotificationManager mNotificationManager;

    public NotificationHandler(Context context) {
        ((SyncthingApp) context.getApplicationContext()).component().inject(this);
        mContext = context;
        mNotificationManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    /**
     * Shows or hides the persistent notification based on running state and
     * {@link Constants#PREF_NOTIFICATION_TYPE}.
     */
    public void updatePersistentNotification(SyncthingService service) {
        String type = mPreferences.getString(Constants.PREF_NOTIFICATION_TYPE, "low_priority");
        boolean foreground = mPreferences.getBoolean(Constants.PREF_FOREGROUND_SERVICE, false);

        // Android 8 does not allow starting service from background unless it's a foreground
        // service, so if "always run in background" is enabled, we have to use a foreground service.
        // https://stackoverflow.com/a/44505719/1837158
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && DeviceStateHolder.alwaysRunInBackground(mContext)) {
            foreground = true;
        }

        // foreground priority requires any notification
        // so this ensures that we either have a "default" or "low_priority" notification,
        // but not "none".
        if ("none".equals(type) && foreground) {
            type = "low_priority";
        }

        boolean syncthingRunning = service.getCurrentState() == SyncthingService.State.ACTIVE ||
                service.getCurrentState() == SyncthingService.State.STARTING;
        if (foreground || (syncthingRunning && !type.equals("none"))) {
            // Launch FirstStartActivity instead of MainActivity so we can request permission if
            // necessary.
            PendingIntent pi = PendingIntent.getActivity(mContext, 0,
                    new Intent(mContext, FirstStartActivity.class), 0);
            int title = syncthingRunning ? R.string.syncthing_active : R.string.syncthing_disabled;
            NotificationCompat.Builder builder = new NotificationCompat.Builder(mContext)
                    .setContentTitle(mContext.getString(title))
                    .setSmallIcon(R.drawable.ic_stat_notify)
                    .setOngoing(true)
                    .setContentIntent(pi);
            if (type.equals("low_priority"))
                builder.setPriority(NotificationCompat.PRIORITY_MIN);

            if (foreground) {
                builder.setContentText(mContext.getString(R.string.syncthing_active_foreground));
                service.startForeground(ID_PERSISTENT, builder.build());
            } else {
                service.stopForeground(false); // ensure no longer running with foreground priority
                mNotificationManager.notify(ID_PERSISTENT, builder.build());
            }
        } else {
            // ensure no longer running with foreground priority
            cancelPersistentNotification(service);
        }
    }

    public void cancelPersistentNotification(SyncthingService service) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && DeviceStateHolder.alwaysRunInBackground(mContext))
            return;

        service.stopForeground(false);
        mNotificationManager.cancel(ID_PERSISTENT);
    }

    public void showCrashedNotification(@StringRes int title, boolean force) {
        if (force || mPreferences.getBoolean("notify_crashes", false)) {
            Intent intent = new Intent(mContext, LogActivity.class);
            Notification n = new NotificationCompat.Builder(mContext)
                    .setContentTitle(mContext.getString(title))
                    .setContentText(mContext.getString(R.string.notification_crash_text))
                    .setSmallIcon(R.drawable.ic_stat_notify)
                    .setContentIntent(PendingIntent.getActivity(mContext, 0, intent, 0))
                    .setAutoCancel(true)
                    .build();
            mNotificationManager.notify(ID_CRASH, n);
        }
    }

    public void showEventNotification(String text, PendingIntent pi, int id) {
        Notification n = new NotificationCompat.Builder(mContext)
                .setContentTitle(mContext.getString(R.string.app_name))
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(text))
                .setContentIntent(pi)
                .setSmallIcon(R.drawable.ic_stat_notify)
                .setAutoCancel(true)
                .build();
        mNotificationManager.notify(id, n);
    }

    public void showRestartNotification() {
        Intent intent = new Intent(mContext, SyncthingService.class)
                .setAction(SyncthingService.ACTION_RESTART);
        PendingIntent pi = PendingIntent.getService(mContext, 0, intent, 0);

        Notification n = new NotificationCompat.Builder(mContext)
                .setContentTitle(mContext.getString(R.string.restart_title))
                .setContentText(mContext.getString(R.string.restart_notification_text))
                .setSmallIcon(R.drawable.ic_stat_notify)
                .setContentIntent(pi)
                .build();
        n.flags |= Notification.FLAG_ONLY_ALERT_ONCE | Notification.FLAG_AUTO_CANCEL;
        mNotificationManager.notify(ID_RESTART, n);
    }

    public void cancelRestartNotification() {
        mNotificationManager.cancel(ID_RESTART);
    }

    public void showStopSyncthingWarningNotification() {
        final String msg = mContext.getString(R.string.appconfig_receiver_background_enabled);
        NotificationCompat.Builder nb = new NotificationCompat.Builder(mContext)
                .setContentText(msg)
                .setTicker(msg)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(msg))
                .setContentTitle(mContext.getText(mContext.getApplicationInfo().labelRes))
                .setSmallIcon(R.drawable.ic_stat_notify)
                .setAutoCancel(true)
                .setContentIntent(PendingIntent.getActivity(mContext, 0,
                        new Intent(mContext, MainActivity.class),
                        PendingIntent.FLAG_UPDATE_CURRENT));


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            nb.setCategory(Notification.CATEGORY_ERROR);
        }
        mNotificationManager.notify(ID_STOP_BACKGROUND_WARNING, nb.build());
    }
}
