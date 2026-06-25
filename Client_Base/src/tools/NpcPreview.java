package tools;

import com.openrsc.client.entityhandling.EntityHandler;
import com.openrsc.client.entityhandling.defs.NPCDef;
import com.openrsc.client.entityhandling.defs.extras.AnimationDef;
import com.openrsc.client.model.Sprite;
import orsc.Config;
import orsc.graphics.two.MudClientGraphics;
import orsc.mudclient;
import orsc.multiclient.ClientPort;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.ByteBuffer;

/**
 * Voidscape NPC sprite previewer (dev tool).
 *
 * Renders an NPC EXACTLY how the in-game client does: it reuses the real {@link MudClientGraphics}
 * software rasterizer, the {@link EntityHandler} defs, and the {@link Sprite} archive decoder, and
 * faithfully replicates {@code mudclient.drawNPC}'s frame-selection (direction octants, the
 * {0,1,2,1} walk cycle, the combat A/B 8-phase cycle, mirroring, and the 12-layer composite).
 *
 * Use it to iterate on new NPC sprites/animations without the server+client+login loop:
 *   - pick any NPC id, rotate the camera, scrub direction, play the walk + combat animations live
 *   - tweak walkModel / combatModel / combatSprite / camera1 / camera2 live to tune animation SPEED
 *     and billboard SIZE before committing them to EntityHandler.java
 *   - re-pack candidate frames into Authentic_Sprites.orsc and hit "Reload sprites" to see them
 *
 * Run:  cd Client_Base && java -cp Open_RSC_Client.jar tools.NpcPreview [cacheDir]
 */
public final class NpcPreview {

	// Copied verbatim from mudclient (the render source of truth).
	private static final int[] WALK = {0, 1, 2, 1};
	private static final int[] COMBAT_A = {0, 1, 2, 1, 0, 0, 0, 0};
	private static final int[] COMBAT_B = {0, 0, 0, 0, 0, 1, 2, 1};
	private static final int[][] LAYER = {
		{11, 2, 9, 7, 1, 6, 10, 0, 5, 8, 3, 4},
		{11, 2, 9, 7, 1, 6, 10, 0, 5, 8, 3, 4},
		{11, 3, 2, 9, 7, 1, 6, 10, 0, 5, 8, 4},
		{3, 4, 2, 9, 7, 1, 6, 10, 8, 11, 0, 5},
		{3, 4, 2, 9, 7, 1, 6, 10, 8, 11, 0, 5},
		{4, 3, 2, 9, 7, 1, 6, 10, 8, 11, 0, 5},
		{11, 4, 2, 9, 7, 1, 6, 10, 0, 5, 8, 3},
		{11, 2, 9, 7, 1, 6, 10, 0, 5, 8, 4, 3},
	};

	private static final int BUF_W = 520, BUF_H = 640;
	private static final int BG = 0x282830;

	private final MudClientGraphics g;
	private final BufferedImage image = new BufferedImage(BUF_W, BUF_H, BufferedImage.TYPE_INT_RGB);

	private static final class HeadlessClientPort implements ClientPort {
		private final String cacheLocation;

		HeadlessClientPort(String cacheLocation) {
			this.cacheLocation = new File(cacheLocation).getPath() + File.separator;
		}

		@Override public boolean drawLoading(int i) { return true; }
		@Override public void showLoadingProgress(int percentage, String status) {}
		@Override public void initListeners() {}
		@Override public void crashed() {}
		@Override public void drawLoadingError() {}
		@Override public void drawOutOfMemoryError() {}
		@Override public boolean isDisplayable() { return true; }
		@Override public void drawTextBox(String line2, byte var2, String line1) {}
		@Override public void initGraphics() {}
		@Override public void draw() {}
		@Override public void close() {}
		@Override public String getCacheLocation() { return cacheLocation; }
		@Override public Sprite getBattery(int level) { return Sprite.getUnknownSprite(18, 18); }
		@Override public int getBatteryPercent() { return 100; }
		@Override public boolean getBatteryCharging() { return false; }
		@Override public Sprite getConnectivity(int level) { return Sprite.getUnknownSprite(18, 18); }
		@Override public String getConnectivityText() { return ""; }
		@Override public void resized() {}
		@Override public Sprite getSpriteFromByteArray(ByteArrayInputStream input) {
			try {
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				byte[] buffer = new byte[4096];
				int read;
				while ((read = input.read(buffer)) != -1) {
					out.write(buffer, 0, read);
				}
				return Sprite.unpack(ByteBuffer.wrap(out.toByteArray()));
			} catch (Exception e) {
				return Sprite.getUnknownSprite(18, 18);
			}
		}
		@Override public void playSound(byte[] soundData, int offset, int dataLength) {}
		@Override public void stopSoundPlayer() {}
		@Override public void drawKeyboard() {}
		@Override public void closeKeyboard() {}
		@Override public boolean openUrl(String url) { return false; }
		@Override public void setTitle(String title) {}
		@Override public void setIconImage(String serverName) {}
	}

