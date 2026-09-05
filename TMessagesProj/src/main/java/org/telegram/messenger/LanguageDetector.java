package org.telegram.messenger;

public class LanguageDetector {
    public interface StringCallback {
        void run(String str);
    }
    public interface ExceptionCallback {
        void run(Exception e);
    }

    public static boolean hasSupport() {
        return false; // ML Kit removed in FOSS
    }

    public static void detectLanguage(String text, StringCallback onSuccess, ExceptionCallback onFail) {
        // Fallback or local simple script detection
        if (onFail != null) {
            onFail.run(new UnsupportedOperationException("ML Kit is disabled in FOSS build"));
        }
    }
}
