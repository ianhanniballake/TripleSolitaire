package com.github.triplesolitaire;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Random;
import java.util.Stack;

import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import com.github.triplesolitaire.Move.Type;
import com.github.triplesolitaire.TripleSolitaireActivity.AutoPlayPreference;

public class GameState
{
	/**
	 * Logging tag
	 */
	private static final String TAG = "TripleSolitaireActivity";
	private final TripleSolitaireActivity activity;
	private boolean[] autoplayLaneIndexLocked = new boolean[13];
	private String[] foundation;
	private long gameId = 0;
	private boolean gameInProgress = false;
	private final Runnable gameTimerIncrement = new Runnable()
	{
		@Override
		public void run()
		{
			if (moveCount == 0)
				return;
			timeInSeconds++;
			activity.updateTime(timeInSeconds);
			if (gameInProgress)
				timerHandler.postDelayed(this, 1000);
		}
	};
	private LaneData[] lane;
	private int moveCount = 0;
	private Stack<Move> moves;
	private int pendingMoves = 0;
	private Stack<String> stock;
	private int timeInSeconds = 0;
	private final Handler timerHandler = new Handler();
	private LinkedList<String> waste;

	public GameState(final TripleSolitaireActivity activity)
	{
		this.activity = activity;
	}

	public boolean acceptCascadeDrop(final int laneIndex,
			final String topNewCard)
	{
		final String cascadeCard = lane[laneIndex - 1].getCascade().getLast();
		final String cascadeSuit = getSuit(cascadeCard);
		final int cascadeNum = getNumber(cascadeCard);
		final String topNewCardSuit = getSuit(topNewCard);
		final int topNewCardNum = getNumber(topNewCard);
		boolean acceptDrop = false;
		final boolean cascadeCardIsBlack = cascadeSuit.equals("clubs")
				|| cascadeSuit.equals("spades");
		final boolean topNewCardIsBlack = topNewCardSuit.equals("clubs")
				|| topNewCardSuit.equals("spades");
		if (topNewCardNum != cascadeNum - 1)
			acceptDrop = false;
		else
			acceptDrop = cascadeCardIsBlack && !topNewCardIsBlack
					|| !cascadeCardIsBlack && topNewCardIsBlack;
		if (acceptDrop)
			Log.d(TAG, "Drag -> " + laneIndex + ": Acceptable drag of "
					+ topNewCard + " onto " + cascadeCard);
		return acceptDrop;
	}

	public boolean acceptFoundationDrop(final int foundationIndex,
			final String newCard)
	{
		if (newCard.startsWith("MULTI"))
			// Foundations don't accept multiple cards
			return false;
		final String existingFoundationCard = foundation[-1 * foundationIndex
				- 1];
		boolean acceptDrop = false;
		if (existingFoundationCard == null)
			acceptDrop = newCard.endsWith("s1");
		else
			acceptDrop = newCard.equals(nextInSuit(existingFoundationCard));
		if (acceptDrop)
		{
			final String foundationDisplayCard = existingFoundationCard == null ? "empty foundation"
					: existingFoundationCard;
			Log.d(TAG, "Drag -> " + foundationIndex + ": Acceptable drag of "
					+ newCard + " onto " + foundationDisplayCard);
		}
		return acceptDrop;
	}

	public boolean acceptLaneDrop(final int laneIndex, final String topNewCard)
	{
		final boolean acceptDrop = topNewCard.endsWith("s13");
		if (acceptDrop)
			Log.d(TAG, "Drag -> " + laneIndex + ": Acceptable drag of "
					+ topNewCard + " onto empty lane");
		return acceptDrop;
	}

	private void addMoveToUndo(final Move move)
	{
		moves.push(move);
		if (moves.size() == 1)
			activity.invalidateOptionsMenu();
	}

	public void animationCompleted()
	{
		pendingMoves--;
		moveCompleted(true);
	}

	public boolean attemptAutoMoveFromCascadeToFoundation(final int laneIndex)
	{
		if (lane[laneIndex - 1].getCascade().isEmpty())
			return false;
		final String card = lane[laneIndex - 1].getCascade().getLast();
		for (int foundationIndex = -1; foundationIndex >= -12; foundationIndex--)
			if (acceptFoundationDrop(foundationIndex, card))
			{
				move(new Move(Type.AUTO_PLAY, foundationIndex, laneIndex, card));
				return true;
			}
		return false;
	}

