package com.github.triplesolitaire

/**
 * Stores all game state associated with a Lane, including its stack and cascade.
 */
class LaneData @JvmOverloads constructor(
    initialStack: List<String> = emptyList(),
    initialCascade: List<String> = emptyList()
) {
    /**
     * Cascade of flipped over card, where the last card is the card on the top
     * (i.e., not covered by any other cards) of the cascade
     */
    val cascade: ArrayDeque<String> = ArrayDeque()
    /**
     * Stack of cards, where the top card is the top of the stack
     */
    val stack: ArrayDeque<String> = ArrayDeque()

    init {
        for (card in initialStack) stack.addLast(card)
        cascade.addAll(initialCascade)
    }
}
