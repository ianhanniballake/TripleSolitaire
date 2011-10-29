package com.github.triplesolitaire;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;

/**
 * Dialog to show when a user wins the game
 */
public class WinDialogFragment extends DialogFragment
{
	/**
	 * Key for saving and restoring the cached message from the saved instance
	 * state bundle
	 */
	private final static String MESSAGE_KEY = "message";
	/**
	 * Cached message to restore on screen rotation or other configuration
	 * changes
	 */
	private String cachedMessage = null;

	@Override
	public Dialog onCreateDialog(final Bundle savedInstanceState)
	{
		final TripleSolitaireActivity activity = (TripleSolitaireActivity) getActivity();
		if (savedInstanceState != null)
			cachedMessage = savedInstanceState.getString(MESSAGE_KEY, null);
		if (cachedMessage == null)
		{
			final String messageFormat = getString(R.string.win_dialog);
			final CharSequence time = activity.getGameTime();
			final int moveCount = activity.getMoveCount();
			cachedMessage = String.format(messageFormat, time, moveCount);
		}
		final AlertDialog.Builder builder = new AlertDialog.Builder(activity);
		builder.setMessage(cachedMessage)
				.setCancelable(false)
				.setPositiveButton(getString(R.string.new_game),
						new DialogInterface.OnClickListener()
						{
							@Override
							public void onClick(final DialogInterface dialog,
									final int dialogId)
							{
								dialog.dismiss();
								activity.newGame();
							}
						})
				.setNegativeButton(getString(R.string.exit),
						new DialogInterface.OnClickListener()
						{
							@Override
							public void onClick(final DialogInterface dialog,
									final int dialogId)
							{
								activity.finish();
							}
						});
		return builder.create();
	}

	@Override
	public void onSaveInstanceState(final Bundle outState)
	{
		super.onSaveInstanceState(outState);
		outState.putString(MESSAGE_KEY, cachedMessage);
	}
}
