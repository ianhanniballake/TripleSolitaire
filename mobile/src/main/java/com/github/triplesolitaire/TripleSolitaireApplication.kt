package com.github.triplesolitaire;

import android.app.Application;
import android.os.StrictMode;

/**
 * Creates the Triple Solitaire application, setting strict mode in debug mode
 */
public class TripleSolitaireApplication extends Application {
    /**
     * Sets strict mode if we are in debug mode
     *
     * @see android.app.Application#onCreate()
     */
    @Override
    public void onCreate() {
        super.onCreate();
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder().detectDiskReads().detectDiskWrites()
                    .detectNetwork().penaltyLog().penaltyFlashScreen().build());
        }
    }
}
