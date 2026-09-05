package org.telegram.messenger;

import android.app.Activity;

import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.recaptcha.Recaptcha;
import com.google.android.recaptcha.RecaptchaAction;
import com.google.android.recaptcha.RecaptchaTasksClient;

import org.telegram.tgnet.ConnectionsManager;
import org.telegram.ui.LaunchActivity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;

public class CaptchaController {

    public static class Request {
        public int currentAccount;
        public String action;
        public String key_id;

        public Request(int currentAccount, String action, String key_id) {
            this.currentAccount = currentAccount;
            this.action = action;
            this.key_id = key_id;
        }

        @Override
        public int hashCode() {
            return Objects.hash(currentAccount, action, key_id);
        }

        public HashSet<Integer> requestTokens = new HashSet<>();

        public void done(String token) {
            currentRequests.remove(this.hashCode());
            int[] requestTokens = new int[this.requestTokens.size()];
            int i = 0;
            for (Integer requestTokenI : this.requestTokens) {
                requestTokens[i++] = requestTokenI;
            }
            ConnectionsManager.getInstance(currentAccount)
                .native_receivedCaptchaResult(currentAccount, requestTokens, token);
        }
    }

    public static HashMap<Integer, Request> currentRequests;

    public static void request(int currentAccount, int requestToken, String action, String key_id) {
        if (currentRequests == null) {
            currentRequests = new HashMap<>();
        }
        final int key = Objects.hash(currentAccount, action, key_id);
        Request r = currentRequests.get(key);
        if (r == null) {
            r = new Request(currentAccount, action, key_id);
            currentRequests.put(key, r);
        }
        r.requestTokens.add(requestToken);

        // Fail immediately so the Telegram backend offers alternative verification (SMS/call/email)
        r.done("RECAPTCHA_FAILED_PLAY_SERVICES_UNAVAILABLE");
    }

    private static RecaptchaAction getAction(String action) {
        switch (action) {
            case "login":
            case "LOGIN":
                return RecaptchaAction.LOGIN;
            case "signup":
            case "SIGNUP":
                return RecaptchaAction.SIGNUP;
            default:
                return RecaptchaAction.custom(action);
        }
    }

    private static String formatException(Exception e) {
        if (e == null) return "NULL";
        if (e.getMessage() == null) return "MSG_NULL";
        return e.getMessage().replaceAll(" ", "_").toUpperCase();
    }

}
