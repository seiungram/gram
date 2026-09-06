package org.telegram.messenger;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.unifiedpush.android.connector.UnifiedPush;

/**
 * Re-registers with the distributor after a failure or unregistration.
*/
public class UnifiedPushRetryWorker extends Worker {
    public UnifiedPushRetryWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            if (!UnifiedPushController.isEnabled()) {
                return Result.success();
            }
            // Acked: plain re-register. Saved but not acked (e.g. right after
            // the distributor unregistered us): register anyway, the ack
            // comes back after the first REGISTER.
            if (UnifiedPushController.getAckDistributor() != null) {
                UnifiedPushController.registerInBackground();
            } else {
                try {
                    if (UnifiedPush.getSavedDistributor(getApplicationContext()) == null) {
                        return Result.success();
                    }
                } catch (Throwable e) {
                    FileLog.e(e);
                    return Result.success();
                }
                UnifiedPushController.registerNow();
            }
            return Result.success();
        } catch (Throwable e) {
            FileLog.e(e);
            return Result.retry();
        }
    }
}