	// live state
	private int npcId = 852;            // Void Colossus
	private int rsDir = 4;              // SOUTH (front-facing)
	private int cameraRotation = 128;   // default in-game camera
	private int stepFrame = 0;          // advances while "walking"
	private int frameCounter = 0;       // global counter that drives combat phase
	private int combat = 0;             // 0 none, 1 A, 2 B
	private int zoom = 100;             // % of camera1/camera2
	private boolean animating = true;
	private boolean entitySetMode = false;
	private boolean onionSkin = false;
	private int animationId = 0;

	// combat-scene mode: NPC vs an opponent at combat distance, to dial facing + distance + lunge
	private boolean combatScene = false;
	private int gap = 130;              // screen px between the two figures' centres (combat distance)
	private int overlay = 0;            // lunge progress (0..~150), animated during combat
	private int opponentId = 11;        // Man (player stand-in)

	// per-NPC overrides (initialised from the def, tweakable live)
	private int walkModel, combatModel, combatSprite, camera1, camera2;

	private final RenderPanel panel = new RenderPanel();
	private final JLabel info = new JLabel();

	private static final class EntityFrame {
		final int variant;
		final boolean mirror;

		EntityFrame(int variant, boolean mirror) {
			this.variant = variant;
			this.mirror = mirror;
		}
	}

	private NpcPreview() {
		g = new MudClientGraphics(BUF_W, BUF_H, 12000);
		loadEntitySprites();
		applyDefDefaults();
	}

	/** Replicates mudclient.loadEntitiesAuthentic: assign each animation its archive number + load frames. */
	private void loadEntitySprites() {
		int animationNumber = 0;
		outer:
		for (int ai = 0; ai < EntityHandler.animationCount(); ai++) {
			String name = EntityHandler.getAnimationDef(ai).getName();
			for (int j = 0; j < ai; j++) {
				if (EntityHandler.getAnimationDef(j).getName().equalsIgnoreCase(name)) {
					EntityHandler.getAnimationDef(ai).number = EntityHandler.getAnimationDef(j).number;
					continue outer;
				}
			}
			for (int k = 0; k < 15; k++) g.loadSprite(animationNumber + k, "entity");
			if (EntityHandler.getAnimationDef(ai).hasA())
				for (int k = 0; k < 3; k++) g.loadSprite(animationNumber + 15 + k, "entity");
			if (EntityHandler.getAnimationDef(ai).hasF())
				for (int k = 0; k < 9; k++) g.loadSprite(animationNumber + 18 + k, "entity");
			EntityHandler.getAnimationDef(ai).number = animationNumber;
			animationNumber += 27;
			if (animationNumber == 1998) animationNumber = 3300;
		}
		System.out.println("Loaded entity sprites up to number " + animationNumber);
	}

	private void applyDefDefaults() {
		NPCDef def = EntityHandler.getNpcDef(npcId);
		walkModel = def.getWalkModel();
		combatModel = def.getCombatModel();
		combatSprite = def.getCombatSprite();
		camera1 = def.getCamera1();
		camera2 = def.getCamera2();
		if (def.getSprite(0) >= 0) animationId = def.getSprite(0);
		// default zoom auto-fits the figure to ~85% of the buffer height
		zoom = Math.max(10, Math.min(250, (int) (BUF_H * 0.85 / Math.max(1, camera2) * 100)));
	}

