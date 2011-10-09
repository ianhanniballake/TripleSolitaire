package com.github.triplesolitaire;

import java.text.NumberFormat;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.LoaderManager;
import android.content.CursorLoader;
import android.content.Loader;
import android.database.Cursor;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import com.github.triplesolitaire.provider.GameContract;

/**
 * Stats Dialog for the application
 */
public class StatsDialogFragment extends DialogFragment implements
		LoaderManager.LoaderCallbacks<Cursor>
{
	@Override
	public void onActivityCreated(final Bundle savedInstanceState)
	{
		super.onActivityCreated(savedInstanceState);
		getLoaderManager().initLoader(0, null, this);
	}

	@Override
	public Dialog onCreateDialog(final Bundle savedInstanceState)
	{
		final AlertDialog.Builder builder = new AlertDialog.Builder(
				getActivity());
		final LayoutInflater inflater = getActivity().getLayoutInflater();
		final View layout = inflater.inflate(R.layout.stats_dialog, null);
		builder.setTitle(R.string.stats).setIcon(R.drawable.icon)
				.setView(layout)
				.setNegativeButton(getText(R.string.close), null);
		return builder.create();
	}

	@Override
	public Loader<Cursor> onCreateLoader(final int id, final Bundle args)
	{
		return new CursorLoader(getActivity(), GameContract.Games.CONTENT_URI,
				null, null, null, null);
	}

	@Override
	public void onLoaderReset(final Loader<Cursor> loader)
	{
		// Nothing to do
	}

	@Override
	public void onLoadFinished(final Loader<Cursor> loader, final Cursor data)
	{
		final int gamesDealt = data.getCount();
		int gamesWon = 0;
		double averageDuration = 0;
		double averageMoves = 0;
		final int durationColumnIndex = data
				.getColumnIndex(GameContract.Games.COLUMN_NAME_DURATION);
		final int movesColumnIndex = data
				.getColumnIndex(GameContract.Games.COLUMN_NAME_MOVES);
		while (data.moveToNext())
		{
			if (data.isNull(durationColumnIndex))
				continue;
			final int curDuration = data.getInt(durationColumnIndex);
			averageDuration = (curDuration + gamesWon * averageDuration)
					/ (gamesWon + 1);
			final int curMoves = data.getInt(movesColumnIndex);
			averageMoves = (curMoves + gamesWon * averageMoves)
					/ (gamesWon + 1);
			gamesWon++;
		}
		final TextView gamesDealtView = (TextView) getDialog().findViewById(
				R.id.games_dealt);
		gamesDealtView.setText(Integer.toString(gamesDealt));
		final TextView gamesWonView = (TextView) getDialog().findViewById(
				R.id.games_won);
		gamesWonView.setText(Integer.toString(gamesWon));
		final TextView winPercentageView = (TextView) getDialog().findViewById(
				R.id.win_percentage);
		final NumberFormat percentFormat = NumberFormat.getPercentInstance();
		percentFormat.setMaximumFractionDigits(1);
		winPercentageView.setText(percentFormat.format((double) gamesWon
				/ gamesDealt));
		final TextView averageDurationView = (TextView) getDialog()
				.findViewById(R.id.average_duration);
		final NumberFormat integerFormat = NumberFormat.getIntegerInstance();
		if (gamesWon == 0)
			averageDurationView.setText(getText(R.string.stats_na));
		else
			averageDurationView.setText(integerFormat.format(averageDuration));
		final TextView averageMovesView = (TextView) getDialog().findViewById(
				R.id.average_moves);
		if (gamesWon == 0)
			averageMovesView.setText(getText(R.string.stats_na));
		else
			averageMovesView.setText(integerFormat.format(averageMoves));
	}
}
