package com.github.triplesolitaire;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Random;
import java.util.Stack;
import java.util.StringTokenizer;

import android.os.Bundle;
import android.util.Log;

public class GameState
{
	/**
	 * Logging tag
	 */
	private static final String TAG = "TripleSolitaireActivity";
	private final TripleSolitaireActivity activity;
	private String[] foundation;
	private long gameId = 0;
	private LaneData[] lane;
	private int moveCount = 0;
	private Stack<String> stock;
	private int timeInSeconds = 0;
	private LinkedList<String> waste;

	public GameState(final TripleSolitaireActivity activity)
	{
		this.activity = activity;
	}

	public boolean acceptCascadeDrop(final int laneIndex,
			final String topNewCard)
	{
		final String cascadeCard = lane[laneIndex].getCascade().getLast();
		final String cascadeSuit = getSuit(cascadeCard);
		final int cascadeNum = getNumber(cascadeCard);
		final String topNewCardSuit = getSuit(topNewCard);
		final int topNewCardNum = getNumber(topNewCard);
		boolean acceptDrop = false;
		if (topNewCardNum != cascadeNum - 1)
			acceptDrop = false;
		else if (cascadeSuit.equals("clubs")
				&& topNewCardSuit.equals("diamonds"))
			acceptDrop = true;
		else if (cascadeSuit.equals("clubs") && topNewCardSuit.equals("hearts"))
			acceptDrop = true;
		else if (cascadeSuit.equals("clubs") && topNewCardSuit.equals("spades"))
			acceptDrop = false;
		else if (cascadeSuit.equals("diamonds")
				&& topNewCardSuit.equals("clubs"))
			acceptDrop = true;
		else if (cascadeSuit.equals("diamonds")
				&& topNewCardSuit.equals("hearts"))
			acceptDrop = false;
		else if (cascadeSuit.equals("diamonds")
				&& topNewCardSuit.equals("spades"))
			acceptDrop = true;
		else if (cascadeSuit.equals("hearts") && topNewCardSuit.equals("clubs"))
			acceptDrop = true;
		else if (cascadeSuit.equals("hearts")
				&& topNewCardSuit.equals("diamonds"))
			acceptDrop = false;
		else if (cascadeSuit.equals("hearts")
				&& topNewCardSuit.equals("spades"))
			acceptDrop = true;
		else if (cascadeSuit.equals("spades") && topNewCardSuit.equals("clubs"))
			acceptDrop = false;
		else if (cascadeSuit.equals("spades")
				&& topNewCardSuit.equals("diamonds"))
			acceptDrop = true;
		else if (cascadeSuit.equals("spades")
				&& topNewCardSuit.equals("hearts"))
			acceptDrop = true;
		else
			// same suit
			acceptDrop = false;
		if (acceptDrop)
			Log.d(TAG, "Drag -> " + (laneIndex + 1) + ": Acceptable drag of "
					+ topNewCard + " onto " + cascadeCard);
		return acceptDrop;
	}

	public boolean acceptFoundationDrop(final int foundationIndex,
			final String newCard)
	{
		if (newCard.startsWith("MULTI"))
			// Foundations don't accept multiple cards
			return false;
		final String existingFoundationCard = foundation[foundationIndex];
		boolean acceptDrop = false;
		if (existingFoundationCard == null)
			acceptDrop = newCard.endsWith("s1");
		else
			acceptDrop = newCard.equals(nextInSuit(existingFoundationCard));
		if (acceptDrop)
		{
			final String foundationDisplayCard = existingFoundationCard == null ? "empty foundation"
					: existingFoundationCard;
			Log.d(TAG, "Drag -> " + -1 * (foundationIndex + 1)
					+ ": Acceptable drag of " + newCard + " onto "
					+ foundationDisplayCard);
		}
		return acceptDrop;
	}

	public boolean acceptLaneDrop(final int laneIndex, final String topNewCard)
	{
		final boolean acceptDrop = topNewCard.endsWith("s13");
		if (acceptDrop)
			Log.d(TAG, "Drag -> " + (laneIndex + 1) + ": Acceptable drag of "
					+ topNewCard + " onto empty lane");
		return acceptDrop;
	}

