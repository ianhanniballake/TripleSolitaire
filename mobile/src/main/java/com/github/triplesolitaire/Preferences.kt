package com.github.triplesolitaire;

import android.app.backup.BackupManager;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.view.MenuItem;

import java.util.List;

/**
 * Activity managing the various application preferences
 */
public class Preferences extends PreferenceActivity {
    /**
     * Preference Fragment showing preferences relating to game play
     */
    public static class AnimationPreferenceFragment extends PreferenceFragment implements
            OnSharedPreferenceChangeListener {
        /**
         * Reference to the ListPreference corresponding with the auto play animation speed
         */
        private ListPreference animateSpeedAutoplayListPreference;
        /**
         * Reference to the ListPreference corresponding with the undo animation speed
         */
        private ListPreference animateSpeedUndoListPreference;

        @Override
        public void onCreate(final Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.preferences_animation);
            animateSpeedAutoplayListPreference = (ListPreference) getPreferenceScreen().findPreference(
                    ANIMATE_SPEED_AUTO_PLAY_PREFERENCE_KEY);
            animateSpeedAutoplayListPreference.setSummary(animateSpeedAutoplayListPreference.getEntry());
            animateSpeedUndoListPreference = (ListPreference) getPreferenceScreen().findPreference(
                    ANIMATE_SPEED_UNDO_PREFERENCE_KEY);
            animateSpeedUndoListPreference.setSummary(animateSpeedUndoListPreference.getEntry());
        }

        @Override
        public void onPause() {
            super.onPause();
            getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onResume() {
            super.onResume();
            getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onSharedPreferenceChanged(final SharedPreferences sharedPreferences, final String key) {
            if (key.equals(ANIMATE_SPEED_AUTO_PLAY_PREFERENCE_KEY))
                animateSpeedAutoplayListPreference.setSummary(animateSpeedAutoplayListPreference.getEntry());
            else if (key.equals(ANIMATE_SPEED_UNDO_PREFERENCE_KEY))
                animateSpeedUndoListPreference.setSummary(animateSpeedUndoListPreference.getEntry());
            new BackupManager(getActivity()).dataChanged();
        }
    }

    /**
     * Preference Fragment showing preferences relating to game play
     */
    public static class GameplayPreferenceFragment extends PreferenceFragment implements
            OnSharedPreferenceChangeListener {
        /**
         * Reference to the ListPreference corresponding with the auto play mode
         */
        private ListPreference autoplayListPreference;

        @Override
        public void onCreate(final Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.preferences_gameplay);
            autoplayListPreference = (ListPreference) getPreferenceScreen().findPreference(AUTO_PLAY_PREFERENCE_KEY);
            autoplayListPreference.setSummary(autoplayListPreference.getEntry());
        }

        @Override
        public void onPause() {
            super.onPause();
            getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onResume() {
            super.onResume();
            getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onSharedPreferenceChanged(final SharedPreferences sharedPreferences, final String key) {
            if (key.equals(AUTO_PLAY_PREFERENCE_KEY))
                autoplayListPreference.setSummary(autoplayListPreference.getEntry());
            new BackupManager(getActivity()).dataChanged();
        }
    }

    /**
     * Animate Auto Play preference name
     */
    public static final String ANIMATE_AUTO_PLAY_PREFERENCE_KEY = "animate_auto_play";
    /**
     * Animate Undo preference name
     */
    public static final String ANIMATE_SPEED_AUTO_PLAY_PREFERENCE_KEY = "animate_speed_auto_play";
    /**
     * Animate Undo preference name
     */
    public static final String ANIMATE_SPEED_UNDO_PREFERENCE_KEY = "animate_speed_undo";
    /**
     * Animate Undo preference name
     */
    public static final String ANIMATE_UNDO_PREFERENCE_KEY = "animate_undo";
    /**
     * Auto Flip preference name
     */
    public static final String AUTO_FLIP_PREFERENCE_KEY = "auto_flip";
    /**
     * Auto Play preference name
     */
    public static final String AUTO_PLAY_PREFERENCE_KEY = "auto_play";

    @Override
    public void onBuildHeaders(final List<Header> target) {
        loadHeadersFromResource(R.xml.preference_headers, target);
    }

    @Override
    protected boolean isValidFragment(final String fragmentName) {
        if (fragmentName.equals(GameplayPreferenceFragment.class.getName()))
            return true;
        else if (fragmentName.equals(AnimationPreferenceFragment.class.getName()))
            return true;
        return false;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        getActionBar().setDisplayHomeAsUpEnabled(true);
    }
}
