// Generated from content/appearance/registry.yaml and active/reserved Look manifests. Do not edit by hand.
package com.openrsc.server.appearance;

public final class GeneratedLookPresets {
	private static final int[] LEGACY_SELECTABLE_HEAD_IDS = new int[] {1, 4, 6, 7, 8};
	private static final Entry[] MANAGED_LOOKS = new Entry[] {new Entry(247, "future_mullet_mustache", "Mullet + mustache", false, 8)};

	private GeneratedLookPresets() {}

	public static boolean isSelectableHead(int appearanceId) {
		for (int legacyId : LEGACY_SELECTABLE_HEAD_IDS) {
			if (legacyId == appearanceId) return true;
		}
		Entry managed = findManaged(appearanceId);
		return managed != null && managed.selectable;
	}

	public static Entry findManaged(int appearanceId) {
		for (Entry entry : MANAGED_LOOKS) {
			if (entry.appearanceId == appearanceId) return entry;
		}
		return null;
	}

	public static int[] selectableHeadIds() {
		int managedCount = 0;
		for (Entry entry : MANAGED_LOOKS) {
			if (entry.selectable) managedCount++;
		}
		int[] result = new int[LEGACY_SELECTABLE_HEAD_IDS.length + managedCount];
		System.arraycopy(LEGACY_SELECTABLE_HEAD_IDS, 0, result, 0, LEGACY_SELECTABLE_HEAD_IDS.length);
		int offset = LEGACY_SELECTABLE_HEAD_IDS.length;
		for (Entry entry : MANAGED_LOOKS) {
			if (entry.selectable) result[offset++] = entry.appearanceId;
		}
		return result;
	}

	public static Entry[] managedLooks() { return MANAGED_LOOKS.clone(); }

	public static final class Entry {
		public final int appearanceId;
		public final String key;
		public final String name;
		public final boolean selectable;
		public final int retroFallbackAppearanceId;

		private Entry(int appearanceId, String key, String name, boolean selectable, int retroFallbackAppearanceId) {
			this.appearanceId = appearanceId;
			this.key = key;
			this.name = name;
			this.selectable = selectable;
			this.retroFallbackAppearanceId = retroFallbackAppearanceId;
		}
	}
}
