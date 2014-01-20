package com.github.triplesolitaire;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.os.Bundle;

/**
 * Dialog to show when a user pauses the game
 */
public class GamePauseDialogFragment extends DialogFragment {
    @Override
    public Dialog onCreateDialog(final Bundle savedInstanceState) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(R.string.app_name).setIcon(R.drawable.icon).setMessage(R.string.game_paused)
                .setNegativeButton(getText(R.string.resume), null);
        return builder.create();
    }
}
