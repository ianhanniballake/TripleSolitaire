package com.github.triplesolitaire;

import android.app.Application;
import android.content.pm.ApplicationInfo;
import android.os.StrictMode;
import android.preference.PreferenceManager;

/**
 * Creates the Triple Solitaire application, setting strict mode in debug mode
 */
public class TripleSolitaireApplication extends Application
{
	/**
	 * Sets strict mode if we are in debug mode
	 * 
	 * @see android.app.Application#onCreate()
	 */
	@Override
	public void onCreate()
	{
		final ApplicationInfo appInfo = getApplicationContext()
				.getApplicationInfo();
		if ((appInfo.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0)
			StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
					.detectDiskReads().detectDiskWrites().detectNetwork()
					.penaltyLog().penaltyFlashScreen().build());
		PreferenceManager.setDefaultValues(this, R.xml.preferences_gameplay,
				false);
		PreferenceManager.setDefaultValues(this, R.xml.preferences_animation,
				false);
	}
}
