package com.github.triplesolitaire;

import java.util.ArrayList;
import java.util.Stack;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;

public class TripleSolitaireActivity extends Activity
{
	private class OnCardFlipListener implements OnClickListener
	{
		private final int laneIndex;

		public OnCardFlipListener(final int laneIndex)
		{
			this.laneIndex = laneIndex;
		}

		@Override
		public void onClick(final View v)
		{
			Log.d(TAG, "Clicked " + laneIndex);
			final String card = lane[laneIndex].getStack().pop();
			lane[laneIndex].getCascade().add(card);
			final int laneId = getResources().getIdentifier(
					"lane" + (laneIndex + 1), "id", getPackageName());
			final Lane laneLayout = (Lane) findViewById(laneId);
			laneLayout.restoreUI(lane[laneIndex]);
		}
	}

	/**
	 * Logging tag
	 */
	private static final String TAG = "TripleSolitaireActivity";
	private String foundation[];
	private LaneData lane[];
	private Stack<String> stock;
	private ArrayList<String> waste;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		for (int curLane = 0; curLane < 13; curLane++)
		{
			final int laneId = getResources().getIdentifier(
					"lane" + (curLane + 1), "id", getPackageName());
			final Lane laneLayout = (Lane) findViewById(laneId);
			laneLayout.setOnCardFlipListener(new OnCardFlipListener(curLane));
		}
		if (savedInstanceState == null)
			startGame();
	}

	@Override
	public void onRestoreInstanceState(final Bundle savedInstanceState)
	{
		super.onRestoreInstanceState(savedInstanceState);
		// Restore the stack
		final ArrayList<String> arrayCardStock = savedInstanceState
				.getStringArrayList("stock");
		stock = new Stack<String>();
		for (final String card : arrayCardStock)
			stock.push(card);
		// Restore the waste data
		waste = savedInstanceState.getStringArrayList("waste");
		// Restore the foundation data
		foundation = savedInstanceState.getStringArray("foundation");
		lane = new LaneData[13];
		for (int h = 0; h < 13; h++)
			lane[h] = new LaneData(
					savedInstanceState.getStringArrayList("laneStack" + h),
					savedInstanceState.getStringArrayList("laneCascade" + h));
		restoreUI();
	}

	@Override
	public void onSaveInstanceState(final Bundle outState)
	{
		super.onSaveInstanceState(outState);
		outState.putStringArrayList("stock", new ArrayList<String>(stock));
		outState.putStringArrayList("waste", waste);
		outState.putStringArray("foundation", foundation);
		for (int h = 0; h < 13; h++)
		{
			outState.putStringArrayList("laneStack" + h, new ArrayList<String>(
					lane[h].getStack()));
			outState.putStringArrayList("laneCascade" + h, lane[h].getCascade());
		}
	}

	public void restoreUI()
	{
		// Restore stock UI
		final ImageView stockView = (ImageView) findViewById(R.id.stock);
		if (stock.isEmpty())
			stockView.setBackgroundResource(R.drawable.lane);
		else
			stockView.setBackgroundResource(R.drawable.back);
		// Restore waste UI
		final ImageView waste1 = (ImageView) findViewById(R.id.waste1);
		if (waste.size() > 2)
			waste1.setBackgroundResource(getResources().getIdentifier(
					waste.get(2), "drawable", getPackageName()));
		else
			waste1.setBackgroundResource(0);
		final ImageView waste2 = (ImageView) findViewById(R.id.waste2);
		if (waste.size() > 1)
			waste2.setBackgroundResource(getResources().getIdentifier(
					waste.get(1), "drawable", getPackageName()));
		else
			waste2.setBackgroundResource(0);
		final ImageView waste3 = (ImageView) findViewById(R.id.waste3);
		if (waste.size() > 0)
			waste3.setBackgroundResource(getResources().getIdentifier(
					waste.get(0), "drawable", getPackageName()));
		else
			waste3.setBackgroundResource(0);
		// Restore foundation UI
		for (int h = 0; h < 12; h++)
		{
			final int foundationViewId = getResources().getIdentifier(
					"foundation" + (h + 1), "id", getPackageName());
			final ImageView foundationView = (ImageView) findViewById(foundationViewId);
			if (foundation[h] == null)
				foundationView.setBackgroundResource(R.drawable.foundation);
			else
				foundationView.setBackgroundResource(getResources()
						.getIdentifier(foundation[h], "drawable",
								getPackageName()));
		}
		// Restore lane UI
		for (int curLane = 0; curLane < 13; curLane++)
		{
			final int laneId = getResources().getIdentifier(
					"lane" + (curLane + 1), "id", getPackageName());
			final Lane laneLayout = (Lane) findViewById(laneId);
			laneLayout.restoreUI(lane[curLane]);
		}
	}

	private void startGame()
	{
		Log.d(TAG, "Starting new game...");
		stock = new Stack<String>();
		stock.push("clubs10");
		stock.push("clubs5");
		waste = new ArrayList<String>();
		waste.add("spades13");
		waste.add("clubs3");
		waste.add("diamonds1");
		foundation = new String[12];
		foundation[3] = "clubs6";
		lane = new LaneData[13];
		for (int h = 0; h < 13; h++)
			lane[h] = new LaneData();
		lane[1].getStack().add("diamonds2");
		lane[1].getCascade().add("clubs11");
		lane[2].getStack().add("diamonds2");
		lane[2].getStack().add("diamonds2");
		lane[2].getCascade().add("diamonds3");
		lane[2].getCascade().add("clubs2");
		lane[3].getStack().add("diamonds2");
		lane[4].getStack().add("diamonds2");
		lane[5].getCascade().add("diamonds2");
		lane[6].getStack().add("diamonds2");
		lane[7].getStack().add("diamonds2");
		lane[7].getStack().add("diamonds2");
		lane[8].getStack().add("diamonds2");
		lane[9].getStack().add("diamonds2");
		lane[10].getStack().add("diamonds2");
		lane[11].getStack().add("diamonds2");
		lane[12].getStack().add("diamonds2");
		restoreUI();
	}
}