package com.github.triplesolitaire.ui;

import android.app.Activity;
import android.os.Bundle;

import com.github.triplesolitaire.R;

public class TripleSolitaireActivity extends Activity
{
	/** Called when the activity is first created. */
	@Override
	public void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
	}
}