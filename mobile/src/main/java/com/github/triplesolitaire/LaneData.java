package com.github.triplesolitaire;

import java.util.LinkedList;
import java.util.List;
import java.util.Stack;

/**
 * Stores all game state associated with a Lane, including its stack and cascade. Used to get around inability to create
 * arrays of generic objects (i.e., <code>List&lt;String&gt;[]</code>).
 */
public class LaneData {
    /**
     * Cascade of flipped over card, where the last card is the card on the top (i.e., not covered by any other cards)
     * of the cascade
     */
    private final LinkedList<String> cascade;
    /**
     * Stack of cards, where the top card is the top of the stack
     */
    private final Stack<String> stack;

    /**
     * Creates a new empty LaneData
     */
    public LaneData() {
        stack = new Stack<String>();
        cascade = new LinkedList<String>();
    }

    /**
     * Creates a LaneData given a set of cards for the stack and cascade
     *
     * @param arrayStack Cards to set as the stack
     * @param cascade    Cards to set as the cascade
     */
    public LaneData(final List<String> arrayStack, final List<String> cascade) {
        stack = new Stack<String>();
        for (final String card : arrayStack)
            stack.push(card);
        this.cascade = new LinkedList<String>(cascade);
    }

    /**
     * Getter for the cascade. Additions and removal of cards should be called on the returned list.
     *
     * @return The cards in the cascade
     */
    public LinkedList<String> getCascade() {
        return cascade;
    }

    /**
     * Getter for the stack. Additions and removal of cards should be called on the returned list.
     *
     * @return The cards in the stack
     */
    public Stack<String> getStack() {
        return stack;
    }
}
