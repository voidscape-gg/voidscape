package com.openrsc.server.net.rsc.struct.outgoing;

import com.openrsc.server.net.rsc.enums.OpcodeOut;
import com.openrsc.server.net.rsc.struct.AbstractStruct;

public class VoidScoutStateStruct extends AbstractStruct<OpcodeOut> {
	public boolean active;
	public int bodyX;
	public int bodyY;
	public int viewX;
	public int viewY;
	public int maxDistance;
	public int remainingMillis;
}
