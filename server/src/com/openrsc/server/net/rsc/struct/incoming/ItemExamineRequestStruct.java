package com.openrsc.server.net.rsc.struct.incoming;

import com.openrsc.server.net.rsc.enums.OpcodeIn;
import com.openrsc.server.net.rsc.struct.AbstractStruct;

/**
 * Inbound payload for per-instance item examine. Carries the inventory slot;
 * the server resolves slot to {@link com.openrsc.server.model.container.Item}
 * so it can render dynamic per-instance description text and reply via the
 * existing {@code SEND_MESSAGE} channel.
 */
public class ItemExamineRequestStruct extends AbstractStruct<OpcodeIn> {
	public int slot;
}
