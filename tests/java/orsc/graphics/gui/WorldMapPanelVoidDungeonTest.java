package orsc.graphics.gui;

/** Executable floor-selection regression for the Void Dungeon world map. */
public final class WorldMapPanelVoidDungeonTest {
	private static final int GAME_WIDTH = 800;
	private static final int GAME_HEIGHT = 600;

	private WorldMapPanelVoidDungeonTest() {
	}

	public static void main(String[] args) {
		testAutomaticDungeonFloorSelection();
		testNonDungeonUpstairsRemainsSurface();
		testManualFloorTabs();
		System.out.println("Void Dungeon world-map floor selection tests passed.");
	}

	private static void testAutomaticDungeonFloorSelection() {
		WorldMapPanel panel = new WorldMapPanel();
		int[][] targets = {
			{1, 72, 1364},
			{2, 72, 2308},
			{3, 72, 3252},
		};
		for (int[] target : targets) {
			panel.setVisible(false);
			panel.setVisible(true);
			panel.prepareLayout(GAME_WIDTH, GAME_HEIGHT, target[1], target[2]);
			check(panel.getCurrentFloor() == target[0],
				"automatic selection should follow dungeon floor " + target[0]);
			check(panel.getZoomLevel() == 1,
				"dungeon floor " + target[0] + " should use the fitted default zoom");
		}
	}

	private static void testNonDungeonUpstairsRemainsSurface() {
		WorldMapPanel panel = new WorldMapPanel();
		panel.setVisible(true);
		panel.prepareLayout(GAME_WIDTH, GAME_HEIGHT, 300, 1364);
		check(panel.getCurrentFloor() == 0,
			"ordinary upstairs coordinates outside the generated dungeon must use Surface");
	}

	private static void testManualFloorTabs() {
		WorldMapPanel panel = new WorldMapPanel();
		panel.setVisible(true);
		panel.prepareLayout(GAME_WIDTH, GAME_HEIGHT, 72, 3252);
		int[] outWorld = new int[2];
		for (int floor = 0; floor <= 3; floor++) {
			int x = panel.getFloorTabCenterX(floor);
			int y = panel.getFloorTabCenterY(floor);
			check(x >= 0 && y >= 0, "floor " + floor + " should expose a click target");
			panel.pollMouse(x, y, true, GAME_WIDTH, outWorld);
			panel.pollMouse(x, y, false, GAME_WIDTH, outWorld);
			check(panel.getCurrentFloor() == floor,
				"clicking the real floor tab should select floor " + floor);
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
