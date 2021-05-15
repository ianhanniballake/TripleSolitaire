package com.github.triplesolitaire

import android.app.AlertDialog
import android.app.Dialog
import android.app.DialogFragment
import android.os.Bundle
import android.widget.TextView
import androidx.core.os.bundleOf
import java.text.NumberFormat

/**
 * Stats Dialog for the application
 */
class StatsDialogFragment : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle): Dialog {
        val stats = StatsState(arguments.getString(STATS_STATE))
        val gamesPlayed = stats.gamesPlayed
        val gamesWon = stats.getGamesWon(false)
        val builder = AlertDialog.Builder(activity)
        val inflater = activity.layoutInflater
        val layout = inflater.inflate(R.layout.stats_dialog, null)
        val gamesPlayedView = layout.findViewById<TextView>(R.id.games_played)
        gamesPlayedView.text = gamesPlayed.toString()
        val gamesWonView = layout.findViewById<TextView>(R.id.games_won)
        gamesWonView.text = gamesWon.toString()
        val winPercentageView = layout.findViewById<TextView>(R.id.win_percentage)
        winPercentageView.text = if (gamesPlayed == 0) {
            getText(R.string.stats_na)
        } else {
            val percentFormat = NumberFormat.getPercentInstance()
            percentFormat.maximumFractionDigits = 1
            percentFormat.format(gamesWon.toDouble() / gamesPlayed)
        }
        val averageDurationView = layout.findViewById<TextView>(R.id.average_duration)
        averageDurationView.text = if (gamesWon == 0) {
            getText(R.string.stats_na)
        } else {
            val averageDuration = stats.averageDuration
            val minutes = (averageDuration / 60).toInt()
            val seconds = (averageDuration % 60).toInt()
            val sb = StringBuilder()
            sb.append(minutes)
            sb.append(':')
            if (seconds < 10) sb.append(0)
            sb.append(seconds)
            sb
        }
        val integerFormat = NumberFormat.getIntegerInstance()
        val averageMovesView = layout.findViewById<TextView>(R.id.average_moves)
        averageMovesView.text = if (gamesWon == 0) {
            getText(R.string.stats_na)
        } else {
            val averageMoves = stats.averageMoves
            integerFormat.format(averageMoves)
        }
        builder.setTitle(R.string.stats).setView(layout)
            .setNegativeButton(getText(R.string.close), null)
        return builder.create()
    }

    companion object {
        private const val STATS_STATE = "STATS_STATE"

        /**
         * Create a new StatsDialogFragment with the given StatsState
         *
         * @param stats Stats to display
         * @return A valid StatsDialogFragment
         */
        @JvmStatic
        fun createInstance(stats: StatsState) = StatsDialogFragment().apply {
            arguments = bundleOf(STATS_STATE to stats.toString())
        }
    }
}
