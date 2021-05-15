package com.github.triplesolitaire

import android.app.AlertDialog
import android.app.Dialog
import android.app.DialogFragment
import android.os.Bundle

/**
 * Dialog to show when a user pauses the game
 */
class GamePauseDialogFragment : DialogFragment() {
    override fun onCreateDialog(
        savedInstanceState: Bundle
    ): Dialog = AlertDialog.Builder(activity)
        .setTitle(R.string.app_name)
        .setMessage(R.string.game_paused)
        .setNegativeButton(getText(R.string.resume), null)
        .create()
}
