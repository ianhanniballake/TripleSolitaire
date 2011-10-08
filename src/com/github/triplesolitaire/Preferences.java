package com.github.triplesolitaire;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.PreferenceActivity;

/**
 * Activity managing the various application preferences
 */
public class Preferences extends PreferenceActivity implements
		OnSharedPreferenceChangeListener
{
	/**
	 * Auto Flip preference name
	 */
	public static final String AUTO_FLIP_PREFERENCE_KEY = "auto_flip";
	/**
	 * Auto Play preference name
	 */
	public static final String AUTO_PLAY_PREFERENCE_KEY = "auto_play";
	/**
	 * Reference to the ListPreference corresponding with the auto play mode
	 */
	private ListPreference autoplayListPreference;

	@Override
	protected void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.preferences);
		autoplayListPreference = (ListPreference) getPreferenceScreen()
				.findPreference(AUTO_PLAY_PREFERENCE_KEY);
		autoplayListPreference.setSummary(autoplayListPreference.getEntry());
	}

	@Override
	protected void onPause()
	{
		super.onPause();
		getPreferenceScreen().getSharedPreferences()
				.unregisterOnSharedPreferenceChangeListener(this);
	}

	@Override
	protected void onResume()
	{
		super.onResume();
		getPreferenceScreen().getSharedPreferences()
				.registerOnSharedPreferenceChangeListener(this);
	}

	@Override
	public void onSharedPreferenceChanged(
			final SharedPreferences sharedPreferences, final String key)
	{
		if (key.equals(AUTO_PLAY_PREFERENCE_KEY))
			autoplayListPreference
					.setSummary(autoplayListPreference.getEntry());
	}
}
