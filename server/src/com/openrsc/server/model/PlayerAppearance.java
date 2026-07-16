package com.openrsc.server.model;

import com.openrsc.server.appearance.GeneratedLookPresets;
import com.mysql.cj.xdevapi.Client;
import com.openrsc.server.external.NPCDef;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.rsc.ClientLimitations;
import com.openrsc.server.util.rsc.DataConversions;

public class PlayerAppearance {
	public static final int MODERN_HAIR_CLIENT_VERSION = 10057;
	public static final int MAX_HAIR_STYLE = 0;
	public static final int DEFAULT_HAIR_COLOUR = 10;
	public static final int DEFAULT_TOP_COLOUR = 15;
	public static final int DEFAULT_TROUSER_COLOUR = 22;
	public static final int DEFAULT_SKIN_COLOUR = 43;
	public static final int MIN_HAIR_COLOUR = 10;
	public static final int MAX_HAIR_COLOUR = 17;
	public static final int MAX_CLOTHING_COLOUR = 22;
	public static final int MAX_SKIN_COLOUR = 47;
	public static final int MODERN_HAIR_BASE_HEAD = 1;
	public static final int PAPERDOLL_V2_BASE_HEAD = 8;
	public static final int PAPERDOLL_V2_BASE_MALE_BODY = 2;
	public static final int PAPERDOLL_V2_BASE_FEMALE_BODY = 5;
	public static final int PAPERDOLL_V2_BASE_LEGS = 3;
	public static final int PAPERDOLL_V2_EVALUATION_MAX_HAIR_STYLE = 6;

	private boolean hideTrousers;
	private int body;
	private byte hairColour;
	private int hairStyle;
	private int head;
	private byte skinColour;
	private byte topColour;
	private byte trouserColour;
	private final int allowedHairStyleMax;
	private final int compatibleHairHead;

	private boolean impersonatingNpc;
	private NPCDef npcAppearance;

	private final int[] bodySprites = { 2, 5 };

	private final int[] playerSkinColors = new int[]{
		// original player skin colours
		0xECDED0, 0xCCB366, 0xB38C40, 0x997326, 0x906020,

		// authentic npc skin colours (with previously used colours removed)
		0x000000, 0x000004, 0x0066FF, 0x009000, 0x3CB371,
		0x55BFEE, 0x55CFFF, 0x604020, 0x663300, 0x6F5737,
		0x705010, 0x804000, 0x996633, 0x999999, 0xAC9E90,
		0xDCC399, 0xDCCEA0, 0xDCFFD0, 0xDD3040, 0xEADED2,
		0xECEED0, 0xECFED0, 0xECFFD0, 0xFCEEE0, 0xFF3333,
		0xFF9F55, 0xFFDED2, 0xFFFEF0, 0xFFFFFF,

		0x00A0A0, // teal
		0xFFFF00, // yellow
		0xFF69B4, // hot pink
		0x0180A2, // rsc zombie
		0x86668e, // evequill purple
		0x663399, // rebecca purple
		0xB5FF1D, // easter ogre
		0xA0C0C0, // silver man
		0x608080, // coal woman

		0xF2C8BA, // dawn
		0xD99A8F, // rose
		0xA76A45, // bronze
		0x6D3F2C, // umber
		0x9B9290, // ash
	};

	public PlayerAppearance(int hairColour, int topColour, int trouserColour,
							int skinColour, int head, int body) {
		this(hairColour, topColour, trouserColour, skinColour, head, body, 0);
	}

	public PlayerAppearance(int hairColour, int topColour, int trouserColour,
							int skinColour, int head, int body, int hairStyle) {
		this(hairColour, topColour, trouserColour, skinColour, head, body, hairStyle,
			MAX_HAIR_STYLE, MODERN_HAIR_BASE_HEAD, false);
	}

	public static PlayerAppearance forPaperdollV2Evaluation(int hairColour,
		int topColour, int trouserColour, int skinColour, int head, int body,
		int hairStyle) {
		return new PlayerAppearance(hairColour, topColour, trouserColour, skinColour,
			head, body, hairStyle, PAPERDOLL_V2_EVALUATION_MAX_HAIR_STYLE,
			PAPERDOLL_V2_BASE_HEAD, true);
	}

