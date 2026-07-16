package orsc.appearance.v2;

import orsc.appearance.v2.PaperdollV2Pack.Asset;
import orsc.appearance.v2.PaperdollV2Pack.Channel;
import orsc.appearance.v2.PaperdollV2Pack.RenderStack;
import orsc.graphics.two.GraphicsController;

/** Shared isolated compositor for the Workbench-only Paperdoll V2 proof. */
public final class PaperdollV2Renderer {
	private PaperdollV2Renderer() {
	}

	public static void drawStackSlot(GraphicsController target, RenderStack stack, int paperdollSlot,
		PaperdollV2Pose pose, PaperdollV2Palette palette) {
		if (target == null || stack == null || pose == null || palette == null) {
			throw new IllegalArgumentException("Paperdoll V2 renderer arguments must be non-null");
		}
		drawStackSlot(target, stack, paperdollSlot, pose.getSpriteOffset(),
			PaperdollV2Pack.PREVIEW_DRAW_X, PaperdollV2Pack.PREVIEW_DRAW_Y,
			PaperdollV2Pack.WALK_WIDTH, PaperdollV2Pack.PLAYER_HEIGHT,
			pose.isMirrorX(), 0, palette, 0xffffffff);
	}

	/**
	 * Draws one substituted paperdoll slot into the live scene geometry.
	 *
	 * <p>{@code x/width} describe the projected 128-wide walking envelope. Combat
	 * frames expand to 168/128 of that width and remain centered exactly like the
	 * legacy paperdoll compositor.</p>
	 */
	public static void drawStackSlot(GraphicsController target, RenderStack stack, int paperdollSlot,
		int frame, int x, int y, int width, int height, boolean mirrorX, int topPixelSkew,
		PaperdollV2Palette palette, int actorColourTransform) {
		if (target == null || stack == null || palette == null) {
			throw new IllegalArgumentException("Paperdoll V2 renderer arguments must be non-null");
		}
		drawStackSlot(target, stack, paperdollSlot, frame, x, y, width, height, mirrorX,
			topPixelSkew, palette.getSkin(), palette.getHair(), palette.getFacialHair(),
			palette.getTop(), palette.getBottom(), palette.getPrimary(), palette.getSecondary(),
			actorColourTransform);
	}

	/** Allocation-free primitive palette entry point used by the live Scene compositor. */
	public static void drawStackSlot(GraphicsController target, RenderStack stack, int paperdollSlot,
		int frame, int x, int y, int width, int height, boolean mirrorX, int topPixelSkew,
		int skin, int hair, int facialHair, int top, int bottom, int primary, int secondary,
		int actorColourTransform) {
		if (target == null || stack == null) {
			throw new IllegalArgumentException("Paperdoll V2 renderer arguments must be non-null");
		}
		if (frame < 0 || frame >= PaperdollV2Pack.FRAME_COUNT || width <= 0 || height <= 0) {
			throw new IllegalArgumentException("Paperdoll V2 live geometry is invalid");
		}
		int logicalWidth = frame < 15 ? PaperdollV2Pack.WALK_WIDTH : PaperdollV2Pack.COMBAT_WIDTH;
		int drawWidth = logicalWidth * width / PaperdollV2Pack.WALK_WIDTH;
		int drawX = x - (drawWidth - width) / 2;
		for (int assetIndex = 0; assetIndex < stack.getAssetCount(); assetIndex++) {
			Asset asset = stack.getAsset(assetIndex);
			if (asset.getPaperdollSlot() == paperdollSlot) {
				drawAsset(target, asset, frame, drawX, y, drawWidth, height, mirrorX,
					topPixelSkew, skin, hair, facialHair, top, bottom, primary, secondary,
					actorColourTransform);
			}
		}
	}

	/**
	 * Workbench oracle for one semantic asset kind using the exact live geometry
	 * and tint path. Returns the number of matching assets that were rendered.
	 */
	public static int drawStackAssetKind(GraphicsController target, RenderStack stack,
		String assetKind, int frame, int x, int y, int width, int height, boolean mirrorX,
		int topPixelSkew, int skin, int hair, int facialHair, int top, int bottom,
		int primary, int secondary, int actorColourTransform) {
		if (target == null || stack == null || !"hair".equals(assetKind)) {
			throw new IllegalArgumentException("Paperdoll V2 asset-kind oracle is invalid");
		}
		if (frame < 0 || frame >= PaperdollV2Pack.FRAME_COUNT || width <= 0 || height <= 0) {
			throw new IllegalArgumentException("Paperdoll V2 live geometry is invalid");
		}
		int logicalWidth = frame < 15 ? PaperdollV2Pack.WALK_WIDTH : PaperdollV2Pack.COMBAT_WIDTH;
		int drawWidth = logicalWidth * width / PaperdollV2Pack.WALK_WIDTH;
		int drawX = x - (drawWidth - width) / 2;
		int count = 0;
		for (int assetIndex = 0; assetIndex < stack.getAssetCount(); assetIndex++) {
			Asset asset = stack.getAsset(assetIndex);
			if (assetKind.equals(asset.getKind())) {
				count++;
				drawAsset(target, asset, frame, drawX, y, drawWidth, height, mirrorX,
					topPixelSkew, skin, hair, facialHair, top, bottom, primary, secondary,
					actorColourTransform);
			}
		}
		return count;
	}

	private static void drawAsset(GraphicsController target, Asset asset, int frame, int x, int y,
		int width, int height, boolean mirrorX, int topPixelSkew, int skin, int hair,
		int facialHair, int top, int bottom, int primary, int secondary, int actorColourTransform) {
		for (int channelIndex = 0; channelIndex < asset.getChannelCount(); channelIndex++) {
			Channel channel = asset.getChannel(channelIndex);
			if (channel.isEmpty(frame)) continue;
			target.drawArgbSpriteClipping(channel.getFrame(frame), x, y, width, height,
				mirrorX, topPixelSkew,
				combineTransforms(PaperdollV2Palette.colourTransform(channel.getTintRoleCode(),
					skin, hair, facialHair, top, bottom, primary, secondary), actorColourTransform));
		}
	}

	private static int combineTransforms(int tint, int actor) {
		int alpha = (tint >>> 24 & 0xff) * (actor >>> 24 & 0xff) / 255;
		int red = (tint >>> 16 & 0xff) * (actor >>> 16 & 0xff) / 255;
		int green = (tint >>> 8 & 0xff) * (actor >>> 8 & 0xff) / 255;
		int blue = (tint & 0xff) * (actor & 0xff) / 255;
		return (alpha << 24) | (red << 16) | (green << 8) | blue;
	}
}
