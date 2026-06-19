package com.openrsc.server.database.struct;

public class ItemProvenanceEvent {
	public long eventID;
	public long itemID;
	public int catalogID;
	public int amount;
	public boolean noted;
	public int actorID;
	public String actorUsername;
	public int targetID;
	public String targetUsername;
	public String eventType;
	public String source;
	public String destination;
	public String command;
	public int x;
	public int y;
	public long time;
	public String extra;
}
