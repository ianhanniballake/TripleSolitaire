package com.github.triplesolitaire

import android.app.AlertDialog
import android.app.Dialog
import android.app.DialogFragment
import android.os.Bundle

/**
 * About Dialog for the application
 */
class AboutDialogFragment : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle): Dialog {
        val builder = AlertDialog.Builder(activity)
        val inflater = activity.layoutInflater
        val layout = inflater.inflate(R.layout.about_dialog, null)
        builder.setTitle(R.string.app_name).setIcon(R.mipmap.ic_launcher).setView(layout)
            .setNegativeButton(getText(R.string.close), null)
        return builder.create()
    }
}
