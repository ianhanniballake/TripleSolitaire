package com.github.triplesolitaire;

import android.content.Context;
import android.widget.ImageView;

/**
 * Image View specifically for Cards. Auto sets the width and height to the
 * appropriate values
 */
public class Card extends ImageView
{
	/**
	 * Constructs a new Card with the given background image
	 * 
	 * @param context
	 *            context used to create the ImageView
	 * @param resid
	 *            image to set as the background
	 */
	public Card(final Context context, final int resid)
	{
		super(context);
		setBackgroundResource(resid);
	}

	/**
	 * Sets the width and height to the appropriate values for a single card
	 * 
	 * @see android.widget.ImageView#onMeasure(int, int)
	 */
	@Override
	protected void onMeasure(final int widthMeasureSpec,
			final int heightMeasureSpec)
	{
		setMeasuredDimension(
				getResources().getDimensionPixelSize(R.dimen.card_width_dim),
				getResources().getDimensionPixelSize(R.dimen.card_height_dim));
	}
}
