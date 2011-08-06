package com.github.triplesolitaire;

import java.util.StringTokenizer;

public class Move
{
	public enum Type {
		AUTO_PLAY, FLIP, PLAYER_MOVE, STOCK, UNDO
	}

	private final String card;
	private final int fromIndex;
	private final int toIndex;
	private final Type type;

	public Move(final String move)
	{
		final StringTokenizer st = new StringTokenizer(move, ":>");
		type = Type.valueOf(st.nextToken());
		fromIndex = Integer.parseInt(st.nextToken());
		toIndex = Integer.parseInt(st.nextToken());
		if (st.hasMoreTokens())
			card = st.nextToken();
		else
			card = "";
	}

	public Move(final Type type)
	{
		this.type = type;
		toIndex = 0;
		fromIndex = 0;
		card = "";
	}

	public Move(final Type type, final int toIndex)
	{
		this.type = type;
		this.toIndex = toIndex;
		fromIndex = 0;
		card = "";
	}

	public Move(final Type type, final int toIndex, final int fromIndex)
	{
		this.type = type;
		this.toIndex = toIndex;
		this.fromIndex = fromIndex;
		card = "";
	}

	public Move(final Type type, final int toIndex, final int fromIndex,
			final String card)
	{
		this.type = type;
		this.toIndex = toIndex;
		this.fromIndex = fromIndex;
		this.card = card;
	}

	public String getCard()
	{
		return card;
	}

	public int getFromIndex()
	{
		return fromIndex;
	}

	public int getToIndex()
	{
		return toIndex;
	}

	public Type getType()
	{
		return type;
	}

	@Override
	public String toString()
	{
		return type.toString() + ":" + fromIndex + ">" + toIndex + ":" + card;
	}
}