	public void attemptAutoMoveFromCascadeToFoundation(final int laneIndex)
	{
		final String card = lane[laneIndex].getCascade().getLast();
		Log.d(TAG, "Auto Move Attempt " + (laneIndex + 1) + ": " + card);
		for (int foundationIndex = 0; foundationIndex < 12; foundationIndex++)
			if (acceptFoundationDrop(foundationIndex, card))
			{
				Log.d(TAG, "Auto Move " + (laneIndex + 1) + " -> " + -1
						* (foundationIndex + 1) + ": " + card);
				dropFromCascadeToFoundation(foundationIndex, laneIndex);
				return;
			}
	}

	public void attemptAutoMoveFromWasteToFoundation()
	{
		final String card = waste.getFirst();
		Log.d(TAG, "Auto Move Attempt W: " + card);
		for (int foundationIndex = 0; foundationIndex < 12; foundationIndex++)
			if (acceptFoundationDrop(foundationIndex, card))
			{
				Log.d(TAG, "Auto Move W -> " + -1 * (foundationIndex + 1)
						+ ": " + card);
				dropFromWasteToFoundation(foundationIndex);
				return;
			}
	}

	public String buildCascadeString(final int laneIndex,
			final int numCardsToInclude)
	{
		final LinkedList<String> cascade = lane[laneIndex].getCascade();
		final StringBuilder cascadeData = new StringBuilder(cascade.get(cascade
				.size() - numCardsToInclude));
		for (int cascadeIndex = cascade.size() - numCardsToInclude + 1; cascadeIndex < cascade
				.size(); cascadeIndex++)
		{
			cascadeData.append(";");
			cascadeData.append(cascade.get(cascadeIndex));
		}
		return cascadeData.toString();
	}

	private void checkForWin()
	{
		for (int foundationIndex = 0; foundationIndex < 12; foundationIndex++)
			if (!foundation[foundationIndex].endsWith("s13"))
				return;
		Log.d(TAG, "Game win detected");
		activity.showDialog(TripleSolitaireActivity.DIALOG_ID_WINNING);
	}

	public void clickStock()
	{
		if (stock.isEmpty() && waste.isEmpty())
			return;
		Log.d(TAG, "Clicked Stock");
		if (stock.isEmpty())
		{
			stock.addAll(waste);
			waste.clear();
		}
		else
			for (int wasteIndex = 0; wasteIndex < 3 && !stock.isEmpty(); wasteIndex++)
				waste.addFirst(stock.pop());
		activity.updateStockUI();
		activity.updateWasteUI();
		activity.updateMoveCount(++moveCount);
	}

	public void dropFromCascadeToCascade(final int laneIndex, final int from,
			final String card)
	{
		Log.d(TAG, "Drop " + (from + 1) + " -> " + (laneIndex + 1) + ": "
				+ card);
		final ArrayList<String> cascadeToAdd = new ArrayList<String>();
		final StringTokenizer st = new StringTokenizer(card, ";");
		while (st.hasMoreTokens())
			cascadeToAdd.add(st.nextToken());
		for (int cascadeIndex = 0; cascadeIndex < cascadeToAdd.size(); cascadeIndex++)
			lane[from].getCascade().removeLast();
		final Lane fromLaneLayout = activity.getLane(from);
		fromLaneLayout.decrementCascadeSize(cascadeToAdd.size());
		lane[laneIndex].getCascade().addAll(cascadeToAdd);
		final Lane laneLayout = activity.getLane(laneIndex);
		laneLayout.addCascade(cascadeToAdd);
		activity.updateMoveCount(++moveCount);
	}

	public void dropFromCascadeToFoundation(final int foundationIndex,
			final int from)
	{
		final String cascadeCard = lane[from].getCascade().removeLast();
		Log.d(TAG, "Drop " + (from + 1) + " -> " + -1 * (foundationIndex + 1)
				+ ": " + cascadeCard);
		foundation[foundationIndex] = cascadeCard;
		activity.updateFoundationUI(foundationIndex);
		activity.getLane(from).decrementCascadeSize(1);
		activity.updateMoveCount(++moveCount);
		checkForWin();
	}

