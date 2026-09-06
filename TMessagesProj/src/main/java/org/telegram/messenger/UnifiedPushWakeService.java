package org.telegram.messenger;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

/**
 * Foreground guard for wake-up fetches. A WakeLock keeps the CPU on but not
 * the process: once the distributor unbinds, a dead-started process can be
 * killed before the fetch finishes. This service holds foreground importance
 * for the fetch, then stops itself. Transient, never restarts keep-alive.
 */
public class UnifiedPushWakeService extends Service {
    private static final String CHANNEL_ID = "up_wake";
    private static final int NOTIFICATION_ID = 21001;
    private static final long STOP_DELAY_MS = 25_000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable stopRunnable = this::stopSelf;
    private boolean fetchStarted;

    public static void start(Context context) {
        try {
            Intent intent = new Intent(context, UnifiedPushWakeService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } catch (Throwable e) {
            FileLog.e(e);
            UnifiedPushController.onPushWakeup();
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NOTIFICATION_ID, createNotification());
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!fetchStarted) {
            fetchStarted = true;
            ApplicationLoader.postInitApplication();
            Utilities.stageQueue.postRunnable(() -> {
                try {
                    PushListenerController.onPushWakeup();
                } catch (Throwable e) {
                    FileLog.e(e);
                }
            });
            handler.postDelayed(stopRunnable, STOP_DELAY_MS);
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(stopRunnable);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager.getNotificationChannel(CHANNEL_ID) == null) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        getString(R.string.UnifiedPushSync),
                        NotificationManager.IMPORTANCE_LOW);
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification() {
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return builder
                .setSmallIcon(R.drawable.notification)
                .setContentTitle(getString(R.string.AppName))
                .setContentText(getString(R.string.UnifiedPushSync))
                .setCategory(Notification.CATEGORY_SERVICE)
                .setOngoing(true)
                .setShowWhen(false)
                .setPriority(Notification.PRIORITY_MIN)
                .build();
    }
}