	/** Render the whole frame: a single NPC, or (combat-scene mode) the NPC vs an opponent. */
	private void renderNpc() {
		if (entitySetMode) {
			renderEntitySet();
			return;
		}
		java.util.Arrays.fill(g.pixelData, BG);
		NPCDef def = EntityHandler.getNpcDef(npcId);
		if (combatScene) {
			// Both fighters stand on adjacent tiles facing each other; the engine LOCKS combat to a
			// side view (var11=2), so this is the true in-combat composition. COMBAT_A uses the
			// unmirrored right-facing source strip; COMBAT_B mirrors that same strip to face left.
			// This matches vanilla Man frames and the server's sprite 8/9 combat assignment.
			NPCDef opp = EntityHandler.getNpcDef(opponentId);
			int oppCam1 = opp.getCamera1(), oppCam2 = opp.getCamera2();
			// One shared zoom so the NPC's size RELATIVE to the player is faithful (the bigger fighter
			// looks bigger). Scale so the taller fighter is ~62% of the buffer height.
			int sZoom = (int) (BUF_H * 0.62 / Math.max(camera2, oppCam2) * 100);
			int half = gap / 2;
			drawFigure(def, 0, 1, camera1, camera2, walkModel, combatModel, combatSprite,
				sZoom, BUF_W / 2 - half, overlay);          // NPC, combat-A, right-facing source
			drawFigure(opp, 0, 2, oppCam1, oppCam2, opp.getWalkModel(), opp.getCombatModel(),
				opp.getCombatSprite(), sZoom, BUF_W / 2 + half, overlay); // opponent, combat-B, mirrored left
		} else {
			drawFigure(def, rsDir, combat, camera1, camera2, walkModel, combatModel, combatSprite,
				zoom, BUF_W / 2, combat != 0 ? overlay : 0);
		}
		image.setRGB(0, 0, BUF_W, BUF_H, g.pixelData, 0, BUF_W);
	}

	private void renderEntitySet() {
		Graphics2D gr = image.createGraphics();
		try {
			gr.setColor(new Color(BG));
			gr.fillRect(0, 0, BUF_W, BUF_H);
			AnimationDef ad = EntityHandler.getAnimationDef(animationId);
			int width = Math.max(1, 160 * zoom / 100);
			int height = Math.max(1, 220 * zoom / 100);
			if (onionSkin) {
				drawEntityFrame(gr, ad, entityFrame(-1), width, height, 0.28f);
				drawEntityFrame(gr, ad, entityFrame(1), width, height, 0.28f);
			}
			drawEntityFrame(gr, ad, entityFrame(0), width, height, 1.0f);
			gr.setComposite(AlphaComposite.SrcOver);
			gr.setColor(new Color(220, 214, 230));
			gr.drawString(ad.getName() + " [" + animationId + "]", 12, 22);
		} finally {
			gr.dispose();
		}
	}

	private void drawEntityFrame(Graphics2D gr, AnimationDef ad, EntityFrame frame, int width, int height, float alpha) {
		BufferedImage layer = renderEntityFrameImage(ad, frame, width, height);
		gr.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
		gr.drawImage(layer, 0, 0, null);
	}

	private BufferedImage renderEntityFrameImage(AnimationDef ad, EntityFrame frame, int width, int height) {
		java.util.Arrays.fill(g.pixelData, BG);
		Sprite sprite = g.spriteSelect(ad, frame.variant);
		g.drawSpriteClipping(sprite, (BUF_W - width) / 2, (BUF_H - height) / 2, width, height,
			ad.getCharColour(), 0, ad.getBlueMask(), frame.mirror, 0, 1);
		BufferedImage layer = new BufferedImage(BUF_W, BUF_H, BufferedImage.TYPE_INT_ARGB);
		int[] argb = new int[BUF_W * BUF_H];
		for (int i = 0; i < g.pixelData.length; i++) {
			int rgb = g.pixelData[i] & 0xFFFFFF;
			argb[i] = rgb == (BG & 0xFFFFFF) ? 0 : 0xFF000000 | rgb;
		}
		layer.setRGB(0, 0, BUF_W, BUF_H, argb, 0, BUF_W);
		return layer;
	}

