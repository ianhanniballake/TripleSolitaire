package com.github.triplesolitaire;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.os.Build;
import android.os.Bundle;

/**
 * Dialog to show when a user pauses the game
 */
public class GamePauseDialogFragment extends DialogFragment {
    @Override
    public Dialog onCreateDialog(final Bundle savedInstanceState) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            builder.setIcon(R.drawable.icon);
        }
        builder.setTitle(R.string.app_name).setMessage(R.string.game_paused)
                .setNegativeButton(getText(R.string.resume), null);
        return builder.create();
    }
}
