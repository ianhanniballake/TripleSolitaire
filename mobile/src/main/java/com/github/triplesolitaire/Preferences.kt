package com.github.triplesolitaire

import android.app.backup.BackupManager
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.os.Bundle
import android.preference.ListPreference
import android.preference.PreferenceActivity
import android.preference.PreferenceFragment
import android.view.MenuItem

/**
 * Activity managing the various application preferences
 */
class Preferences : PreferenceActivity() {
    /**
     * Preference Fragment showing preferences relating to game play
     */
    class AnimationPreferenceFragment : PreferenceFragment(), OnSharedPreferenceChangeListener {
        /**
         * Reference to the ListPreference corresponding with the auto play animation speed
         */
        private lateinit var animateSpeedAutoplayListPreference: ListPreference

        /**
         * Reference to the ListPreference corresponding with the undo animation speed
         */
        private lateinit var animateSpeedUndoListPreference: ListPreference

        override fun onCreate(savedInstanceState: Bundle) {
            super.onCreate(savedInstanceState)
            addPreferencesFromResource(R.xml.preferences_animation)
            animateSpeedAutoplayListPreference = preferenceScreen.findPreference(
                ANIMATE_SPEED_AUTO_PLAY_PREFERENCE_KEY
            ) as ListPreference
            animateSpeedAutoplayListPreference.summary =
                animateSpeedAutoplayListPreference.entry
            animateSpeedUndoListPreference = preferenceScreen.findPreference(
                ANIMATE_SPEED_UNDO_PREFERENCE_KEY
            ) as ListPreference
            animateSpeedUndoListPreference.summary = animateSpeedUndoListPreference.entry
        }

        override fun onPause() {
            super.onPause()
            preferenceScreen.sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
        }

        override fun onResume() {
            super.onResume()
            preferenceScreen.sharedPreferences.registerOnSharedPreferenceChangeListener(this)
        }

        override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
            if (key == ANIMATE_SPEED_AUTO_PLAY_PREFERENCE_KEY) {
                animateSpeedAutoplayListPreference.summary = animateSpeedAutoplayListPreference.entry
            } else if (key == ANIMATE_SPEED_UNDO_PREFERENCE_KEY) {
                animateSpeedUndoListPreference.summary = animateSpeedUndoListPreference.entry
            }
            BackupManager(activity).dataChanged()
        }
    }

    /**
     * Preference Fragment showing preferences relating to game play
     */
    class GameplayPreferenceFragment : PreferenceFragment(), OnSharedPreferenceChangeListener {
        /**
         * Reference to the ListPreference corresponding with the auto play mode
         */
        private lateinit var autoplayListPreference: ListPreference

        override fun onCreate(savedInstanceState: Bundle) {
            super.onCreate(savedInstanceState)
            addPreferencesFromResource(R.xml.preferences_gameplay)
            autoplayListPreference =
                preferenceScreen.findPreference(AUTO_PLAY_PREFERENCE_KEY) as ListPreference
            autoplayListPreference.summary = autoplayListPreference.entry
        }

        override fun onPause() {
            super.onPause()
            preferenceScreen.sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
        }

        override fun onResume() {
            super.onResume()
            preferenceScreen.sharedPreferences.registerOnSharedPreferenceChangeListener(this)
        }

        override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
            if (key == AUTO_PLAY_PREFERENCE_KEY) {
                autoplayListPreference.summary = autoplayListPreference.entry
            }
            BackupManager(activity).dataChanged()
        }
    }

    override fun onBuildHeaders(target: List<Header>) {
        loadHeadersFromResource(R.xml.preference_headers, target)
    }

    override fun isValidFragment(fragmentName: String): Boolean {
        return when (fragmentName) {
            GameplayPreferenceFragment::class.java.name -> true
            AnimationPreferenceFragment::class.java.name -> true
            else -> false
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onStart() {
        super.onStart()
        actionBar!!.setDisplayHomeAsUpEnabled(true)
    }

    companion object {
        /**
         * Animate Auto Play preference name
         */
        const val ANIMATE_AUTO_PLAY_PREFERENCE_KEY = "animate_auto_play"

        /**
         * Animate Undo preference name
         */
        const val ANIMATE_SPEED_AUTO_PLAY_PREFERENCE_KEY = "animate_speed_auto_play"

        /**
         * Animate Undo preference name
         */
        const val ANIMATE_SPEED_UNDO_PREFERENCE_KEY = "animate_speed_undo"

        /**
         * Animate Undo preference name
         */
        const val ANIMATE_UNDO_PREFERENCE_KEY = "animate_undo"

        /**
         * Auto Flip preference name
         */
        const val AUTO_FLIP_PREFERENCE_KEY = "auto_flip"

        /**
         * Auto Play preference name
         */
        const val AUTO_PLAY_PREFERENCE_KEY = "auto_play"
    }
}