	private EntityFrame entityFrame(int phaseOffset) {
		AnimationDef ad = EntityHandler.getAnimationDef(animationId);
		int var11 = 7 & (rsDir + (cameraRotation + 16) / 32);
		boolean mirror = false;
		int var13 = var11;
		if (var11 == 5) { mirror = true; var13 = 3; }
		else if (var11 == 6) { mirror = true; var13 = 2; }
		else if (var11 == 7) { mirror = true; var13 = 1; }

		int variant = WALK[(Math.max(0, stepFrame + phaseOffset * Math.max(1, walkModel)) / Math.max(1, walkModel)) % 4]
			+ var13 * 3;
		if (combat == 1 && ad.hasA()) {
			mirror = false;
			int cm = Math.max(2, combatModel);
			variant = 15 + COMBAT_A[(Math.max(0, frameCounter + phaseOffset * cm) / (cm - 1)) % 8];
		} else if (combat == 2 && ad.hasA()) {
			mirror = true;
			int cm = Math.max(1, combatModel);
			variant = 15 + COMBAT_B[(Math.max(0, frameCounter + phaseOffset * cm) / cm) % 8];
		} else if (mirror && var13 >= 1 && var13 <= 3 && ad.hasF()) {
			variant += 15;
		}
		return new EntityFrame(variant, mirror);
	}

	/** Faithful replica of mudclient.drawNPC frame-selection + 12-layer composite, for one figure
	 *  centred horizontally at screenCx. combatMode: 0 none, 1 COMBAT_A, 2 COMBAT_B. */
	private void drawFigure(NPCDef def, int dir, int combatMode, int cam1, int cam2,
							int wmOverride, int cmOverride, int csOverride,
							int zoomPct, int screenCx, int overlayMovement) {
		int width1 = Math.max(1, cam1 * zoomPct / 100);
		int height = Math.max(1, cam2 * zoomPct / 100);
		int x = screenCx - width1 / 2;
		int y = (BUF_H - height) / 2;

		int var11 = 7 & (dir + (cameraRotation + 16) / 32);
		boolean var12 = false;
		int var13 = var11;
		if (var11 == 5) { var12 = true; var13 = 3; }
		else if (var11 == 6) { var12 = true; var13 = 2; }
		else if (var11 == 7) { var12 = true; var13 = 1; }

		int wm = Math.max(1, wmOverride);
		int var14 = WALK[(stepFrame / wm) % 4] + var13 * 3;
		if (combatMode == 1) {
			var12 = false; var13 = 5; var11 = 2;
			x -= overlayMovement * csOverride / 100;
			int cm = Math.max(2, cmOverride);
			var14 = var13 * 3 + COMBAT_A[(frameCounter / (cm - 1)) % 8];
		} else if (combatMode == 2) {
			var13 = 5; var11 = 2; var12 = true;
			x += csOverride * overlayMovement / 100;
			int cm = Math.max(1, cmOverride);
			var14 = COMBAT_B[(frameCounter / cm) % 8] + var13 * 3;
		}

		for (int var15 = 0; var15 < 12; var15++) {
			int var16 = LAYER[var11][var15];
			int animID = def.getSprite(var16);
			if (animID < 0) continue;
			AnimationDef ad = EntityHandler.getAnimationDef(animID);
			int variant = var14;
			if (var12 && var13 >= 1 && var13 <= 3 && ad.hasF()) variant = var14 + 15;
			if (var13 != 5 || ad.hasA()) {
				Sprite sprite = g.spriteSelect(ad, variant);
				int s1 = sprite.getSomething1();
				int s2 = sprite.getSomething2();
				int s3 = g.spriteSelect(EntityHandler.getAnimationDef(animID), 0).getSomething1();
				if (s1 != 0 && s2 != 0 && s3 != 0) {
					int spriteWidth = (s1 * width1) / s3;
					int xOff = -(spriteWidth - width1) / 2;
					int colorVariant = ad.getCharColour();
					int baseColor = 0;
					if (colorVariant == 1) { baseColor = def.getSkinColour(); colorVariant = def.getHairColour(); }
					else if (animID >= 230 && Config.S_WANT_CUSTOM_SPRITES) { baseColor = def.getSkinColour(); }
					else if (colorVariant != 2) { if (colorVariant == 3) { baseColor = def.getSkinColour(); colorVariant = def.getBottomColour(); } }
					else { colorVariant = def.getTopColour(); baseColor = def.getSkinColour(); }
					g.drawSpriteClipping(sprite, xOff + x, y, spriteWidth, height, colorVariant, baseColor, 0, var12, 0, 1);
				}
			}
		}
	}

