package com.github.triplesolitaire;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Random;
import java.util.Stack;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;

import com.github.triplesolitaire.Move.Type;

/**
 * Class to manage the game state associated with a Triple Solitaire game
 */
public class GameState
{
	/**
	 * Logging tag
	 */
	private static final String TAG = "TripleSolitaireActivity";
	/**
	 * Activity to issue UI update callbacks
	 */
	private final TripleSolitaireActivity activity;
	/**
	 * Whether a lane should be excluded from autoplay (i.e., if the user just
	 * dragged a card from the foundation to that column
	 */
	private boolean[] autoplayLaneIndexLocked = new boolean[13];
	/**
	 * Cards in the Foundation
	 */
	private String[] foundation;
	/**
	 * Current Game ID, used for debugging a specific game
	 */
	private long gameId = 0;
	/**
	 * Whether a game is in progress
	 */
	private boolean gameInProgress = false;
	/**
	 * Increments the game timer is there is at least one move. Posts another
	 * copy of itself to trigger in 1 second if the game is in progress.
	 */
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
	/**
	 * Data (stack and cascade information) for each lane
	 */
	private LaneData[] lane;
	/**
	 * Number of player moves in the current game
	 */
	private int moveCount = 0;
	/**
	 * A list of all undoable moves
	 */
	private Stack<Move> moves;
	/**
	 * Number of auto play moves that are pending animation complete
	 */
	private int pendingMoves = 0;
	/**
	 * Represents the cards in the stock
	 */
	private Stack<String> stock;
	/**
	 * How much time has elapsed in the current game
	 */
	private int timeInSeconds = 0;
	/**
	 * Handler for running the game timer
	 */
	private final Handler timerHandler = new Handler();
	/**
	 * Represents the cards in the waste
	 */
	private LinkedList<String> waste;

	/**
	 * Creates a new GameState instance
	 * 
	 * @param activity
	 *            Activity to send UI update commands
	 */
	public GameState(final TripleSolitaireActivity activity)
	{
		this.activity = activity;
	}

	/**
	 * Whether the given lane should accept the given dropped card/cascade
	 * 
	 * @param laneIndex
	 *            One-based index (1 through 13) of the lane drop target
	 * @param bottomNewCard
	 *            Bottom card of the cascade/the card to be dropped
	 * @return Whether the lane should accept the drop
	 */
	public boolean acceptCascadeDrop(final int laneIndex,
			final String bottomNewCard)
	{
		final String cascadeCard = lane[laneIndex - 1].getCascade().getLast();
		final String cascadeSuit = getSuit(cascadeCard);
		final int cascadeNum = getNumber(cascadeCard);
		final String bottomNewCardSuit = getSuit(bottomNewCard);
		final int bottomNewCardNum = getNumber(bottomNewCard);
		boolean acceptDrop = false;
		final boolean cascadeCardIsBlack = cascadeSuit.equals("clubs")
				|| cascadeSuit.equals("spades");
		final boolean bottomNewCardIsBlack = bottomNewCardSuit.equals("clubs")
				|| bottomNewCardSuit.equals("spades");
		if (bottomNewCardNum != cascadeNum - 1)
			acceptDrop = false;
		else
			acceptDrop = cascadeCardIsBlack && !bottomNewCardIsBlack
					|| !cascadeCardIsBlack && bottomNewCardIsBlack;
		if (acceptDrop)
			Log.d(TAG, "Drag -> " + laneIndex + ": Acceptable drag of "
					+ bottomNewCard + " onto " + cascadeCard);
		return acceptDrop;
	}

	/**
	 * Whether the given foundation should accept the given dropped card. Note,
	 * that no multi-card drops are accepted on the foundation
	 * 
	 * @param foundationIndex
	 *            Negative One-based index (-1 through -12) of the foundation
	 *            drop target
	 * @param newCard
	 *            The card to be dropped
	 * @return Whether the foundation should accept the drop
	 */
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

