package orsc.appearance.v2;

import java.io.IOException;
import java.util.Locale;

/**
 * Digest-independent live anatomy contract for the PC-only Paperdoll V2 proof.
 *
 * <p>The strict pack intentionally remains schema v1, so catalog stacks expose
 * their base profile through stable {@code male_}/{@code female_} ids and asset
 * prefixes. The two original proof stacks predate that convention and remain
 * explicitly pinned to the male control anatomy.</p>
 */
public enum PaperdollV2BaseProfile {
	MALE("male", 8, 2, 3),
	FEMALE("female", 8, 5, 3);

	private final String id;
	private final int headAppearanceId;
	private final int bodyAppearanceId;
	private final int legsAppearanceId;

	PaperdollV2BaseProfile(String id, int headAppearanceId, int bodyAppearanceId,
		int legsAppearanceId) {
		this.id = id;
		this.headAppearanceId = headAppearanceId;
		this.bodyAppearanceId = bodyAppearanceId;
		this.legsAppearanceId = legsAppearanceId;
	}

	public String getId() { return id; }
	public int getHeadAppearanceId() { return headAppearanceId; }
	public int getBodyAppearanceId() { return bodyAppearanceId; }
	public int getLegsAppearanceId() { return legsAppearanceId; }

	public int expectedAppearanceId(int paperdollSlot) {
		switch (paperdollSlot) {
			case 0: return headAppearanceId;
			case 1: return bodyAppearanceId;
			case 2: return legsAppearanceId;
			default:
				throw new IllegalArgumentException("No base-profile appearance for slot " + paperdollSlot);
		}
	}

	public void applyTo(int[] layerAnimation) {
		if (layerAnimation == null || layerAnimation.length < 3) {
			throw new IllegalArgumentException("Paperdoll layer array must contain base slots 0/1/2");
		}
		layerAnimation[0] = headAppearanceId;
		layerAnimation[1] = bodyAppearanceId;
		layerAnimation[2] = legsAppearanceId;
	}

	public static PaperdollV2BaseProfile parse(String value) throws IOException {
		String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
		for (PaperdollV2BaseProfile profile : values()) {
			if (profile.id.equals(normalized)) return profile;
		}
		throw new IOException("Unknown Paperdoll V2 base profile: " + value);
	}

	public static PaperdollV2BaseProfile derive(PaperdollV2Pack.RenderStack stack,
		PaperdollV2BaseProfile ambiguousFallback) throws IOException {
		if (stack == null) throw new IOException("Paperdoll V2 stack is missing");
		PaperdollV2BaseProfile inferred = profilePrefix(stack.getId());
		for (PaperdollV2Pack.Asset asset : stack.getAssets()) {
			PaperdollV2BaseProfile assetProfile = profilePrefix(asset.getId());
			if (assetProfile == null) continue;
			if (inferred != null && inferred != assetProfile) {
				throw new IOException("Paperdoll V2 stack " + stack.getId()
					+ " mixes male and female base-profile assets");
			}
			inferred = assetProfile;
		}
		if (inferred != null) return inferred;
		if (ambiguousFallback != null) return ambiguousFallback;
		if ("control".equals(stack.getId()) || "rare_hair".equals(stack.getId())) return MALE;
		throw new IOException("Paperdoll V2 stack " + stack.getId()
			+ " does not declare a male/female base profile");
	}

	private static PaperdollV2BaseProfile profilePrefix(String id) {
		if (id == null) return null;
		if (id.startsWith("male_")) return MALE;
		if (id.startsWith("female_")) return FEMALE;
		return null;
	}
}