	private final class RenderPanel extends JPanel {
		RenderPanel() { setPreferredSize(new Dimension(BUF_W, BUF_H)); setBackground(Color.BLACK); }
		@Override protected void paintComponent(Graphics gr) {
			super.paintComponent(gr);
			renderNpc();
			gr.drawImage(image, (getWidth() - BUF_W) / 2, (getHeight() - BUF_H) / 2, null);
		}
	}

	private void buildUi() {
		JFrame f = new JFrame("Voidscape NPC Previewer");
		f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		f.setLayout(new BorderLayout());
		f.add(panel, BorderLayout.CENTER);

		JPanel controls = new JPanel(new GridLayout(0, 1, 0, 2));
		controls.setPreferredSize(new Dimension(300, BUF_H));

		ButtonGroup modeGroup = new ButtonGroup();
		JPanel modeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		modeRow.add(new JLabel("Mode:"));
		modeRow.add(radio("NPC", true, modeGroup, () -> entitySetMode = false));
		modeRow.add(radio("Entity set", false, modeGroup, () -> entitySetMode = true));
		controls.add(modeRow);

		controls.add(labeled("NPC id", spinner(npcId, 0, EntityHandler.npcCount() - 1, v -> {
			npcId = v;
			applyDefDefaults();
			syncSpinners();
		})));
		animationSp = spinner(animationId, 0, EntityHandler.animationCount() - 1, v -> animationId = v);
		controls.add(labeled("AnimationDef id (entity set mode)", animationSp));
		controls.add(labeled("Direction (rsDir 0-7: N,NW,W,SW,S,SE,E,NE)", slider(0, 7, rsDir, v -> rsDir = v)));
		controls.add(labeled("Camera rotation (0-255)", slider(0, 255, cameraRotation, v -> cameraRotation = v)));
		controls.add(labeled("Zoom %", slider(20, 250, zoom, v -> zoom = v)));

		ButtonGroup bg = new ButtonGroup();
		JPanel crow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		crow.add(new JLabel("Combat:"));
		crow.add(radio("none", true, bg, () -> combat = 0));
		crow.add(radio("A", false, bg, () -> combat = 1));
		crow.add(radio("B", false, bg, () -> combat = 2));
		controls.add(crow);

		JCheckBox anim = new JCheckBox("Animate", animating);
		anim.addActionListener(e -> animating = anim.isSelected());
		controls.add(anim);
		JCheckBox onion = new JCheckBox("Onion skin (entity set mode)", onionSkin);
		onion.addActionListener(e -> onionSkin = onion.isSelected());
		controls.add(onion);
		controls.add(labeled("Step frame (manual when paused)", slider(0, 240, stepFrame, v -> stepFrame = v)));

		// --- combat-scene controls: see the actual fight composition ---
		JCheckBox scene = new JCheckBox("Combat scene (NPC vs opponent, side-locked)", combatScene);
		scene.addActionListener(e -> combatScene = scene.isSelected());
		controls.add(scene);
		controls.add(labeled("Combat distance (gap px between fighters)", slider(20, 420, gap, v -> gap = v)));
		controls.add(labeled("Lunge (overlay; auto-animates)", slider(0, 200, overlay, v -> overlay = v)));
		controls.add(labeled("Opponent id (player stand-in)", spinner(opponentId, 0, EntityHandler.npcCount() - 1, v -> opponentId = v)));

		walkSp = spinner(walkModel, 1, 200, v -> walkModel = v);
		combatSp = spinner(combatModel, 2, 200, v -> combatModel = v);
		combatSpriteSp = spinner(combatSprite, 0, 300, v -> combatSprite = v);
		cam1Sp = spinner(camera1, 10, 3000, v -> camera1 = v);
		cam2Sp = spinner(camera2, 10, 3000, v -> camera2 = v);
		controls.add(labeled("walkModel (bigger = slower legs)", walkSp));
		controls.add(labeled("combatModel (bigger = slower swing)", combatSp));
		controls.add(labeled("combatSprite (lunge)", combatSpriteSp));
		controls.add(labeled("camera1 (width)", cam1Sp));
		controls.add(labeled("camera2 (height)", cam2Sp));

		JButton reload = new JButton("Reload sprites (re-read .orsc)");
		reload.addActionListener(e -> { loadEntitySprites(); panel.repaint(); });
		controls.add(reload);
		controls.add(info);

		f.add(new JScrollPane(controls), BorderLayout.EAST);
		f.pack();
		f.setLocationRelativeTo(null);
		f.setVisible(true);

		new Timer(20, e -> {
			if (animating) {
				stepFrame = (stepFrame + 1) % 100000;
				frameCounter = (frameCounter + 1) % 100000;
				// triangle-wave lunge pulse (0 -> ~100 -> 0) so the combat lunge is visible
				overlay = Math.abs((frameCounter % 60) - 30) * 100 / 30;
			}
			NPCDef d = EntityHandler.getNpcDef(npcId);
			AnimationDef ad = EntityHandler.getAnimationDef(animationId);
			info.setText("<html>" + (entitySetMode ? "Entity set: " + ad.getName() + " (anim " + animationId + ")" : d.getName() + " (id " + npcId + ")")
				+ "<br>wm=" + walkModel
				+ " cm=" + combatModel + " cs=" + combatSprite + " cam=" + camera1 + "x" + camera2
				+ (entitySetMode ? "<br>variant=" + entityFrame(0).variant + " onion=" + onionSkin : "")
				+ (combatScene ? "<br>gap=" + gap + " overlay=" + overlay : "") + "</html>");
			panel.repaint();
		}).start();
	}