	/**
	 * Whether the empty lane should accept a dropped card/cascade
	 * 
	 * @param laneIndex
	 *            One-based index (1 through 13) of the lane drop target
	 * @param topNewCard
	 *            Top card of the cascade/the card to be dropped
	 * @return Whether the lane should accept the drop
	 */
	public boolean acceptLaneDrop(final int laneIndex, final String topNewCard)
	{
		final boolean acceptDrop = topNewCard.endsWith("s13");
		if (acceptDrop)
			Log.d(TAG, "Drag -> " + laneIndex + ": Acceptable drag of "
					+ topNewCard + " onto empty lane");
		return acceptDrop;
	}

	/**
	 * Adds the given move to the undo stack and updates the UI if this is the
	 * first move in the undo stack
	 * 
	 * @param move
	 *            Move to add to the undo stack
	 */
	private void addMoveToUndo(final Move move)
	{
		moves.push(move);
		if (moves.size() == 1)
			activity.invalidateOptionsMenu();
	}

	/**
	 * Callback from the UI to inform us of animation completion
	 */
	public void animationCompleted()
	{
		pendingMoves--;
		moveCompleted(true);
	}

	/**
	 * Attempts to auto play the first card in the given cascade to the
	 * foundation (moving from -1 to -12 i.e., left to right)
	 * 
	 * @param laneIndex
	 *            One-based index (1 through 13)
	 * @return Whether an auto play was found
	 */
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

	/**
	 * Attempts to auto play the first card in the waste to the foundation
	 * (moving from -1 to -12 i.e., left to right)
	 * 
	 * @return Whether an auto play was found
	 */
	public boolean attemptAutoMoveFromWasteToFoundation()
	{
		if (waste.isEmpty())
			return false;
		final String card = waste.getFirst();
		for (int foundationIndex = -1; foundationIndex >= -12; foundationIndex--)
			if (acceptFoundationDrop(foundationIndex, card))
			{
				move(new Move(Type.AUTO_PLAY, foundationIndex, 0, card));
				return true;
			}
		return false;
	}

	/**
	 * Using the user's Autoplay preference, attempts to auto play a single
	 * card, first looking through the lanes (from 1 to 13 i.e., left to right)
	 * and then to the waste
	 */
	private void autoPlay()
	{
		final SharedPreferences preferences = PreferenceManager
				.getDefaultSharedPreferences(activity);
		final String preference = preferences.getString(
				Preferences.AUTO_PLAY_PREFERENCE_KEY,
				activity.getString(R.string.pref_auto_play_default));
		// If preference is never we have nothing to do
		if (preference.equals("never"))
			return;
		else if (preference.equals("won"))
		{
			// Check to make sure the user has 'won'
			int totalStackSize = 0;
			for (int laneIndex = 0; laneIndex < 13; laneIndex++)
				totalStackSize += lane[laneIndex].getStack().size();
			if (totalStackSize > 0 || !stock.isEmpty() || waste.size() > 1)
				return;
		}
		// Auto play
		for (int laneIndex = 0; laneIndex < 13; laneIndex++)
			if (!autoplayLaneIndexLocked[laneIndex]
					&& attemptAutoMoveFromCascadeToFoundation(laneIndex + 1))
				return;
		attemptAutoMoveFromWasteToFoundation();
	}

