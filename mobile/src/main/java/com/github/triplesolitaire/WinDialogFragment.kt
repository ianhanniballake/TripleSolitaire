package com.github.triplesolitaire

import android.app.AlertDialog
import android.app.Dialog
import android.app.DialogFragment
import android.content.Intent
import android.os.Bundle
import androidx.core.os.bundleOf

/**
 * Dialog to show when a user wins the game
 */
class WinDialogFragment : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle): Dialog {
        val activity = activity as TripleSolitaireActivity
        val time = arguments.getCharSequence(GAME_WIN_TIME)
        val moveCount = arguments.getInt(GAME_WIN_MOVE_COUNT)
        val message = getString(R.string.win_dialog, time, moveCount)
        val builder = AlertDialog.Builder(activity)
        builder.setMessage(message).setCancelable(false)
            .setPositiveButton(getString(R.string.new_game)) { dialog, _ ->
                dialog.dismiss()
                activity.newGame()
            }
            .setNegativeButton(getString(R.string.main_menu)) { dialog, _ -> dialog.dismiss() }
        return builder.create()
    }

    companion object {
        private const val GAME_WIN_TIME = "GAME_WIN_TIME"
        private const val GAME_WIN_MOVE_COUNT = "GAME_WIN_MOVE_COUNT"

        /**
         * Create a new Intent which can be passed via setResult(int requestCode, Intent data)
         * to eventually create a WinDialogFragment
         *
         * @param time      time of the winning game
         * @param moveCount move count of the winning game
         * @return Intent suitable for setResult(int requestCode, Intent data)
         */
        @JvmStatic
        fun createDataIntent(time: CharSequence, moveCount: Int) = Intent().apply {
            putExtra(GAME_WIN_TIME, time)
            putExtra(GAME_WIN_MOVE_COUNT, moveCount)
        }

        /**
         * Create a new instance of WinDialogFragment given the Intent created by createDataIntent
         *
         * @param data Intent created by createDataIntent()
         * @return A valid instance of WinDialogFragment
         */
        @JvmStatic
        fun createInstance(data: Intent): WinDialogFragment {
            val winDialogFragment = WinDialogFragment()
            val args = bundleOf(
                GAME_WIN_TIME to data.getCharSequenceExtra(GAME_WIN_TIME),
                GAME_WIN_MOVE_COUNT to data.getIntExtra(GAME_WIN_MOVE_COUNT, 0)
            )
            winDialogFragment.arguments = args
            return winDialogFragment
        }
    }
}