	private PlayerAppearance(int hairColour, int topColour, int trouserColour,
		int skinColour, int head, int body, int hairStyle, int allowedHairStyleMax,
		int compatibleHairHead, boolean rejectOutOfRange) {
		this.allowedHairStyleMax = allowedHairStyleMax;
		this.compatibleHairHead = compatibleHairHead;
		if (rejectOutOfRange && (hairStyle < 1 || hairStyle > allowedHairStyleMax
			|| head != compatibleHairHead
			|| (body != PAPERDOLL_V2_BASE_MALE_BODY
				&& body != PAPERDOLL_V2_BASE_FEMALE_BODY))) {
			throw new IllegalArgumentException("Invalid Paperdoll V2 evaluation appearance");
		}
		this.hairColour = (byte) hairColour;
		this.topColour = (byte) topColour;
		this.trouserColour = (byte) trouserColour;
		this.skinColour = (byte) skinColour;
		this.setHead(head);
		this.setBody(body);
		this.setHairStyle(hairStyle);
	}

	public byte getHairColour() {
		if (impersonatingNpc)
			return getNearestHairColour(npcAppearance.getHairColour());
		return hairColour;
	}

	public byte getHairColourSave() {
		return hairColour;
	}

	public int getHairStyle() {
		return hairStyle;
	}

	public void setHairStyle(int hairStyle) {
		if (!supportsModernHairStyleHead()) {
			this.hairStyle = 0;
			return;
		}
		this.hairStyle = Math.max(0, Math.min(allowedHairStyleMax, hairStyle));
	}

	private boolean supportsModernHairStyleHead() {
		return head == compatibleHairHead;
	}

	public static boolean isPaperdollV2EvaluationIdentity(int hairStyle, int head,
		int body, boolean male) {
		return hairStyle >= 1 && hairStyle <= PAPERDOLL_V2_EVALUATION_MAX_HAIR_STYLE
			&& head == PAPERDOLL_V2_BASE_HEAD
			&& body == (male ? PAPERDOLL_V2_BASE_MALE_BODY
				: PAPERDOLL_V2_BASE_FEMALE_BODY);
	}

	public boolean usesPaperdollV2EvaluationPolicy() {
		return allowedHairStyleMax == PAPERDOLL_V2_EVALUATION_MAX_HAIR_STYLE
			&& compatibleHairHead == PAPERDOLL_V2_BASE_HEAD;
	}

	private byte getNearestHairColour(int hairColour) {
		int[] authenticHairColours = new int[] { 0xffc030, 0xffa040, 0x805030, 0x604020, 0x303030, 0xff6020, 0xff4000, 0xffffff, 0x00ff00, 0x00ffff };
		return getNearestColour(hairColour, authenticHairColours);
	}

	public byte getSkinColour() {
		return getSkinColour(4);
	}

	public byte getSkinColour(int limit) {
		if (impersonatingNpc)
			return getNearestSkinColour(npcAppearance.getSkinColour(), limit);
		return skinColour;
	}

	public byte getSkinColourSave() {
		return skinColour;
	}

	private byte getNearestSkinColour(int skinColour, int supportedColours) {
		return getNearestColour(skinColour, playerSkinColors, supportedColours);
	}

	public int getSprite(int pos) {
		switch (pos) {
			case 0:
				return getHead();
			case 1:
				return getBody();
			case 2:
				if (hideTrousers) {
					return 0;
				}
				return 3;
			default:
				return 0;
		}
	}

	// was originally implemented for use with some minigame pre-RSCR era
	public void hideTrousers(boolean b) {
		hideTrousers = b;
	}

	public int[] getSprites() {
		return new int[]{getHead(), getBody(), 3, 0, 0, 0, 0, 0, 0, 0, 0, 0};
	}

	public byte getTopColour() {
		if (impersonatingNpc)
			return getNearestClothingColour(npcAppearance.getTopColour());
		return topColour;
	}

	public byte getTopColourSave() {
		return topColour;
	}

	public byte getTrouserColour() {
		if (impersonatingNpc)
			return getNearestClothingColour(npcAppearance.getBottomColour());
		return trouserColour;
	}

