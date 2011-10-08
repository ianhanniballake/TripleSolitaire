package com.github.triplesolitaire;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.widget.TextView;

/**
 * Dialog to show when a user wins the game
 */
public class WinDialogFragment extends DialogFragment
{
	/**
	 * Game State used for starting a new game
	 */
	private final GameState gameState;

	/**
	 * Creates a WinDialog from the given game state
	 * 
	 * @param gameState
	 *            Game State for starting a new game
	 */
	public WinDialogFragment(final GameState gameState)
	{
		this.gameState = gameState;
	}

	@Override
	public Dialog onCreateDialog(final Bundle savedInstanceState)
	{
		final TextView timeView = (TextView) getActivity().getActionBar()
				.getCustomView().findViewById(R.id.time);
		final CharSequence time = timeView.getText();
		final TextView moveCountView = (TextView) getActivity().getActionBar()
				.getCustomView().findViewById(R.id.move_count);
		final CharSequence moveCount = moveCountView.getText();
		final String message = getString(R.string.win_dialog_1) + " " + time
				+ " " + getString(R.string.win_dialog_2) + " " + moveCount
				+ " " + getString(R.string.win_dialog_3);
		final AlertDialog.Builder builder = new AlertDialog.Builder(
				getActivity());
		builder.setMessage(message)
				.setCancelable(false)
				.setPositiveButton(getString(R.string.new_game),
						new DialogInterface.OnClickListener()
						{
							@Override
							public void onClick(final DialogInterface dialog,
									final int dialogId)
							{
								dialog.dismiss();
								gameState.newGame();
							}
						})
				.setNegativeButton(getString(R.string.exit),
						new DialogInterface.OnClickListener()
						{
							@Override
							public void onClick(final DialogInterface dialog,
									final int dialogId)
							{
								getActivity().finish();
							}
						});
		return builder.create();
	}
}
