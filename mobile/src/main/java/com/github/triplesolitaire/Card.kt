package com.github.triplesolitaire

import android.annotation.SuppressLint
import android.content.Context
import android.widget.ImageView

/**
 * Image View specifically for Cards. Auto sets the width and height to the appropriate values
 */
@SuppressLint("ViewConstructor")
class Card(context: Context, resId: Int) : ImageView(context) {
    init {
        setBackgroundResource(resId)
    }

    /**
     * Sets the width and height to the appropriate values for a single card
     *
     * @see android.widget.ImageView.onMeasure
     */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            resources.getDimensionPixelSize(R.dimen.card_width_dim),
            resources.getDimensionPixelSize(R.dimen.card_height_dim)
        )
    }
}