	private JSpinner walkSp, combatSp, combatSpriteSp, cam1Sp, cam2Sp, animationSp;
	private void syncSpinners() {
		walkSp.setValue(walkModel); combatSp.setValue(combatModel); combatSpriteSp.setValue(combatSprite);
		cam1Sp.setValue(camera1); cam2Sp.setValue(camera2);
		if (animationSp != null) animationSp.setValue(animationId);
	}

	// --- tiny Swing helpers ---
	private interface IntSink { void set(int v); }
	private static JPanel labeled(String text, JComponent c) {
		JPanel p = new JPanel(new BorderLayout());
		p.add(new JLabel(text), BorderLayout.NORTH);
		p.add(c, BorderLayout.CENTER);
		return p;
	}
	private static JSlider slider(int min, int max, int val, IntSink sink) {
		JSlider s = new JSlider(min, max, val);
		s.addChangeListener(e -> sink.set(s.getValue()));
		return s;
	}
	private static JSpinner spinner(int val, int min, int max, IntSink sink) {
		JSpinner s = new JSpinner(new SpinnerNumberModel(val, min, max, 1));
		s.addChangeListener(e -> sink.set((Integer) s.getValue()));
		return s;
	}
	private static JRadioButton radio(String text, boolean sel, ButtonGroup bg, Runnable onSel) {
		JRadioButton r = new JRadioButton(text, sel);
		r.addActionListener(e -> onSel.run());
		bg.add(r);
		return r;
	}

	/** Headless verification: render frames for an npc to PNGs (no display needed). */
	private void dump(int id, String outDir) throws Exception {
		new java.io.File(outDir).mkdirs();
		npcId = id; applyDefDefaults();
		// 8 direction octants (neutral, default camera)
		combat = 0; cameraRotation = 128; stepFrame = 0;
		for (int d = 0; d < 8; d++) { rsDir = d; save(outDir + "/dir" + d + ".png"); }
		// front walk cycle phases
		rsDir = 4; for (int p = 0; p < 4; p++) { stepFrame = p * walkModel; save(outDir + "/walk" + p + ".png"); }
		// combat A + B phases
		stepFrame = 0;
		for (int c = 1; c <= 2; c++) { combat = c; for (int p = 0; p < 8; p++) { frameCounter = p * Math.max(1, combatModel); save(outDir + "/combat" + (c == 1 ? "A" : "B") + p + ".png"); } }
		// combat-scene composition (NPC vs opponent) at a few lunge values
		combatScene = true; cameraRotation = 128;
		for (int ov : new int[]{0, 50, 100}) { overlay = ov; frameCounter = 2 * Math.max(1, combatModel); save(outDir + "/scene_ov" + ov + ".png"); }
		combatScene = false;
		// camera ORBIT: idle NPC facing south, the player walks around it (cameraRotation 0..255).
		// This is the "pov from every angle" view — it should read as one figure rotating in 3D.
		combat = 0; stepFrame = 0; rsDir = 4;
		for (int cr = 0; cr < 256; cr += 32) { cameraRotation = cr; save(outDir + "/cam" + cr + ".png"); }
		System.out.println("dumped frames to " + outDir);
	}