	/**
	 * Builds a string containing a semicolon separated list of the top
	 * numCardsToInclude cards from the given lane
	 * 
	 * @param laneIndex
	 *            One-based index (1 through 13)
	 * @param numCardsToInclude
	 *            The number of cards to include
	 * @return A semicolon separated list of the cards in the requested cascade
	 */
	public String buildCascadeString(final int laneIndex,
			final int numCardsToInclude)
	{
		final LinkedList<String> cascade = lane[laneIndex - 1].getCascade();
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

	/**
	 * Whether there exists a move to undo
	 * 
	 * @return Whether there exists a move to undo
	 */
	public boolean canUndo()
	{
		return !moves.empty();
	}

	/**
	 * Checks to determine if the player has won the game (all foundations have
	 * a king). If so, shows the 'you won' dialog.
	 */
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

	/**
	 * Gets the foundation card in the given foundation index
	 * 
	 * @param foundationIndex
	 *            Negative One-based index (-1 through -12) of the foundation
	 * @return the card in the given foundation location
	 */
	public String getFoundationCard(final int foundationIndex)
	{
		return foundation[-1 * foundationIndex - 1];
	}

	/**
	 * Gets the current game's game id
	 * 
	 * @return The current game's game id
	 */
	public long getGameId()
	{
		return gameId;
	}

	/**
	 * Parses a given card to return the card number (from ace at 1 to king at
	 * 13)
	 * 
	 * @param card
	 *            Card to get the number from
	 * @return The number of the card, ranging from ace at 1 to king at 13
	 */
	private int getNumber(final String card)
	{
		int firstNumber;
		for (firstNumber = 0; firstNumber < card.length(); firstNumber++)
			if (Character.isDigit(card.charAt(firstNumber)))
				break;
		return Integer.parseInt(card.substring(firstNumber));
	}

	/**
	 * Parses a given card to return its suit
	 * 
	 * @param card
	 *            Card to get the suit from
	 * @return The suit of the card
	 */
	private String getSuit(final String card)
	{
		int firstNumber;
		for (firstNumber = 0; firstNumber < card.length(); firstNumber++)
			if (Character.isDigit(card.charAt(firstNumber)))
				break;
		return card.substring(0, firstNumber);
	}

	/**
	 * Gets the requested card from the waste
	 * 
	 * @param wasteIndex
	 *            Zero-based index of what waste card to return
	 * @return The card in the given position in the waste
	 */
	public String getWasteCard(final int wasteIndex)
	{
		if (wasteIndex < waste.size())
			return waste.get(wasteIndex);
		return null;
	}

	/**
	 * Whether there are any cards in the stock
	 * 
	 * @return Whether there are any cards in the stock
	 */
	public boolean isStockEmpty()
	{
		return stock.isEmpty();
	}

	/**
	 * Whether there are any cards in the waste
	 * 
	 * @return Whether there are any cards in the waste
	 */
	public boolean isWasteEmpty()
	{
		return waste.isEmpty();
	}

	/**
	 * Triggers a move, whether player initiated or an auto play move. Moves are
	 * assumed to be valid. Note that moves should have to and from locations in
	 * the following format:
	 * <ul>
	 * <li>Lanes: One-based index (1 through 13)</li>
	 * <li>Waste: 0</li>
	 * <li>Foundation: Negative One-based index (-1 through -12)</li>
	 * </ul>
	 * 
	 * @param move
	 *            Move to do
	 */
	public void move(final Move move)
	{
		Log.d(TAG, move.toString());
		switch (move.getType())
		{
			case STOCK: // Clicked the stock
				if (stock.isEmpty())
				{
					// Flip are cards from the waste over into the stock
					stock.addAll(waste);
					waste.clear();
					addMoveToUndo(move);
				}
				else
				{
					// Move up to 3 cards from the stock to the waste
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
			case UNDO_STOCK: // Undo'ing a stock click
				if (waste.isEmpty())
				{
					// An empty waste means we had an empty stock right before
					// the stock click, so we move everything back to the waste
					waste.addAll(stock);
					stock.clear();
				}
				else
				{
					// We undo the move of cards from the stock to the waste
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
			case FLIP: // Flipping over a face down card in a lane
				final String toFlip = lane[move.getToIndex() - 1].getStack()
						.pop();
				lane[move.getToIndex() - 1].getCascade().add(toFlip);
				addMoveToUndo(move);
				activity.getLane(move.getToIndex() - 1)
						.flipOverTopStack(toFlip);
				for (int laneIndex = 0; laneIndex < 13; laneIndex++)
					autoplayLaneIndexLocked[laneIndex] = false;
				autoPlay();
				break;
			case UNDO_FLIP: // Undo'ing the flip of a face down card in a lane
				final String flippedCard = lane[move.getToIndex() - 1]
						.getCascade().removeFirst();
				lane[move.getToIndex() - 1].getStack().add(flippedCard);
				final int newStackSize = lane[move.getToIndex() - 1].getStack()
						.size();
				activity.getLane(move.getToIndex() - 1).setStackSize(
						newStackSize);
				break;
			case AUTO_PLAY: // Auto play
			case UNDO: // Undo of a player move or auto play
			case PLAYER_MOVE: // Player dragged move
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
					// Animate an auto play and wait for its completion before
					// doing anything else. We need to keep track of how many
					// moves are currently being animated so that we don't kick
					// off multiple auto plays
					pendingMoves++;
					activity.animate(move);
				}
				else if (move.getType() == Move.Type.UNDO)
					activity.animate(move);
				else if (move.getToIndex() < 0) // PLAYER_MOVE
				{
					activity.updateFoundationUI(-1 * move.getToIndex() - 1);
					moveCompleted(true);
				}
				else if (move.getToIndex() == 0) // PLAYER_MOVE
				{
					activity.updateWasteUI();
					moveCompleted(true);
				}
				else
				// PLAYER_MOVE, move.getToIndex > 0
				{
					if (move.getFromIndex() < 0)
						autoplayLaneIndexLocked[move.getToIndex() - 1] = true;
					activity.getLane(move.getToIndex() - 1).addCascade(
							move.getCascade());
					moveCompleted(move.getFromIndex() >= 0);
				}
				break;
		}
	}

	/**
	 * Signals completion of a move, updating the move count, starting the game
	 * timer, resetting the auto play lane locks if requested, checking for
	 * wins, and starting auto play if there are no other pending animations
	 * (which will eventually call this method).
	 * 
	 * @param resetAutoplayLaneIndexLocked
	 *            Whether to reset the auto play lane locks
	 */
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

	/**
	 * Starts a new game, resetting the game state and updating the UI to match
	 */
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

	/**
	 * Gets the next card (sequentially higher) than the given card
	 * 
	 * @param card
	 *            Card to 'increment'
	 * @return A card of the same suit and one number higher
	 */
	private String nextInSuit(final String card)
	{
		return getSuit(card) + (getNumber(card) + 1);
	}

	/**
	 * Restores the game state from a Bundle and updates the UI to match
	 * 
	 * @param savedInstanceState
	 *            Bundle to restore from
	 */
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

	/**
	 * Saves the current game state to the given Bundle
	 * 
	 * @param outState
	 *            Bundle to save game state to
	 */
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

	/**
	 * Pauses the game, stopping any pending game time increment calls
	 */
	public void pauseGame()
	{
		gameInProgress = false;
		timerHandler.removeCallbacks(gameTimerIncrement);
	}

	/**
	 * Returns the previous (one number less) card in the same suit as the given
	 * card
	 * 
	 * @param card
	 *            Card to 'decrement'
	 * @return A card one number less and in the same suit as the given card, or
	 *         null if the card was an Ace (number=1)
	 */
	private String prevInSuit(final String card)
	{
		if (card.endsWith("s1"))
			return null;
		return getSuit(card) + (getNumber(card) - 1);
	}

	/**
	 * Resumes the game, starting the game timer increment if there has been at
	 * least one move
	 */
	public void resumeGame()
	{
		gameInProgress = moveCount > 0;
		if (gameInProgress)
		{
			timerHandler.removeCallbacks(gameTimerIncrement);
			timerHandler.postDelayed(gameTimerIncrement, 1000);
		}
	}

	/**
	 * Undo's the last move
	 */
	public void undo()
	{
		if (moves.empty())
			return;
		move(moves.pop().toUndo());
		if (moves.empty())
			activity.invalidateOptionsMenu();
	}
}