	public byte getTrouserColourSave() {
		return trouserColour;
	}

	private byte getNearestClothingColour(int clothingColour) {
		int[] authenticClothingColours = new int[] { 0xff0000, 0xff8000, 0xffe000, 0xa0e000, 0x00e000, 0x008000, 0x00a080, 0x00b0ff, 0x0080ff, 0x0030f0, 0xe000e0, 0x303030, 0x604000, 0x805000, 0xffffff };
		return getNearestColour(clothingColour, authenticClothingColours);
	}

	public boolean isValid(Player player) {
		if (!GeneratedLookPresets.isSelectableHead(getHead())
			|| !DataConversions.inArray(bodySprites, getBody())) {
			return false;
		}
		if (hairColour < 0 || topColour < 0 || trouserColour < 0
			|| skinColour < 0 || hairStyle < 0 || hairStyle > allowedHairStyleMax
			|| (hairStyle > 0 && !supportsModernHairStyleHead())) {
			return false;
		}
		if (hairColour < MIN_HAIR_COLOUR || hairColour > MAX_HAIR_COLOUR
			|| topColour > MAX_CLOTHING_COLOUR || trouserColour > MAX_CLOTHING_COLOUR
			|| skinColour > MAX_SKIN_COLOUR) {
			return false;
		}
		if (skinColour > 4) {
			if (skinColour >= playerSkinColors.length) {
				return false;
			}
			// player cache hasn't been loaded yet, will allow a skin within bounds
			if (!player.isLoggedIn()) {
				return player.supportsPlayerUnlockedAppearancesPacket();
			}
			if (player.getUnlockedSkinColours() == null || skinColour >= player.getUnlockedSkinColours().length) {
				return false;
			}

			return player.getUnlockedSkinColours()[skinColour];
		}
		return true;
	}

	public int getHead() {
		return head;
	}

	public void setHead(int head) {
		this.head = head;
		if (!supportsModernHairStyleHead()) {
			this.hairStyle = 0;
		}
	}

	public int getBody() {
		return body;
	}

	public void setBody(int body) {
		this.body = body;
	}

	public boolean isImpersonatingNpc() {
		return impersonatingNpc;
	}

	public void setNpcAppearance(NPCDef n) {
		npcAppearance = n;
		impersonatingNpc = true;
	}

	public void restorePlayerAppearance() {
		impersonatingNpc = false;
	}


	private int distanceBetweenColours (int color1, int color2) {
		int red1 = (color1 & 0xFF0000) >> 16;
		int red2 = (color2 & 0xFF0000) >> 16;
		int green1 = (color1 & 0xFF00) >> 8;
		int green2 = (color2 & 0xFF00) >> 8;
		int blue1 = color1 & 0xFF;
		int blue2 = color2 & 0xFF;

		int redDiff = red1 > red2 ? red1 - red2 : red2 - red1;
		int greenDiff = green1 > green2 ? green1 - green2 : green2 - green1;
		int blueDiff = blue1 > blue2 ? blue1 - blue2 : blue2 - blue1;

		return redDiff + greenDiff + blueDiff;
	}

	private byte getNearestColour(int colour, int[] availableColours) {
		return getNearestColour(colour, availableColours, Integer.MAX_VALUE);
	}

	private byte getNearestColour(int colour, int[] availableColours, int limit) {
		// override for black -> a much lighter shade of gray which looks black
		// otherwise, a dark green is actually the closest to black.
		if (colour <= 5) {
			for (int i = 0; i < availableColours.length && i <= limit; i++) {
				if (availableColours[i] == 0x303030) {
					return (byte)(i & 0xFF);
				}
			}
		}

		// normal distance formula
		int[] distances = new int[availableColours.length];
		for (int i = 0; i < availableColours.length && i <= limit; i++) {
			distances[i] = distanceBetweenColours(colour, availableColours[i]);
		}

		int smallestIndex = 0;
		for (int i = 1; i < distances.length && i <= limit; i++) {
			if (distances[i] < distances[smallestIndex]) {
				smallestIndex = i;
			}
		}

		return (byte)(smallestIndex & 0xFF);
	}

}
