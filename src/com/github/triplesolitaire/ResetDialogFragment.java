package com.github.triplesolitaire;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.AsyncQueryHandler;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;

import com.github.triplesolitaire.provider.GameContract;

/**
 * Reset Confirmation Dialog box
 */
public class ResetDialogFragment extends android.app.DialogFragment
{
	@Override
	public Dialog onCreateDialog(final Bundle savedInstanceState)
	{
		return new AlertDialog.Builder(getActivity())
				.setTitle(R.string.reset_dialog_title)
				.setMessage(R.string.reset_dialog_message)
				.setPositiveButton(R.string.reset_dialog_confirm,
						new OnClickListener()
						{
							@Override
							public void onClick(final DialogInterface dialog,
									final int which)
							{
								new AsyncQueryHandler(getActivity()
										.getContentResolver())
								{
									// No call backs needed
								}.startDelete(0, 0,
										GameContract.Games.CONTENT_URI, null,
										null);
							}
						})
				.setNegativeButton(R.string.reset_dialog_cancel, null).create();
	}
}
