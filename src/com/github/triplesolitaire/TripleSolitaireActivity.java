package com.github.triplesolitaire;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;
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
			laneLayout.flipOverTopStack(card);
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
		final ImageView stockView = (ImageView) findViewById(R.id.stock);
		stockView.setOnClickListener(new OnClickListener()
		{
			@Override
			public void onClick(final View v)
			{
				if (stock.isEmpty() && waste.isEmpty())
					return;
				if (stock.isEmpty())
				{
					stock.addAll(waste);
					waste.clear();
				}
				else
					for (int h = 0; h < 3 && !stock.isEmpty(); h++)
						waste.add(0, stock.pop());
				updateStockUI();
				updateWasteUI();
			}
		});
		for (int curLane = 0; curLane < 13; curLane++)
		{
			final int laneId = getResources().getIdentifier(
					"lane" + (curLane + 1), "id", getPackageName());
			final Lane laneLayout = (Lane) findViewById(laneId);
			laneLayout.setOnCardFlipListener(new OnCardFlipListener(curLane));
			laneLayout.setLaneId(curLane);
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
		updateStockUI();
		updateWasteUI();
		updateFoundationUI();
		updateLaneUI();
	}

	private void startGame()
	{
		Log.d(TAG, "Starting new game...");
		final ArrayList<String> fullDeck = new ArrayList<String>();
		fullDeck.add("clubs1");
		fullDeck.add("clubs2");
		fullDeck.add("clubs3");
		fullDeck.add("clubs4");
		fullDeck.add("clubs5");
		fullDeck.add("clubs6");
		fullDeck.add("clubs7");
		fullDeck.add("clubs8");
		fullDeck.add("clubs9");
		fullDeck.add("clubs10");
		fullDeck.add("clubs11");
		fullDeck.add("clubs12");
		fullDeck.add("clubs13");
		fullDeck.add("diamonds1");
		fullDeck.add("diamonds2");
		fullDeck.add("diamonds3");
		fullDeck.add("diamonds4");
		fullDeck.add("diamonds5");
		fullDeck.add("diamonds6");
		fullDeck.add("diamonds7");
		fullDeck.add("diamonds8");
		fullDeck.add("diamonds9");
		fullDeck.add("diamonds10");
		fullDeck.add("diamonds11");
		fullDeck.add("diamonds12");
		fullDeck.add("diamonds13");
		fullDeck.add("hearts1");
		fullDeck.add("hearts2");
		fullDeck.add("hearts3");
		fullDeck.add("hearts4");
		fullDeck.add("hearts5");
		fullDeck.add("hearts6");
		fullDeck.add("hearts7");
		fullDeck.add("hearts8");
		fullDeck.add("hearts9");
		fullDeck.add("hearts10");
		fullDeck.add("hearts11");
		fullDeck.add("hearts12");
		fullDeck.add("hearts13");
		fullDeck.add("spades1");
		fullDeck.add("spades2");
		fullDeck.add("spades3");
		fullDeck.add("spades4");
		fullDeck.add("spades5");
		fullDeck.add("spades6");
		fullDeck.add("spades7");
		fullDeck.add("spades8");
		fullDeck.add("spades9");
		fullDeck.add("spades10");
		fullDeck.add("spades11");
		fullDeck.add("spades12");
		fullDeck.add("spades13");
		fullDeck.add("clubs1");
		fullDeck.add("clubs2");
		fullDeck.add("clubs3");
		fullDeck.add("clubs4");
		fullDeck.add("clubs5");
		fullDeck.add("clubs6");
		fullDeck.add("clubs7");
		fullDeck.add("clubs8");
		fullDeck.add("clubs9");
		fullDeck.add("clubs10");
		fullDeck.add("clubs11");
		fullDeck.add("clubs12");
		fullDeck.add("clubs13");
		fullDeck.add("diamonds1");
		fullDeck.add("diamonds2");
		fullDeck.add("diamonds3");
		fullDeck.add("diamonds4");
		fullDeck.add("diamonds5");
		fullDeck.add("diamonds6");
		fullDeck.add("diamonds7");
		fullDeck.add("diamonds8");
		fullDeck.add("diamonds9");
		fullDeck.add("diamonds10");
		fullDeck.add("diamonds11");
		fullDeck.add("diamonds12");
		fullDeck.add("diamonds13");
		fullDeck.add("hearts1");
		fullDeck.add("hearts2");
		fullDeck.add("hearts3");
		fullDeck.add("hearts4");
		fullDeck.add("hearts5");
		fullDeck.add("hearts6");
		fullDeck.add("hearts7");
		fullDeck.add("hearts8");
		fullDeck.add("hearts9");
		fullDeck.add("hearts10");
		fullDeck.add("hearts11");
		fullDeck.add("hearts12");
		fullDeck.add("hearts13");
		fullDeck.add("spades1");
		fullDeck.add("spades2");
		fullDeck.add("spades3");
		fullDeck.add("spades4");
		fullDeck.add("spades5");
		fullDeck.add("spades6");
		fullDeck.add("spades7");
		fullDeck.add("spades8");
		fullDeck.add("spades9");
		fullDeck.add("spades10");
		fullDeck.add("spades11");
		fullDeck.add("spades12");
		fullDeck.add("spades13");
		fullDeck.add("clubs1");
		fullDeck.add("clubs2");
		fullDeck.add("clubs3");
		fullDeck.add("clubs4");
		fullDeck.add("clubs5");
		fullDeck.add("clubs6");
		fullDeck.add("clubs7");
		fullDeck.add("clubs8");
		fullDeck.add("clubs9");
		fullDeck.add("clubs10");
		fullDeck.add("clubs11");
		fullDeck.add("clubs12");
		fullDeck.add("clubs13");
		fullDeck.add("diamonds1");
		fullDeck.add("diamonds2");
		fullDeck.add("diamonds3");
		fullDeck.add("diamonds4");
		fullDeck.add("diamonds5");
		fullDeck.add("diamonds6");
		fullDeck.add("diamonds7");
		fullDeck.add("diamonds8");
		fullDeck.add("diamonds9");
		fullDeck.add("diamonds10");
		fullDeck.add("diamonds11");
		fullDeck.add("diamonds12");
		fullDeck.add("diamonds13");
		fullDeck.add("hearts1");
		fullDeck.add("hearts2");
		fullDeck.add("hearts3");
		fullDeck.add("hearts4");
		fullDeck.add("hearts5");
		fullDeck.add("hearts6");
		fullDeck.add("hearts7");
		fullDeck.add("hearts8");
		fullDeck.add("hearts9");
		fullDeck.add("hearts10");
		fullDeck.add("hearts11");
		fullDeck.add("hearts12");
		fullDeck.add("hearts13");
		fullDeck.add("spades1");
		fullDeck.add("spades2");
		fullDeck.add("spades3");
		fullDeck.add("spades4");
		fullDeck.add("spades5");
		fullDeck.add("spades6");
		fullDeck.add("spades7");
		fullDeck.add("spades8");
		fullDeck.add("spades9");
		fullDeck.add("spades10");
		fullDeck.add("spades11");
		fullDeck.add("spades12");
		fullDeck.add("spades13");
		final Random random = new Random(0);
		Collections.shuffle(fullDeck, random);
		int currentIndex = 0;
		stock = new Stack<String>();
		for (int h = 0; h < 62; h++)
			stock.push(fullDeck.get(currentIndex++));
		waste = new ArrayList<String>();
		for (int h = 0; h < 3; h++)
			waste.add(fullDeck.get(currentIndex++));
		foundation = new String[12];
		lane = new LaneData[13];
		for (int h = 0; h < 13; h++)
		{
			lane[h] = new LaneData();
			for (int i = 0; i < h; i++)
				lane[h].getStack().push(fullDeck.get(currentIndex++));
			lane[h].getCascade().add(fullDeck.get(currentIndex++));
		}
		restoreUI();
	}

	public void updateFoundationUI()
	{
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
	}

	public void updateLaneUI()
	{
		for (int curLane = 0; curLane < 13; curLane++)
		{
			final int laneId = getResources().getIdentifier(
					"lane" + (curLane + 1), "id", getPackageName());
			final Lane laneLayout = (Lane) findViewById(laneId);
			laneLayout.restoreUI(lane[curLane]);
		}
	}

	public void updateStockUI()
	{
		final ImageView stockView = (ImageView) findViewById(R.id.stock);
		if (stock.isEmpty())
			stockView.setBackgroundResource(R.drawable.lane);
		else
			stockView.setBackgroundResource(R.drawable.back);
	}

	public void updateWasteUI()
	{
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
	}
}