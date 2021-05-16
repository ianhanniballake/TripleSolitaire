package com.github.triplesolitaire

import java.util.StringTokenizer

/**
 * Class encapsulating a single 'move' from any and all sources.
 * See the Move.Type enum for enumeration of all types of Moves
 */
class Move {
    /**
     * Enum representing all of the types of moves
     */
    enum class Type {
        /**
         * Auto playing a card to a foundation
         */
        AUTO_PLAY,
        /**
         * Move for flipping over the top card in a stack
         */
        FLIP,
        /**
         * Player initiated move (drag and drop of a card)
         */
        PLAYER_MOVE,
        /**
         * Clicking on the Stock
         */
        STOCK,
        /**
         * Undo of an AUTO_PLAY or PLAYER_MOVE Move
         */
        UNDO,
        /**
         * Undo of a FLIP Move
         */
        UNDO_FLIP,
        /**
         * Undo of a STOCK Move
         */
        UNDO_STOCK
    }

    /**
     * Cascade of cards. May contain only a single card.
     */
    val cascade = ArrayDeque<String>()

    /**
     * Source location in the following format:
     *
     * - Lanes: One-based index (1 through 13)
     * - Waste: 0
     * - Foundation: Negative One-based index (-1 through -12)
     *
     */
    val fromIndex: Int

    /**
     * Destination location in the following format:
     *
     * - Lanes: One-based index (1 through 13)
     * - Waste: 0
     * - Foundation: Negative One-based index (-1 through -12)
     *
     */
    val toIndex: Int

    /**
     * Type of the move
     */
    val type: Type

    /**
     * Creates a Move from its formatted String form (as returned by toString)
     *
     * @param move String form of a move as returned by toString
     */
    constructor(move: String?) {
        val st = StringTokenizer(move, ":>")
        type = Type.valueOf(st.nextToken())
        fromIndex = st.nextToken().toInt()
        toIndex = st.nextToken().toInt()
        if (st.hasMoreTokens()) {
            fillCascade(st.nextToken())
        }
    }

    /**
     * Move involving no cards and no from/to location, associated with STOCK moves.
     *
     * @param type Type (STOCK)
     */
    constructor(type: Type) {
        this.type = type
        toIndex = 0
        fromIndex = 0
    }

    /**
     * Move containing only a single location, associated with the FLIP move.
     *
     * @param type    Type (FLIP)
     * @param toIndex Index of the FLIP Move
     */
    constructor(type: Type, toIndex: Int) {
        this.type = type
        this.toIndex = toIndex
        fromIndex = 0
    }

    /**
     * Moves involving a single card from one location to another
     *
     * @param type      Type of Move
     * @param toIndex   Destination location
     * @param fromIndex Source location
     * @param card      Card to move
     */
    constructor(type: Type, toIndex: Int, fromIndex: Int, card: String) {
        this.type = type
        this.toIndex = toIndex
        this.fromIndex = fromIndex
        fillCascade(card)
    }

    /**
     * Move of one or more cards involving the stock.
     *
     * @param type Type (STOCK or UNDO_STOCK)
     * @param card Card(s) moved
     */
    constructor(type: Type, card: String) {
        this.type = type
        toIndex = 0
        fromIndex = 0
        fillCascade(card)
    }

    /**
     * Parses the given card(s) into the cascade, assumes semicolon separators
     *
     * @param card Card(s) to parse, semicolon separated
     */
    private fun fillCascade(card: String) {
        val st = StringTokenizer(card, ";")
        while (st.hasMoreTokens()) {
            cascade.add(st.nextToken())
        }
    }

    /**
     * Gets the card if Move of a single card or bottom card of the cascade
     * if it is a multiple card Move
     *
     * @return The card if Move of a single card or bottom card of the cascade
     * if it is a multiple card Move
     */
    val card: String
        get() = if (cascade.isEmpty()) "" else cascade.first()

    /**
     * Gets a string representation of the cascade.
     *
     * @return A semicolon separated list of cards to use as a string
     * representation of the cascade
     */
    private val cascadeAsString: String
        get() = cascade.joinToString(";")

    /**
     * Returns a string representation of this Move, useful for debugging or serialization
     *
     * @see java.lang.Object.toString
     */
    override fun toString(): String {
        return "$type:$fromIndex>$toIndex:$cascadeAsString"
    }

    /**
     * Converts this move into its Undo equivalent, changing the type and
     * from/to locations as necessary
     *
     * @return A Move that would perfectly undo this Move
     */
    fun toUndo() = when (type) {
        Type.FLIP -> {
            Move(Type.UNDO_FLIP, toIndex)
        }
        Type.STOCK -> {
            Move(Type.UNDO_STOCK, cascadeAsString)
        }
        else -> Move(Type.UNDO, fromIndex, toIndex, cascadeAsString)
    }
}