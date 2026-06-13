package com.openrsc.server.net.rsc.struct.outgoing;

import com.openrsc.server.net.rsc.enums.OpcodeOut;
import com.openrsc.server.net.rsc.struct.AbstractStruct;

public class BestiaryStruct extends AbstractStruct<OpcodeOut> {

	public NpcEntry[] entries = new NpcEntry[0];

	public static class NpcEntry {
		public int npcId;
		public int killCount;
		public DropEntry[] drops = new DropEntry[0];
	}

	public static class DropEntry {
		public int itemId;
		public long amount;
	}
}