	private void dumpEntitySet(int id, String outDir) throws Exception {
		new java.io.File(outDir).mkdirs();
		entitySetMode = true;
		animationId = id;
		combatScene = false;
		cameraRotation = 128;
		for (int d = 0; d < 8; d++) {
			rsDir = d;
			combat = 0;
			for (int p = 0; p < 4; p++) {
				stepFrame = p * Math.max(1, walkModel);
				save(outDir + "/entity_dir" + d + "_walk" + p + ".png");
			}
		}
		for (int c = 1; c <= 2; c++) {
			combat = c;
			for (int p = 0; p < 8; p++) {
				frameCounter = p * Math.max(1, combatModel);
				save(outDir + "/entity_combat" + (c == 1 ? "A" : "B") + p + ".png");
			}
		}
		onionSkin = true;
		combat = 0;
		rsDir = 4;
		stepFrame = Math.max(1, walkModel);
		save(outDir + "/entity_onion.png");
		onionSkin = false;
		System.out.println("dumped entity set frames to " + outDir);
	}

	private void save(String path) throws Exception {
		renderNpc();
		javax.imageio.ImageIO.write(image, "png", new java.io.File(path));
	}

	public static void main(String[] args) throws Exception {
		String cache = args.length > 0 ? args[0] : "Cache";
		Config.F_CACHE_DIR = cache;
		Config.S_WANT_CUSTOM_SPRITES = false;
		mudclient.clientPort = new HeadlessClientPort(cache);
		System.out.println("Cache dir: " + new java.io.File(cache).getAbsolutePath());
		EntityHandler.load(true);
		NpcPreview app = new NpcPreview();
		if (args.length >= 2 && args[1].equals("--debug")) {
			System.out.println("animationCount=" + EntityHandler.animationCount());
			for (int i = 0; i < EntityHandler.animationCount(); i++) {
				AnimationDef ad = EntityHandler.getAnimationDef(i);
				if (ad.getName().toLowerCase().contains("colossus") || ad.getName().toLowerCase().contains("void"))
					System.out.println("  anim idx=" + i + " name=" + ad.getName() + " number=" + ad.number + " hasA=" + ad.hasA());
			}
			for (int id : new int[]{852, 853}) {
				NPCDef def = EntityHandler.getNpcDef(id);
				int animID = def.getSprite(0);
				AnimationDef ad = EntityHandler.getAnimationDef(animID);
				Sprite s = app.g.spriteSelect(ad, 0);
				System.out.println("npc" + id + " name=" + def.getName() + " sprite0=" + animID
					+ " anim=" + ad.getName() + " number=" + ad.number
					+ " cam=" + def.getCamera1() + "x" + def.getCamera2()
					+ " | frame0 s1=" + s.getSomething1() + " s2=" + s.getSomething2()
					+ " w=" + s.getWidth() + " h=" + s.getHeight());
			}
			return;
		}
		if (args.length >= 3 && args[1].equals("--dump")) {
			int id = args.length >= 4 ? Integer.parseInt(args[3]) : 852;
			app.dump(id, args[2]);
			return;
		}
		if (args.length >= 3 && args[1].equals("--dump-entity")) {
			int id = args.length >= 4 ? Integer.parseInt(args[3]) : 0;
			app.dumpEntitySet(id, args[2]);
			return;
		}
		SwingUtilities.invokeLater(app::buildUi);
	}
}
