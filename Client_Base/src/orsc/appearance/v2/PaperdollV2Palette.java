package orsc.appearance.v2;

/** Semantic colour transforms applied to authored ARGB channels. */
public final class PaperdollV2Palette {
	private final int skin;
	private final int hair;
	private final int facialHair;
	private final int top;
	private final int bottom;
	private final int primary;
	private final int secondary;

	public PaperdollV2Palette(int skin, int hair, int facialHair, int top, int bottom,
		int primary, int secondary) {
		this.skin = rgb(skin);
		this.hair = rgb(hair);
		this.facialHair = rgb(facialHair);
		this.top = rgb(top);
		this.bottom = rgb(bottom);
		this.primary = rgb(primary);
		this.secondary = rgb(secondary);
	}

	public int colourTransform(String tintRole) {
		if ("fixed".equals(tintRole)) return colourTransform(0);
		if ("skin".equals(tintRole)) return colourTransform(1);
		if ("hair".equals(tintRole)) return colourTransform(2);
		if ("facial-hair".equals(tintRole)) return colourTransform(3);
		if ("top".equals(tintRole)) return colourTransform(4);
		if ("bottom".equals(tintRole)) return colourTransform(5);
		if ("primary".equals(tintRole)) return colourTransform(6);
		if ("secondary".equals(tintRole)) return colourTransform(7);
		throw new IllegalArgumentException("Unknown Paperdoll V2 tint role: " + tintRole);
	}

	public int colourTransform(int tintRoleCode) {
		return colourTransform(tintRoleCode, skin, hair, facialHair, top, bottom, primary, secondary);
	}

	static int colourTransform(int tintRoleCode, int skin, int hair, int facialHair,
		int top, int bottom, int primary, int secondary) {
		switch (tintRoleCode) {
			case 0: return 0xffffffff;
			case 1: return transform(skin);
			case 2: return transform(hair);
			case 3: return transform(facialHair);
			case 4: return transform(top);
			case 5: return transform(bottom);
			case 6: return transform(primary);
			case 7: return transform(secondary);
			default: throw new IllegalArgumentException("Unknown Paperdoll V2 tint role code: "
				+ tintRoleCode);
		}
	}

	public int getSkin() { return skin; }
	public int getHair() { return hair; }
	public int getFacialHair() { return facialHair; }
	public int getTop() { return top; }
	public int getBottom() { return bottom; }
	public int getPrimary() { return primary; }
	public int getSecondary() { return secondary; }

	private static int rgb(int value) {
		if ((value & 0xff000000) != 0) throw new IllegalArgumentException("Paperdoll V2 colour is not RGB: " + value);
		return value & 0x00ffffff;
	}

	private static int transform(int rgb) {
		return 0xff000000 | rgb;
	}
}