	public void dropFromFoundationToCascade(final int laneIndex,
			final int foundationIndex)
	{
		final String foundationCard = foundation[foundationIndex];
		Log.d(TAG, "Drop " + -1 * (foundationIndex + 1) + " -> "
				+ (laneIndex + 1) + ": " + foundationCard);
		foundation[foundationIndex] = prevInSuit(foundationCard);
		activity.updateFoundationUI(foundationIndex);
		lane[laneIndex].getCascade().add(foundationCard);
		final Lane laneLayout = activity.getLane(laneIndex);
		final ArrayList<String> cascadeToAdd = new ArrayList<String>();
		cascadeToAdd.add(foundationCard);
		laneLayout.addCascade(cascadeToAdd);
	}

	public void dropFromFoundationToFoundation(final int foundationIndex,
			final int from)
	{
		final String foundationCard = foundation[from];
		Log.d(TAG, "Drop " + -1 * (from + 1) + " -> " + -1
				* (foundationIndex + 1) + ": " + foundationCard);
		foundation[foundationIndex] = foundationCard;
		foundation[from] = prevInSuit(foundationCard);
		activity.updateFoundationUI(foundationIndex);
		activity.updateFoundationUI(from);
	}

	public void dropFromWasteToCascade(final int laneIndex)
	{
		final String card = waste.removeFirst();
		Log.d(TAG, "Drop " + "W -> " + (laneIndex + 1) + ": " + card);
		activity.updateWasteUI();
		lane[laneIndex].getCascade().add(card);
		final Lane laneLayout = activity.getLane(laneIndex);
		final ArrayList<String> cascadeToAdd = new ArrayList<String>();
		cascadeToAdd.add(card);
		laneLayout.addCascade(cascadeToAdd);
	}

	public void dropFromWasteToFoundation(final int foundationIndex)
	{
		final String card = waste.removeFirst();
		Log.d(TAG, "Drop " + "W -> " + -1 * (foundationIndex + 1) + ": " + card);
		foundation[foundationIndex] = card;
		activity.updateFoundationUI(foundationIndex);
		activity.updateWasteUI();
		checkForWin();
	}

	public void flipCard(final int laneIndex)
	{
		final String card = lane[laneIndex].getStack().pop();
		Log.d(TAG, "Flip " + (laneIndex + 1) + ": " + card);
		lane[laneIndex].getCascade().add(card);
		activity.getLane(laneIndex).flipOverTopStack(card);
	}

	public String getCascadeTop(final int laneIndex)
	{
		if (lane[laneIndex].getCascade().isEmpty())
			return null;
		return lane[laneIndex].getCascade().getLast();
	}

	public String getFoundationCard(final int foundationIndex)
	{
		return foundation[foundationIndex];
	}

	public long getGameId()
	{
		return gameId;
	}

	public int getNumber(final String card)
	{
		int firstNumber;
		for (firstNumber = 0; firstNumber < card.length(); firstNumber++)
			if (Character.isDigit(card.charAt(firstNumber)))
				break;
		return Integer.parseInt(card.substring(firstNumber));
	}

	public String getSuit(final String card)
	{
		int firstNumber;
		for (firstNumber = 0; firstNumber < card.length(); firstNumber++)
			if (Character.isDigit(card.charAt(firstNumber)))
				break;
		return card.substring(0, firstNumber);
	}

	public String getWasteCard(final int wasteIndex)
	{
		if (wasteIndex < waste.size())
			return waste.get(wasteIndex);
		return null;
	}

	public String getWasteTop()
	{
		return waste.get(0);
	}

	public boolean isStockEmpty()
	{
		return stock.isEmpty();
	}

	public boolean isWasteEmpty()
	{
		return waste.isEmpty();
	}

