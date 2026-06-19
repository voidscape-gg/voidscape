package com.openrsc.server.net.rsc.struct.outgoing;

import com.openrsc.server.net.rsc.enums.OpcodeOut;
import com.openrsc.server.net.rsc.struct.AbstractStruct;

public class VoidRushWaveStruct extends AbstractStruct<OpcodeOut> {
	public int direction;
	public int fromLine;
	public int toLine;
	public int gapStart;
	public int gapEnd;
	public boolean lethal;
}
