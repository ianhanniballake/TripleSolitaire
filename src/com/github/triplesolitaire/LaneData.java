package com.github.triplesolitaire;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Stack;

public class LaneData
{
	private final LinkedList<String> cascade;
	private final Stack<String> stack;

	public LaneData()
	{
		stack = new Stack<String>();
		cascade = new LinkedList<String>();
	}

	public LaneData(final ArrayList<String> arrayStack,
			final ArrayList<String> cascade)
	{
		stack = new Stack<String>();
		for (final String card : arrayStack)
			stack.push(card);
		this.cascade = new LinkedList<String>(cascade);
	}

	/**
	 * @return the cascade
	 */
	public LinkedList<String> getCascade()
	{
		return cascade;
	}

	/**
	 * @return the stack
	 */
	public Stack<String> getStack()
	{
		return stack;
	}
}
