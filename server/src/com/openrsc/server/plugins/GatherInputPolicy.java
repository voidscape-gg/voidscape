package com.openrsc.server.plugins;

/** Packet policy for busy gathering interactions. */
public final class GatherInputPolicy {

	private GatherInputPolicy() {
	}

	public static boolean shouldInterruptBusyWalk(
		boolean batchingEnabled, boolean gatherRepeatSupported, boolean pointWalk
	) {
		return batchingEnabled && (pointWalk || !gatherRepeatSupported);
	}
}
