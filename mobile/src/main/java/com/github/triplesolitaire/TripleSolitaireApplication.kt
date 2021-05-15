package com.github.triplesolitaire

import android.app.Application
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy

/**
 * Creates the Triple Solitaire application, setting strict mode in debug mode
 */
class TripleSolitaireApplication : Application() {
    /**
     * Sets strict mode if we are in debug mode
     *
     * @see android.app.Application.onCreate
     */
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                ThreadPolicy.Builder().detectDiskReads().detectDiskWrites()
                    .detectNetwork().penaltyLog().penaltyFlashScreen().build()
            )
        }
    }
}
