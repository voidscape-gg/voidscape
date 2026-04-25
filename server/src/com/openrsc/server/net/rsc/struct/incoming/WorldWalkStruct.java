package com.openrsc.server.net.rsc.struct.incoming;

import com.openrsc.server.net.rsc.enums.OpcodeIn;
import com.openrsc.server.net.rsc.struct.AbstractStruct;

/**
 * Inbound payload for the world-map auto-walker. Just a destination tile;
 * the server pathfinds, drives walking, and replies with the route via
 * {@link com.openrsc.server.net.rsc.struct.outgoing.WorldWalkRouteStruct}.
 */
public class WorldWalkStruct extends AbstractStruct<OpcodeIn> {
	public int destX;
	public int destY;
}
