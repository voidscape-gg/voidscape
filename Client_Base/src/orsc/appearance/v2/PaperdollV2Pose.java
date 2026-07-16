package orsc.appearance.v2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One of the 30 canonical legacy-compatible paperdoll QA states. */
public final class PaperdollV2Pose {
	private static final String[] WALK_DIRECTIONS = {
		"north", "north-west", "west", "south-west", "south", "south-east", "east", "north-east"
	};
	private static final int[] WALK_ACTUAL_DIRECTIONS = {0, 1, 2, 3, 4, 3, 2, 1};
	private static final List<PaperdollV2Pose> CANONICAL = createCanonical();

	private final int ordinal;
	private final String kind;
	private final String direction;
	private final int animationFrame;
	private final int wantedAnimDir;
	private final int actualAnimDir;
	private final boolean mirrorX;
	private final int spriteOffset;

	private PaperdollV2Pose(int ordinal, String kind, String direction, int animationFrame,
		int wantedAnimDir, int actualAnimDir, boolean mirrorX, int spriteOffset) {
		this.ordinal = ordinal;
		this.kind = kind;
		this.direction = direction;
		this.animationFrame = animationFrame;
		this.wantedAnimDir = wantedAnimDir;
		this.actualAnimDir = actualAnimDir;
		this.mirrorX = mirrorX;
		this.spriteOffset = spriteOffset;
	}

	public static List<PaperdollV2Pose> canonical() {
		return CANONICAL;
	}

	public int getOrdinal() { return ordinal; }
	public String getKind() { return kind; }
	public String getDirection() { return direction; }
	public int getAnimationFrame() { return animationFrame; }
	public int getWantedAnimDir() { return wantedAnimDir; }
	public int getActualAnimDir() { return actualAnimDir; }
	public boolean isMirrorX() { return mirrorX; }
	public int getSpriteOffset() { return spriteOffset; }
	public int getLegacyStepFrame() { return "walk".equals(kind) ? animationFrame * 6 : 0; }
	/**
	 * Projected walking-envelope origin used by both the legacy and V2 compositors.
	 * Combat frames expand and center themselves from this origin; supplying the
	 * already-expanded x=4 value here would center them a second time and clip the
	 * combat-A silhouette at the left edge of the Workbench proof canvas.
	 */
	public int getV2DrawX() { return PaperdollV2Pack.PREVIEW_DRAW_X; }
	public int getV2DrawWidth() {
		return "walk".equals(kind) ? PaperdollV2Pack.WALK_WIDTH : PaperdollV2Pack.COMBAT_WIDTH;
	}
	public String getKey() { return kind + "-" + direction + "-" + animationFrame; }

	private static List<PaperdollV2Pose> createCanonical() {
		List<PaperdollV2Pose> states = new ArrayList<>(30);
		for (int wanted = 0; wanted < WALK_DIRECTIONS.length; wanted++) {
			int actual = WALK_ACTUAL_DIRECTIONS[wanted];
			boolean mirror = wanted >= 5;
			for (int frame = 0; frame < 3; frame++) {
				states.add(new PaperdollV2Pose(states.size(), "walk", WALK_DIRECTIONS[wanted], frame,
					wanted, actual, mirror, actual * 3 + frame));
			}
		}
		for (int side = 0; side < 2; side++) {
			String direction = side == 0 ? "combat-a" : "combat-b";
			for (int frame = 0; frame < 3; frame++) {
				states.add(new PaperdollV2Pose(states.size(), "combat", direction, frame,
					2, 5, side == 1, 15 + frame));
			}
		}
		if (states.size() != 30) throw new IllegalStateException("Paperdoll V2 canonical state count changed");
		return Collections.unmodifiableList(states);
	}
}
