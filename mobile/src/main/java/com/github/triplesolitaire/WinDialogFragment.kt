package com.github.triplesolitaire;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;

/**
 * Dialog to show when a user wins the game
 */
public class WinDialogFragment extends DialogFragment {
    private static final String GAME_WIN_TIME = "GAME_WIN_TIME";
    private static final String GAME_WIN_MOVE_COUNT = "GAME_WIN_MOVE_COUNT";

    /**
     * Create a new Intent which can be passed via setResult(int requestCode, Intent data) to eventually create a
     * WinDialogFragment
     *
     * @param time      time of the winning game
     * @param moveCount move count of the winning game
     * @return Intent suitable for setResult(int requestCode, Intent data)
     */
    public static Intent createDataIntent(CharSequence time, int moveCount) {
        Intent data = new Intent();
        data.putExtra(GAME_WIN_TIME, time);
        data.putExtra(GAME_WIN_MOVE_COUNT, moveCount);
        return data;
    }

    /**
     * Create a new instance of WinDialogFragment given the Intent created by createDataIntent
     *
     * @param data Intent created by createDataIntent()
     * @return A valid instance of WinDialogFragment
     */
    public static WinDialogFragment createInstance(final Intent data) {
        WinDialogFragment winDialogFragment = new WinDialogFragment();
        Bundle args = new Bundle();
        args.putCharSequence(GAME_WIN_TIME, data.getCharSequenceExtra(GAME_WIN_TIME));
        args.putInt(GAME_WIN_MOVE_COUNT, data.getIntExtra(GAME_WIN_MOVE_COUNT, 0));
        winDialogFragment.setArguments(args);
        return winDialogFragment;
    }

    @Override
    public Dialog onCreateDialog(final Bundle savedInstanceState) {
        final TripleSolitaireActivity activity = (TripleSolitaireActivity) getActivity();
        final CharSequence time = getArguments().getCharSequence(GAME_WIN_TIME);
        final int moveCount = getArguments().getInt(GAME_WIN_MOVE_COUNT);
        final String message = getString(R.string.win_dialog, time, moveCount);
        final AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setMessage(message).setCancelable(false)
                .setPositiveButton(getString(R.string.new_game), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(final DialogInterface dialog, final int dialogId) {
                        dialog.dismiss();
                        activity.newGame();
                    }
                }).setNegativeButton(getString(R.string.main_menu), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(final DialogInterface dialog, final int dialogId) {
                dialog.dismiss();
            }
        });
        return builder.create();
    }
}