	public boolean attemptAutoMoveFromWasteToFoundation()
	{
		if (waste.isEmpty())
			return false;
		final String card = waste.getFirst();
		for (int foundationIndex = -1; foundationIndex >= -12; foundationIndex--)
			if (acceptFoundationDrop(foundationIndex, card))
			{
				move(new Move(Type.AUTO_PLAY, foundationIndex, card));
				return true;
			}
		return false;
	}

	private void autoPlay()
	{
		final AutoPlayPreference autoPlayPreference = activity
				.getAutoPlayPreference();
		if (autoPlayPreference == AutoPlayPreference.AUTOPLAY_NEVER)
			return;
		else if (autoPlayPreference == AutoPlayPreference.AUTOPLAY_WHEN_WON)
		{
			int totalStackSize = 0;
			for (int laneIndex = 0; laneIndex < 13; laneIndex++)
				totalStackSize += lane[laneIndex].getStack().size();
			if (totalStackSize > 0 || !stock.isEmpty() || waste.size() > 1)
				return;
		}
		for (int laneIndex = 0; laneIndex < 13; laneIndex++)
			if (!autoplayLaneIndexLocked[laneIndex]
					&& attemptAutoMoveFromCascadeToFoundation(laneIndex + 1))
				return;
		attemptAutoMoveFromWasteToFoundation();
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

	public boolean canUndo()
	{
		return !moves.empty();
	}

	private void checkForWin()
	{
		for (int foundationIndex = 0; foundationIndex < 12; foundationIndex++)
			if (foundation[foundationIndex] == null
					|| !foundation[foundationIndex].endsWith("s13"))
				return;
		Log.d(TAG, "Game win detected");
		pauseGame();
		activity.showDialog(TripleSolitaireActivity.DIALOG_ID_WINNING);
	}

	public String getFoundationCard(final int foundationIndex)
	{
		return foundation[foundationIndex];
	}

	public long getGameId()
	{
		return gameId;
	}

	private int getNumber(final String card)
	{
		int firstNumber;
		for (firstNumber = 0; firstNumber < card.length(); firstNumber++)
			if (Character.isDigit(card.charAt(firstNumber)))
				break;
		return Integer.parseInt(card.substring(firstNumber));
	}

	private String getSuit(final String card)
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

	public boolean isStockEmpty()
	{
		return stock.isEmpty();
	}

	public boolean isWasteEmpty()
	{
		return waste.isEmpty();
	}

	public void move(final Move move)
	{
		Log.d(TAG, move.toString());
		switch (move.getType())
		{
			case STOCK:
				if (stock.isEmpty())
				{
					stock.addAll(waste);
					waste.clear();
					addMoveToUndo(move);
				}
				else
				{
					final StringBuffer sb = new StringBuffer();
					String card = stock.pop();
					sb.append(card);
					waste.addFirst(card);
					for (int wasteIndex = 1; wasteIndex < 3 && !stock.isEmpty(); wasteIndex++)
					{
						card = stock.pop();
						sb.append(';');
						sb.append(card);
						waste.addFirst(card);
					}
					addMoveToUndo(new Move(Move.Type.STOCK, sb.toString()));
				}
				activity.updateWasteUI();
				activity.updateStockUI();
				moveCompleted(true);
				break;
			case UNDO_STOCK:
				if (waste.isEmpty())
				{
					waste.addAll(stock);
					stock.clear();
				}
				else
				{
					final Iterator<String> iterator = move.getCascade()
							.descendingIterator();
					while (iterator.hasNext())
					{
						stock.push(iterator.next());
						waste.removeFirst();
					}
				}
				activity.updateWasteUI();
				activity.updateStockUI();
				break;
			case FLIP:
				final String toFlip = lane[move.getToIndex() - 1].getStack()
						.pop();
				lane[move.getToIndex() - 1].getCascade().add(toFlip);
				addMoveToUndo(move);
				activity.getLane(move.getToIndex() - 1)
						.flipOverTopStack(toFlip);
				autoPlay();
				break;
			case UNDO_FLIP:
				final String flippedCard = lane[move.getToIndex() - 1]
						.getCascade().removeFirst();
				lane[move.getToIndex() - 1].getStack().add(flippedCard);
				final int newStackSize = lane[move.getToIndex() - 1].getStack()
						.size();
				activity.getLane(move.getToIndex() - 1).setStackSize(
						newStackSize);
				break;
			case AUTO_PLAY:
			case UNDO:
			case PLAYER_MOVE:
				// Update game state at from location
				if (move.getFromIndex() < 0)
					foundation[-1 * move.getFromIndex() - 1] = prevInSuit(move
							.getCard());
				else if (move.getFromIndex() == 0)
					waste.removeFirst();
				else
					for (@SuppressWarnings("unused")
					final String card : move.getCascade())
						lane[move.getFromIndex() - 1].getCascade().removeLast();
				// Update game state at to location
				if (move.getToIndex() < 0)
					foundation[-1 * move.getToIndex() - 1] = move.getCard();
				else if (move.getToIndex() == 0)
					waste.addFirst(move.getCard());
				else
					lane[move.getToIndex() - 1].getCascade().addAll(
							move.getCascade());
				// Add move to the undo list if it isn't an undo move
				if (move.getType() != Move.Type.UNDO)
					addMoveToUndo(move);
				// Update the from UI
				if (move.getFromIndex() < 0)
					activity.updateFoundationUI(-1 * move.getFromIndex() - 1);
				else if (move.getFromIndex() == 0)
					activity.updateWasteUI();
				else
					activity.getLane(move.getFromIndex() - 1)
							.decrementCascadeSize(move.getCascade().size());
				// Update the to UI
				if (move.getType() == Move.Type.AUTO_PLAY)
				{
					pendingMoves++;
					activity.animate(move);
				}
				else if (move.getType() == Move.Type.UNDO)
					activity.animate(move);
				else if (move.getToIndex() < 0)
				{
					activity.updateFoundationUI(-1 * move.getToIndex() - 1);
					moveCompleted(move.getFromIndex() < 0);
				}
				else if (move.getToIndex() == 0)
					activity.updateWasteUI();
				else
				{
					if (move.getFromIndex() < 0)
						autoplayLaneIndexLocked[move.getToIndex() - 1] = true;
					activity.getLane(move.getToIndex() - 1).addCascade(
							move.getCascade());
				}
				break;
		}
	}

	private void moveCompleted(final boolean resetAutoplayLaneIndexLocked)
	{
		activity.updateMoveCount(++moveCount);
		if (moveCount == 1)
			resumeGame();
		if (resetAutoplayLaneIndexLocked)
			for (int laneIndex = 0; laneIndex < 13; laneIndex++)
				autoplayLaneIndexLocked[laneIndex] = false;
		checkForWin();
		if (pendingMoves == 0)
			autoPlay();
	}

	public void newGame()
	{
		final ArrayList<String> fullDeck = new ArrayList<String>();
		final String[] suitList = { "clubs", "diamonds", "hearts", "spades" };
		for (int deckNum = 0; deckNum < 3; deckNum++)
			for (final String suit : suitList)
				for (int cardNum = 1; cardNum <= 13; cardNum++)
					fullDeck.add(suit + cardNum);
		final Random random = new Random();
		gameId = random.nextLong();
		random.setSeed(gameId);
		timeInSeconds = 0;
		activity.updateTime(timeInSeconds);
		moveCount = 0;
		activity.updateMoveCount(moveCount);
		for (int h = 0; h < 13; h++)
			autoplayLaneIndexLocked[h] = false;
		moves = new Stack<Move>();
		activity.invalidateOptionsMenu();
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

	private String nextInSuit(final String card)
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
		autoplayLaneIndexLocked = savedInstanceState
				.getBooleanArray("autoplayLaneIndexLocked");
		final ArrayList<String> arrayMoves = savedInstanceState
				.getStringArrayList("moves");
		moves = new Stack<Move>();
		for (final String move : arrayMoves)
			moves.push(new Move(move));
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
		outState.putBooleanArray("autoplayLaneIndexLocked",
				autoplayLaneIndexLocked);
		final ArrayList<String> arrayMoves = new ArrayList<String>();
		for (final Move move : moves)
			arrayMoves.add(move.toString());
		outState.putStringArrayList("moves", arrayMoves);
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

	public void pauseGame()
	{
		gameInProgress = false;
		timerHandler.removeCallbacks(gameTimerIncrement);
	}

	private String prevInSuit(final String card)
	{
		if (card.endsWith("s1"))
			return null;
		return getSuit(card) + (getNumber(card) - 1);
	}

	public void resumeGame()
	{
		gameInProgress = moveCount > 0;
		if (gameInProgress)
		{
			timerHandler.removeCallbacks(gameTimerIncrement);
			timerHandler.postDelayed(gameTimerIncrement, 1000);
		}
	}

	public void undo()
	{
		if (moves.empty())
			return;
		move(moves.pop().toUndo());
		if (moves.empty())
			activity.invalidateOptionsMenu();
	}
}
