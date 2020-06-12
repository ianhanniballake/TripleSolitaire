package com.github.triplesolitaire;

import java.util.LinkedList;
import java.util.ListIterator;
import java.util.StringTokenizer;

import androidx.annotation.NonNull;

/**
 * Class encapsulating a single 'move' from any and all sources. See the Move.Type enum for enumeration of all types of
 * Moves
 */
public class Move {
    /**
     * Enum representing all of the types of moves
     */
    public enum Type {
        /**
         * Auto playing a card to a foundation
         */
        AUTO_PLAY, /**
         * Move for flipping over the top card in a stack
         */
        FLIP, /**
         * Player initiated move (drag and drop of a card)
         */
        PLAYER_MOVE, /**
         * Clicking on the Stock
         */
        STOCK, /**
         * Undo of an AUTO_PLAY or PLAYER_MOVE Move
         */
        UNDO, /**
         * Undo of a FLIP Move
         */
        UNDO_FLIP, /**
         * Undo of a STOCK Move
         */
        UNDO_STOCK
    }

    /**
     * Cascade of cards. May contain only a single card.
     */
    private final LinkedList<String> cascade = new LinkedList<>();
    /**
     * Source location in the following format:
     * <ul>
     * <li>Lanes: One-based index (1 through 13)</li>
     * <li>Waste: 0</li>
     * <li>Foundation: Negative One-based index (-1 through -12)</li>
     * </ul>
     */
    private final int fromIndex;
    /**
     * Destination location in the following format:
     * <ul>
     * <li>Lanes: One-based index (1 through 13)</li>
     * <li>Waste: 0</li>
     * <li>Foundation: Negative One-based index (-1 through -12)</li>
     * </ul>
     */
    private final int toIndex;
    /**
     * Type of the move
     */
    private final Type type;

    /**
     * Creates a Move from its formatted String form (as returned by toString)
     *
     * @param move String form of a move as returned by toString
     */
    public Move(final String move) {
        final StringTokenizer st = new StringTokenizer(move, ":>");
        type = Type.valueOf(st.nextToken());
        fromIndex = Integer.parseInt(st.nextToken());
        toIndex = Integer.parseInt(st.nextToken());
        if (st.hasMoreTokens())
            fillCascade(st.nextToken());
    }

    /**
     * Move involving no cards and no from/to location, associated with STOCK moves.
     *
     * @param type Type (STOCK)
     */
    public Move(final Type type) {
        this.type = type;
        toIndex = 0;
        fromIndex = 0;
    }

    /**
     * Move containing only a single location, associated with the FLIP move.
     *
     * @param type    Type (FLIP)
     * @param toIndex Index of the FLIP Move
     */
    public Move(final Type type, final int toIndex) {
        this.type = type;
        this.toIndex = toIndex;
        fromIndex = 0;
    }

    /**
     * Moves involving a single card from one location to another
     *
     * @param type      Type of Move
     * @param toIndex   Destination location
     * @param fromIndex Source location
     * @param card      Card to move
     */
    public Move(final Type type, final int toIndex, final int fromIndex, final String card) {
        this.type = type;
        this.toIndex = toIndex;
        this.fromIndex = fromIndex;
        fillCascade(card);
    }

    /**
     * Move of one or more cards involving the stock.
     *
     * @param type Type (STOCK or UNDO_STOCK)
     * @param card Card(s) moved
     */
    public Move(final Type type, final String card) {
        this.type = type;
        toIndex = 0;
        fromIndex = 0;
        fillCascade(card);
    }

    /**
     * Parses the given card(s) into the cascade, assumes semicolon separators
     *
     * @param card Card(s) to parse, semicolon separated
     */
    private void fillCascade(final String card) {
        final StringTokenizer st = new StringTokenizer(card, ";");
        while (st.hasMoreTokens())
            cascade.add(st.nextToken());
    }

    /**
     * Gets the card if Move of a single card or bottom card of the cascade if it is a multiple card Move
     *
     * @return The card if Move of a single card or bottom card of the cascade if it is a multiple card Move
     */
    public String getCard() {
        if (cascade.isEmpty())
            return "";
        return cascade.getFirst();
    }

    /**
     * Gets the full list of cards included in the Move
     *
     * @return The full list of cards included in the Move
     */
    public LinkedList<String> getCascade() {
        return cascade;
    }

    /**
     * Gets a string representation of the cascade.
     *
     * @return A semicolon separated list of cards to use as a string representation of the cascade
     */
    private String getCascadeAsString() {
        if (cascade.isEmpty())
            return "";
        final StringBuilder sb = new StringBuilder();
        final ListIterator<String> listIterator = cascade.listIterator();
        sb.append(listIterator.next());
        while (listIterator.hasNext()) {
            sb.append(';');
            sb.append(listIterator.next());
        }
        return sb.toString();
    }

    /**
     * Getter for the source location in the following format:
     * <ul>
     * <li>Lanes: One-based index (1 through 13)</li>
     * <li>Waste: 0</li>
     * <li>Foundation: Negative One-based index (-1 through -12)</li>
     * </ul>
     *
     * @return The source location
     */
    public int getFromIndex() {
        return fromIndex;
    }

    /**
     * Getter for the destination location in the following format:
     * <ul>
     * <li>Lanes: One-based index (1 through 13)</li>
     * <li>Waste: 0</li>
     * <li>Foundation: Negative One-based index (-1 through -12)</li>
     * </ul>
     *
     * @return The destination location
     */
    public int getToIndex() {
        return toIndex;
    }

    /**
     * Getter for the Type of this Move
     *
     * @return The Type of this Move
     */
    public Type getType() {
        return type;
    }

    /**
     * Returns a string representation of this Move, useful for debugging or serialization
     *
     * @see java.lang.Object#toString()
     */
    @Override
    @NonNull
    public String toString() {
        return type.toString() + ':' + fromIndex + '>' + toIndex + ':' + getCascadeAsString();
    }

    /**
     * Converts this move into its Undo equivalent, changing the type and from/to locations as necessary
     *
     * @return A Move that would perfectly undo this Move
     */
    public Move toUndo() {
        if (type == Type.FLIP)
            return new Move(Type.UNDO_FLIP, toIndex);
        else if (type == Type.STOCK)
            return new Move(Type.UNDO_STOCK, getCascadeAsString());
        return new Move(Type.UNDO, fromIndex, toIndex, getCascadeAsString());
    }
}
