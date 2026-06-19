package com.openrsc.server.net.rsc.struct.outgoing;

import com.openrsc.server.model.Point;
import com.openrsc.server.net.rsc.enums.OpcodeOut;
import com.openrsc.server.net.rsc.struct.AbstractStruct;

import java.util.Collections;
import java.util.List;

/**
 * Outbound payload acknowledging a {@code WORLD_WALK_REQUEST}.
 *
 * <p>Wire format (always emitted, even on failure):
 * <pre>
 *   byte    ok       0 if no route, 1 if route follows
 *   byte    reason   0 = OK; non-zero = failure code (see {@link com.openrsc.server.model.WorldPathfinder.Reason})
 *   short   count    number of route tiles (0 when ok = 0)
 *   count × { short x; short y }
 * </pre>
 *
 * <p>Worst-case payload at a 1500-tile path is ~6 KB — comfortably under the
 * length-prefix limit of the inbound/outbound packet framing.
 */
public class WorldWalkRouteStruct extends AbstractStruct<OpcodeOut> {
	public boolean ok;
	public int reason;
	public List<Point> route = Collections.emptyList();
}
