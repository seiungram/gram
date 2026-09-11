package org.telegram.messenger;

import android.app.Activity;
import android.content.Context;

import org.unifiedpush.android.connector.UnifiedPush;
import org.unifiedpush.android.connector.data.ResolvedDistributor;

import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * UnifiedPush helpers: distributor lookup, registration, retry scheduling.
 *
 * One shared registration ({@link #UP_INSTANCE}) covers all accounts via
 * registerDevice other_uids, same as FCM. Per-account instances are future work.
 */
public class UnifiedPushController {
    public static final String UP_INSTANCE = "default";
    /** Default Web Push gateway (same one Mercurygram/NagramX use; changeable in settings). */
    public static final String UP_GATEWAY_DEFAULT = "https://p2p.belloworld.it/";
    private static final String RETRY_WORK_TAG = "seiun_unifiedpush_reregister";

    public static boolean isEnabled() {
        return !SharedConfig.disableUnifiedPush;
    }

    public static List<String> getExternalDistributors(Context context) {
        ArrayList<String> result = new ArrayList<>();
        try {
            List<String> all = UnifiedPush.getDistributors(context);
            if (all == null) {
                return result;
            }
            String own = context.getPackageName();
            for (String pkg : all) {
                if (!own.equals(pkg)) {
                    result.add(pkg);
                }
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        return result;
    }

    /** A distributor app is installed (says nothing about registration state). */
    public static boolean hasInstalledDistributor() {
        Context context = ApplicationLoader.applicationContext;
        if (context == null || !isEnabled()) {
            return false;
        }
        try {
            return !getExternalDistributors(context).isEmpty();
        } catch (Throwable e) {
            FileLog.e(e);
            return false;
        }
    }

    /** A distributor is installed and already acked us: ready to register. */
    public static boolean hasAckedDistributor() {
        return isEnabled() && getAckDistributor() != null;
    }

    public static String getAckDistributor() {
        Context context = ApplicationLoader.applicationContext;
        if (context == null) {
            return null;
        }
        try {
            return UnifiedPush.getAckDistributor(context);
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        }
    }

    /**
     * Silent re-register for cold start / boot / retry worker.
     * Skips unless a distributor already acked us.
     */
    public static void registerInBackground() {
        Context context = ApplicationLoader.applicationContext;
        if (context == null || !isEnabled()) {
            return;
        }
        Utilities.globalQueue.postRunnable(() -> {
            try {
                if (UnifiedPush.getAckDistributor(context) == null) {
                    return;
                }
                UnifiedPush.register(context, UP_INSTANCE, null, null);
            } catch (Throwable e) {
                FileLog.e(e);
            }
        });
    }

    /**
     * Register immediately, no ack needed. Use right after the user picks a
     * distributor: the ack only comes back after the first REGISTER.
     */
    public static void registerNow() {
        Context context = ApplicationLoader.applicationContext;
        if (context == null || !isEnabled()) {
            return;
        }
        Utilities.globalQueue.postRunnable(() -> {
            try {
                UnifiedPush.register(context, UP_INSTANCE, null, null);
            } catch (Throwable e) {
                FileLog.e(e);
            }
        });
    }

    /** Posts {@link NotificationCenter#unifiedPushStateChanged} on the UI thread. */
    public static void notifyStateChanged() {
        AndroidUtilities.runOnUIThread(() -> {
            try {
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.unifiedPushStateChanged);
            } catch (Throwable e) {
                FileLog.e(e);
            }
        });
    }

    public enum SetupState {
        FOUND,
        TO_SELECT,
        NONE_AVAILABLE
    }

    public static SetupState resolveSetupState(Context context) {
        try {
            ResolvedDistributor resolved = UnifiedPush.resolveDefaultDistributor(context);
            if (resolved instanceof ResolvedDistributor.Found) {
                return SetupState.FOUND;
            } else if (resolved instanceof ResolvedDistributor.ToSelect) {
                return SetupState.TO_SELECT;
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        return SetupState.NONE_AVAILABLE;
    }

    /**
     * Picks the default distributor (Activity needed for the OS picker) and
     * registers. Warn about the picker first when state is TO_SELECT.
     */
    public static void useDefaultFromActivity(Activity activity, Runnable onDone) {
        try {
            UnifiedPush.tryUseCurrentOrDefaultDistributor(activity, success -> {
                if (success) {
                    registerNow();
                }
                if (onDone != null) {
                    AndroidUtilities.runOnUIThread(onDone);
                }
                return kotlin.Unit.INSTANCE;
            });
        } catch (Throwable e) {
            FileLog.e(e);
            if (onDone != null) {
                AndroidUtilities.runOnUIThread(onDone);
            }
        }
    }

    /** Picks a non-default distributor. Must be called from an Activity. */
    public static void pickFromActivity(Activity activity, Runnable onDone) {
        try {
            UnifiedPush.tryPickDistributor(activity, success -> {
                if (success) {
                    registerNow();
                }
                if (onDone != null) {
                    AndroidUtilities.runOnUIThread(onDone);
                }
                return kotlin.Unit.INSTANCE;
            });
        } catch (Throwable e) {
            FileLog.e(e);
            if (onDone != null) {
                AndroidUtilities.runOnUIThread(onDone);
            }
        }
    }

    public static void unregisterAll() {
        Context context = ApplicationLoader.applicationContext;
        if (context == null) {
            return;
        }
        try {
            UnifiedPush.unregister(context, UP_INSTANCE);
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    /**
     * Schedules a re-register: on reconnect for network failures, after a
     * short delay otherwise (distributor may be restarting).
     */
    public static void scheduleRetry(boolean needsNetwork) {
        Context context = ApplicationLoader.applicationContext;
        if (context == null || !isEnabled()) {
            return;
        }
        try {
            OneTimeWorkRequest.Builder builder = new OneTimeWorkRequest.Builder(UnifiedPushRetryWorker.class);
            if (needsNetwork) {
                builder.setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build());
            } else {
                builder.setInitialDelay(10, TimeUnit.SECONDS);
            }
            WorkManager.getInstance(context).enqueueUniqueWork(RETRY_WORK_TAG, ExistingWorkPolicy.REPLACE, builder.build());
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    /** Simple Push has no payload, so wake up and fetch over MTProto */
    public static void onPushWakeup() {
        ApplicationLoader.postInitApplication();
        PushListenerController.onPushWakeup();
    }
}