	public void newGame()
	{
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
		final Random random = new Random();
		gameId = random.nextLong();
		random.setSeed(gameId);
		timeInSeconds = 0;
		activity.updateTime(timeInSeconds);
		moveCount = 0;
		activity.updateMoveCount(moveCount);
		Collections.shuffle(fullDeck, random);
		int currentIndex = 0;
		stock = new Stack<String>();
		for (int stockIndex = 0; stockIndex < 65; stockIndex++)
			stock.push(fullDeck.get(currentIndex++));
		activity.updateStockUI();
		waste = new LinkedList<String>();
		activity.updateWasteUI();
		foundation = new String[12];
		for (int foundationIndex = 0; foundationIndex < 12; foundationIndex++)
			activity.updateFoundationUI(foundationIndex);
		lane = new LaneData[13];
		for (int laneIndex = 0; laneIndex < 13; laneIndex++)
		{
			lane[laneIndex] = new LaneData();
			for (int i = 0; i < laneIndex; i++)
				lane[laneIndex].getStack().push(fullDeck.get(currentIndex++));
			lane[laneIndex].getCascade().add(fullDeck.get(currentIndex++));
			final Lane laneLayout = activity.getLane(laneIndex);
			laneLayout.setStackSize(lane[laneIndex].getStack().size());
			laneLayout.addCascade(lane[laneIndex].getCascade());
		}
		Log.d(TAG, "Game Started: " + gameId);
	}

	public String nextInSuit(final String card)
	{
		return getSuit(card) + (getNumber(card) + 1);
	}

	public void onRestoreInstanceState(final Bundle savedInstanceState)
	{
		// Restore the current game information
		gameId = savedInstanceState.getLong("gameId");
		timeInSeconds = savedInstanceState.getInt("timeInSeconds");
		activity.updateTime(timeInSeconds);
		moveCount = savedInstanceState.getInt("moveCount");
		activity.updateMoveCount(moveCount);
		// Restore the stack
		final ArrayList<String> arrayCardStock = savedInstanceState
				.getStringArrayList("stock");
		stock = new Stack<String>();
		for (final String card : arrayCardStock)
			stock.push(card);
		activity.updateStockUI();
		// Restore the waste data
		waste = new LinkedList<String>(
				savedInstanceState.getStringArrayList("waste"));
		activity.updateWasteUI();
		// Restore the foundation data
		foundation = savedInstanceState.getStringArray("foundation");
		for (int foundationIndex = 0; foundationIndex < 12; foundationIndex++)
			activity.updateFoundationUI(foundationIndex);
		lane = new LaneData[13];
		for (int laneIndex = 0; laneIndex < 13; laneIndex++)
		{
			lane[laneIndex] = new LaneData(
					savedInstanceState.getStringArrayList("laneStack"
							+ laneIndex),
					savedInstanceState.getStringArrayList("laneCascade"
							+ laneIndex));
			final Lane laneLayout = activity.getLane(laneIndex);
			laneLayout.setStackSize(lane[laneIndex].getStack().size());
			laneLayout.addCascade(lane[laneIndex].getCascade());
		}
	}

	public void onSaveInstanceState(final Bundle outState)
	{
		outState.putLong("gameId", gameId);
		outState.putInt("timeInSeconds", timeInSeconds);
		outState.putInt("moveCount", moveCount);
		outState.putStringArrayList("stock", new ArrayList<String>(stock));
		outState.putStringArrayList("waste", new ArrayList<String>(waste));
		outState.putStringArray("foundation", foundation);
		for (int laneIndex = 0; laneIndex < 13; laneIndex++)
		{
			outState.putStringArrayList("laneStack" + laneIndex,
					new ArrayList<String>(lane[laneIndex].getStack()));
			outState.putStringArrayList("laneCascade" + laneIndex,
					new ArrayList<String>(lane[laneIndex].getCascade()));
		}
	}

	public String prevInSuit(final String card)
	{
		if (card.endsWith("s1"))
			return null;
		return getSuit(card) + (getNumber(card) - 1);
	}
}
