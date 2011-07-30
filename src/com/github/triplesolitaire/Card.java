package com.github.triplesolitaire;

import android.content.Context;
import android.widget.ImageView;

public class Card extends ImageView
{
	public Card(final Context context, final int resid)
	{
		super(context);
		setBackgroundResource(resid);
	}

	@Override
	protected void onMeasure(final int widthMeasureSpec,
			final int heightMeasureSpec)
	{
		setMeasuredDimension(
				getResources().getDimensionPixelSize(R.dimen.card_width_dim),
				getResources().getDimensionPixelSize(R.dimen.card_height_dim));
	}
}
