package com.github.triplesolitaire

import android.app.backup.BackupAgentHelper
import android.app.backup.SharedPreferencesBackupHelper

/**
 * Agent which handles backup of user settings
 */
class TripleSolitaireBackupAgent : BackupAgentHelper() {
    /**
     * Gets the name associated with PreferenceManager.getDefaultPreferences(), as given by the Android source code
     *
     * @return The name associated with PreferenceManager.getDefaultPreferences()
     */
    private val defaultSharedPreferencesName: String
        get() = packageName + "_preferences"

    override fun onCreate() {
        val helper = SharedPreferencesBackupHelper(this, defaultSharedPreferencesName)
        addHelper("preferences", helper)
    }
}
