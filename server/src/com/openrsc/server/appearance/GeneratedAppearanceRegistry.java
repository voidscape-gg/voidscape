// Generated from content/appearance/registry.yaml. Do not edit by hand.
package com.openrsc.server.appearance;

import com.openrsc.server.avatargenerator.AvatarFormat.AnimationDef;

public final class GeneratedAppearanceRegistry {
	public static final int COWBOY_HAT_APPEARANCE_ID = 245;

	private static final Entry COWBOY_HAT = new Entry(245, "cowboy_hat", new AnimationDef("cowboyhat", "equipment", 0, 0, 0, true, false, 1890), 18, 5);

	private GeneratedAppearanceRegistry() {}

	public static Entry findAuthentic(int appearanceId) {
		switch (appearanceId) {
			case COWBOY_HAT_APPEARANCE_ID: return COWBOY_HAT;
			default: return null;
		}
	}

	public static boolean isManaged(int appearanceId) { return findAuthentic(appearanceId) != null; }
	public static Entry[] authenticEntries() { return new Entry[] {COWBOY_HAT}; }

	public static final class Entry {
		public final int appearanceId;
		public final String key;
		public final AnimationDef definition;
		public final int frameCount;
		public final int paperdollSlot;

		private Entry(int appearanceId, String key, AnimationDef definition, int frameCount, int paperdollSlot) {
			this.appearanceId = appearanceId;
			this.key = key;
			this.definition = definition;
			this.frameCount = frameCount;
			this.paperdollSlot = paperdollSlot;
		}
	}
}
