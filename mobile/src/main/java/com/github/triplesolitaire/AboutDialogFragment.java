package com.github.triplesolitaire;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;

/**
 * About Dialog for the application
 */
public class AboutDialogFragment extends DialogFragment {
    @Override
    public Dialog onCreateDialog(final Bundle savedInstanceState) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        final LayoutInflater inflater = getActivity().getLayoutInflater();
        final View layout = inflater.inflate(R.layout.about_dialog, null);
        builder.setTitle(R.string.app_name).setIcon(R.drawable.icon).setView(layout)
                .setNegativeButton(getText(R.string.close), null);
        return builder.create();
    }
}
