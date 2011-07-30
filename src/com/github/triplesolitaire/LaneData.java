package com.github.triplesolitaire;

import java.util.ArrayList;
import java.util.Stack;

public class LaneData
{
	private final ArrayList<String> cascade;
	private final Stack<String> stack;

	public LaneData()
	{
		stack = new Stack<String>();
		cascade = new ArrayList<String>();
	}

	public LaneData(final ArrayList<String> arrayStack,
			final ArrayList<String> cascade)
	{
		stack = new Stack<String>();
		for (final String card : arrayStack)
			stack.push(card);
		this.cascade = cascade;
	}

	/**
	 * @return the cascade
	 */
	public ArrayList<String> getCascade()
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
