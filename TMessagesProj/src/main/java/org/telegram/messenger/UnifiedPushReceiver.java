package org.telegram.messenger;

import android.os.PowerManager;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Base64;

import org.json.JSONObject;
import org.telegram.messenger.utils.WebPushDecryptor;
import org.telegram.tgnet.ConnectionsManager;
import org.unifiedpush.android.connector.FailedReason;
import org.unifiedpush.android.connector.PushService;
import org.unifiedpush.android.connector.UnifiedPush;
import org.unifiedpush.android.connector.data.PushEndpoint;
import org.unifiedpush.android.connector.data.PushMessage;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;

/**
 * UnifiedPush entry point (PUSH_EVENT service). Registers a Web Push token
 * (type 10) plus a Simple Push wake-up URL (type 4), decrypts content pushes,
 * and falls back to a wake-up fetch when there is nothing to decrypt.
 */
public class UnifiedPushReceiver extends PushService {
    private static final String DISTRIBUTOR_NTFY = "io.heckel.ntfy";

    private static final int WAKELOCK_TIMEOUT_MS = 30_000;
    private static final long FALLBACK_THROTTLE_MS = 10_000;
    private static long lastFallbackWakeup;

    private static volatile byte[] webPushPrivateKey;
    private static volatile byte[] webPushPublicKey;
    private static volatile byte[] webPushAuthSecret;

    @Override
    public void onNewEndpoint(PushEndpoint endpoint, String instance) {
        if (SharedConfig.disableUnifiedPush) {
            try {
                UnifiedPush.unregister(this, instance);
            } catch (Throwable e) {
                FileLog.e(e);
            }
            return;
        }
        AndroidUtilities.runOnUIThread(() -> {
            ApplicationLoader.postInitApplication();
            Utilities.globalQueue.postRunnable(() -> {
                ensureWebPushKeys();

                String gateway = SharedConfig.unifiedPushGateway;
                if (TextUtils.isEmpty(gateway)) {
                    gateway = UnifiedPushController.UP_GATEWAY_DEFAULT;
                }
                if (!gateway.endsWith("/")) {
                    gateway += "/";
                }

                try {
                    String distributorEndpoint = endpoint.getUrl();
                    if (webPushPrivateKey == null || webPushPublicKey == null || webPushAuthSecret == null) {
                        FileLog.e("UP: WebPush keys missing, scheduling retry");
                        UnifiedPushController.scheduleRetry(false);
                        UnifiedPushController.notifyStateChanged();
                        return;
                    }
                    if (BuildVars.LOGS_ENABLED) {
                        FileLog.d("UP new endpoint instance=" + instance + " url=" + distributorEndpoint);
                    }
                    String gatewayUrl = gateway + "aesgcm?e=" + URLEncoder.encode(distributorEndpoint, StandardCharsets.UTF_8.name());
                    String p256dh = Base64.encodeToString(webPushPublicKey, Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
                    String auth = Base64.encodeToString(webPushAuthSecret, Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);

                    JSONObject tokenObj = new JSONObject();
                    tokenObj.put("endpoint", gatewayUrl);
                    JSONObject keys = new JSONObject();
                    keys.put("p256dh", p256dh);
                    keys.put("auth", auth);
                    tokenObj.put("keys", keys);

                    String simplePushUrl = DISTRIBUTOR_NTFY.equals(UnifiedPush.getSavedDistributor(this))
                            ? distributorEndpoint
                            : gateway + URLEncoder.encode(distributorEndpoint, StandardCharsets.UTF_8.name());
                    PushListenerController.sendWebPushRegistrationToServer(tokenObj.toString(), simplePushUrl);
                    UnifiedPushController.notifyStateChanged();
                } catch (Exception e) {
                    FileLog.e(e);
                }
            });
        });
    }

    @Override
    public void onMessage(PushMessage message, String instance) {
        if (SharedConfig.disableUnifiedPush) {
            return;
        }
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("UP message instance=" + instance);
        }

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        PowerManager.WakeLock wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "seiun:up");
        wakeLock.acquire(WAKELOCK_TIMEOUT_MS);

        loadWebPushKeys();

        if (webPushPrivateKey != null && webPushPublicKey != null && webPushAuthSecret != null) {
            try {
                byte[] plaintext = WebPushDecryptor.decrypt(message.getContent(), webPushPrivateKey, webPushPublicKey, webPushAuthSecret);
                String encoded = new JSONObject(new String(plaintext, StandardCharsets.UTF_8)).getString("p");
                FileLog.d("WEB START PROCESSING (decrypted)");
                Utilities.globalQueue.postRunnable(() -> {
                    try {
                        PushListenerController.processRemoteMessage(PushListenerController.PUSH_TYPE_WEBPUSH, encoded, System.currentTimeMillis());
                    } finally {
                        releaseWakeLock(wakeLock);
                    }
                });
                return;
            } catch (Exception e) {
                FileLog.e("WEB DECRYPT ERROR, falling back to wake-up: " + e.getMessage());
            }
        }

