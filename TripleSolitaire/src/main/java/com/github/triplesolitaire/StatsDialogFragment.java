package com.github.triplesolitaire;

import java.text.NumberFormat;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

/**
 * Stats Dialog for the application
 */
public class StatsDialogFragment extends DialogFragment
{
	private static final String STATS_STATE = "STATS_STATE";

	/**
	 * Create a new StatsDialogFragment with the given StatsState
	 * 
	 * @param stats
	 *            Stats to display
	 * @return A valid StatsDialogFragment
	 */
	public static StatsDialogFragment createInstance(final StatsState stats)
	{
		final StatsDialogFragment statsDialogFragment = new StatsDialogFragment();
		final Bundle args = new Bundle();
		args.putString(STATS_STATE, stats.toString());
		statsDialogFragment.setArguments(args);
		return statsDialogFragment;
	}

	@Override
	public Dialog onCreateDialog(final Bundle savedInstanceState)
	{
		final StatsState stats = new StatsState(getArguments().getString(STATS_STATE));
		final int gamesPlayed = stats.getGamesPlayed();
		final int gamesWon = stats.getGamesWon();
		final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		final LayoutInflater inflater = getActivity().getLayoutInflater();
		final View layout = inflater.inflate(R.layout.stats_dialog, null);
		final TextView gamesPlayedView = (TextView) layout.findViewById(R.id.games_played);
		gamesPlayedView.setText(Integer.toString(gamesPlayed));
		final TextView gamesWonView = (TextView) layout.findViewById(R.id.games_won);
		gamesWonView.setText(Integer.toString(gamesWon));
		final TextView winPercentageView = (TextView) layout.findViewById(R.id.win_percentage);
		if (gamesPlayed == 0)
			winPercentageView.setText(R.string.stats_na);
		else
		{
			final NumberFormat percentFormat = NumberFormat.getPercentInstance();
			percentFormat.setMaximumFractionDigits(1);
			winPercentageView.setText(percentFormat.format((double) gamesWon / gamesPlayed));
		}
		final TextView averageDurationView = (TextView) layout.findViewById(R.id.average_duration);
		if (gamesWon == 0)
			averageDurationView.setText(getText(R.string.stats_na));
		else
		{
			final double averageDuration = stats.getAverageDuration();
			final int minutes = (int) (averageDuration / 60);
			final int seconds = (int) (averageDuration % 60);
			final StringBuilder sb = new StringBuilder();
			sb.append(minutes);
			sb.append(':');
			if (seconds < 10)
				sb.append(0);
			sb.append(seconds);
			averageDurationView.setText(sb);
		}
		final NumberFormat integerFormat = NumberFormat.getIntegerInstance();
		final TextView averageMovesView = (TextView) layout.findViewById(R.id.average_moves);
		if (gamesWon == 0)
			averageMovesView.setText(getText(R.string.stats_na));
		else
		{
			final double averageMoves = stats.getAverageMoves();
			averageMovesView.setText(integerFormat.format(averageMoves));
		}
		builder.setTitle(R.string.stats).setIcon(R.drawable.icon).setView(layout)
				.setNegativeButton(getText(R.string.close), null);
		return builder.create();
	}
}
