package com.github.triplesolitaire;

import java.util.LinkedList;
import java.util.ListIterator;
import java.util.StringTokenizer;

public class Move
{
	public enum Type {
		AUTO_PLAY, FLIP, PLAYER_MOVE, STOCK, UNDO, UNDO_FLIP, UNDO_STOCK
	}

	private final LinkedList<String> cascade = new LinkedList<String>();
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
			fillCascade(st.nextToken());
	}

	public Move(final Type type)
	{
		this.type = type;
		toIndex = 0;
		fromIndex = 0;
	}

	public Move(final Type type, final int toIndex)
	{
		this.type = type;
		this.toIndex = toIndex;
		fromIndex = 0;
	}

	public Move(final Type type, final int toIndex, final int fromIndex)
	{
		this.type = type;
		this.toIndex = toIndex;
		this.fromIndex = fromIndex;
	}

	public Move(final Type type, final int toIndex, final int fromIndex,
			final String card)
	{
		this.type = type;
		this.toIndex = toIndex;
		this.fromIndex = fromIndex;
		fillCascade(card);
	}

	public Move(final Type type, final int toIndex, final String card)
	{
		this.type = type;
		this.toIndex = toIndex;
		fromIndex = 0;
		fillCascade(card);
	}

	public Move(final Type type, final String card)
	{
		this.type = type;
		toIndex = 0;
		fromIndex = 0;
		fillCascade(card);
	}

	private void fillCascade(final String card)
	{
		final StringTokenizer st = new StringTokenizer(card, ";");
		while (st.hasMoreTokens())
			cascade.add(st.nextToken());
	}

	public String getCard()
	{
		if (cascade.isEmpty())
			return "";
		return cascade.getFirst();
	}

	public LinkedList<String> getCascade()
	{
		return cascade;
	}

	private String getCascadeAsString()
	{
		if (cascade.isEmpty())
			return "";
		final StringBuffer sb = new StringBuffer();
		final ListIterator<String> listIterator = cascade.listIterator();
		sb.append(listIterator.next());
		while (listIterator.hasNext())
		{
			sb.append(';');
			sb.append(listIterator.next());
		}
		return sb.toString();
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
		final StringBuffer sb = new StringBuffer();
		sb.append(type.toString());
		sb.append(':');
		sb.append(fromIndex);
		sb.append('>');
		sb.append(toIndex);
		sb.append(':');
		sb.append(getCascadeAsString());
		return sb.toString();
	}

	public Move toUndo()
	{
		if (type == Type.FLIP)
			return new Move(Type.UNDO_FLIP, toIndex);
		else if (type == Type.STOCK)
			return new Move(Type.UNDO_STOCK, getCascadeAsString());
		return new Move(Type.UNDO, fromIndex, toIndex, getCascadeAsString());
	}
}
