package com.github.triplesolitaire;

import android.app.Application;
import android.content.SharedPreferences;
import android.os.StrictMode;
import android.preference.PreferenceManager;

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
        if (BuildConfig.DEBUG)
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder().detectDiskReads().detectDiskWrites()
                    .detectNetwork().penaltyLog().penaltyFlashScreen().build());
        try {
            setDefaultValues();
        } catch (final Exception e) {
            final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
            preferences.edit().clear().commit();
            setDefaultValues();
        }
    }

    /**
     * Sets the default preference values
     */
    private void setDefaultValues() {
        PreferenceManager.setDefaultValues(this, R.xml.preferences_gameplay, false);
        PreferenceManager.setDefaultValues(this, R.xml.preferences_animation, false);
    }
}