        long now = SystemClock.elapsedRealtime();
        if (now - lastFallbackWakeup < FALLBACK_THROTTLE_MS) {
            releaseWakeLock(wakeLock);
            return;
        }
        lastFallbackWakeup = now;
        try {
            FileLog.d("UP START PROCESSING (wake-up fallback)");
            if (DISTRIBUTOR_NTFY.equals(savedDistributor())) {
                UnifiedPushWakeService.start(this);
            } else {
                ApplicationLoader.postInitApplication();
                Utilities.stageQueue.postRunnable(PushListenerController::onPushWakeup);
            }
        } finally {
            releaseWakeLock(wakeLock);
        }
    }

    private String savedDistributor() {
        try {
            return UnifiedPush.getSavedDistributor(this);
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        }
    }

    @Override
    public void onRegistrationFailed(FailedReason reason, String instance) {
        if (SharedConfig.disableUnifiedPush) {
            return;
        }
        FileLog.e("Failed to get endpoint: " + reason);
        SharedConfig.pushStringStatus = "__UNIFIEDPUSH_FAILED__";
        Utilities.globalQueue.postRunnable(() ->
                PushListenerController.sendRegistrationToServer(PushListenerController.PUSH_TYPE_WEBPUSH, null));
        UnifiedPushController.notifyStateChanged();
        if (reason != FailedReason.VAPID_REQUIRED) {
            UnifiedPushController.scheduleRetry(reason == FailedReason.NETWORK);
        }
    }

    @Override
    public void onUnregistered(String instance) {
        if (SharedConfig.disableUnifiedPush) {
            return;
        }
        AndroidUtilities.runOnUIThread(() -> {
            ApplicationLoader.postInitApplication();
            SharedConfig.pushStringStatus = "__UNIFIEDPUSH_FAILED__";
            Utilities.globalQueue.postRunnable(() -> {
                PushListenerController.unregisterWebPush();
                PushListenerController.sendRegistrationToServer(PushListenerController.PUSH_TYPE_WEBPUSH, null);
                PushListenerController.unregisterSimplePush();
            });
            UnifiedPushController.notifyStateChanged();
            UnifiedPushController.scheduleRetry(false);
        });
    }

    private static void releaseWakeLock(PowerManager.WakeLock wakeLock) {
        if (wakeLock.isHeld()) {
            try {
                wakeLock.release();
            } catch (RuntimeException ignored) {
            }
        }
    }

    static synchronized void loadWebPushKeys() {
        if (webPushPrivateKey != null && webPushPublicKey != null && webPushAuthSecret != null) {
            return;
        }
        try {
            byte[] privateKey = SharedConfig.webPushPrivateKey;
            byte[] publicKey = SharedConfig.webPushPublicKey;
            byte[] authSecret = SharedConfig.webPushAuthSecret;
            if (privateKey == null || publicKey == null || authSecret == null) {
                return;
            }
            KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(privateKey));
            if (publicKey.length != 65 || publicKey[0] != 0x04 || authSecret.length != 16) {
                throw new IllegalArgumentException("Invalid WebPush keys");
            }
            webPushPrivateKey = privateKey;
            webPushPublicKey = publicKey;
            webPushAuthSecret = authSecret;
        } catch (Exception e) {
            webPushPrivateKey = null;
            webPushPublicKey = null;
            webPushAuthSecret = null;
            SharedConfig.webPushPrivateKey = null;
            SharedConfig.webPushPublicKey = null;
            SharedConfig.webPushAuthSecret = null;
            SharedConfig.saveConfig();
            FileLog.e(e);
        }
    }

    private static synchronized void ensureWebPushKeys() {
        loadWebPushKeys();
        if (webPushPrivateKey != null && webPushPublicKey != null && webPushAuthSecret != null) {
            return;
        }
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
            kpg.initialize(new ECGenParameterSpec("secp256r1"));
            KeyPair keyPair = kpg.generateKeyPair();
            ECPublicKey ecPub = (ECPublicKey) keyPair.getPublic();

            webPushPublicKey = WebPushDecryptor.extractRawPublicKey(ecPub);
            webPushPrivateKey = keyPair.getPrivate().getEncoded();

            byte[] secret = new byte[16];
            Utilities.random.nextBytes(secret);
            webPushAuthSecret = secret;

            SharedConfig.webPushPrivateKey = webPushPrivateKey;
            SharedConfig.webPushPublicKey = webPushPublicKey;
            SharedConfig.webPushAuthSecret = webPushAuthSecret;
            SharedConfig.saveConfig();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }
}
