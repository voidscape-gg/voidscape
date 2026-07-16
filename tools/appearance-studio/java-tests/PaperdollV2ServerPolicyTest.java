package tools;

import com.openrsc.server.appearance.PaperdollV2EvaluationPolicy;
import com.openrsc.server.database.DatabaseType;
import com.openrsc.server.model.PlayerAppearance;
import com.openrsc.server.net.rsc.handlers.PlayerAppearanceUpdater;

/** Focused invariants for the QA-only saved hair-style policy. */
public final class PaperdollV2ServerPolicyTest {
	private static int assertions;

	private PaperdollV2ServerPolicyTest() { }

	public static void main(String[] args) {
		require(PlayerAppearance.MAX_HAIR_STYLE == 0,
			"production hair-style maximum must remain zero");

		PlayerAppearance classic = new PlayerAppearance(10, 15, 22, 43, 1, 2, 6);
		require(classic.getHairStyle() == 0,
			"production constructor must clamp every positive style to zero");
		require(!classic.usesPaperdollV2EvaluationPolicy(),
			"production constructor must not acquire the evaluation policy");

		PlayerAppearance male = PlayerAppearance.forPaperdollV2Evaluation(
			10, 15, 22, 43, 8, 2, 1);
		require(male.getHairStyle() == 1, "evaluation selector 1 must survive");
		require(male.usesPaperdollV2EvaluationPolicy(),
			"evaluation factory must mark its private policy");
		male.setHairStyle(6);
		require(male.getHairStyle() == 6, "evaluation selector 6 must survive");
		male.setHairStyle(255);
		require(male.getHairStyle() == 6,
			"post-construction style mutation must clamp at the QA maximum");
		male.setHead(1);
		require(male.getHairStyle() == 0,
			"leaving the V2 head identity must atomically clear the selector");

		PlayerAppearance female = PlayerAppearance.forPaperdollV2Evaluation(
			10, 15, 22, 43, 8, 5, 6);
		require(female.getHairStyle() == 6,
			"female evaluation selector 6 must survive");

		require(PlayerAppearance.isPaperdollV2EvaluationIdentity(1, 8, 2, true),
			"male lower-bound identity must pass");
		require(PlayerAppearance.isPaperdollV2EvaluationIdentity(6, 8, 5, false),
			"female upper-bound identity must pass");
		require(!PlayerAppearance.isPaperdollV2EvaluationIdentity(0, 8, 2, true),
			"classic selector must not be an evaluation identity");
		require(!PlayerAppearance.isPaperdollV2EvaluationIdentity(7, 8, 2, true),
			"unknown selector must not be an evaluation identity");
		require(!PlayerAppearance.isPaperdollV2EvaluationIdentity(1, 1, 2, true),
			"legacy head must not be an evaluation identity");
		require(!PlayerAppearance.isPaperdollV2EvaluationIdentity(1, 8, 5, true),
			"opposite-gender body must not be an evaluation identity");

		expectRejected(0, 8, 2, "selector zero");
		expectRejected(7, 8, 2, "selector seven");
		expectRejected(1, 1, 2, "legacy head");
		expectRejected(1, 8, 1, "unsupported body");

		require(PlayerAppearanceUpdater.decodeUnsignedAppearanceId((byte) 0x7f) == 128,
			"head byte 0x7f must decode unsigned");
		require(PlayerAppearanceUpdater.decodeUnsignedAppearanceId((byte) 0x80) == 129,
			"head byte 0x80 must decode unsigned");
		require(PlayerAppearanceUpdater.decodeUnsignedAppearanceId((byte) 0xff) == 256,
			"head byte 0xff must decode unsigned");

		for (int style = 1; style <= 6; style++) {
			expectSelectionAccepted(true, true, PlayerAppearance.MODERN_HAIR_CLIENT_VERSION,
				1, style, 8, 2, "male style " + style);
			expectSelectionAccepted(true, true, PlayerAppearance.MODERN_HAIR_CLIENT_VERSION,
				2, style, 8, 5, "female style " + style);
		}
		expectSelectionAccepted(false, false, 1, 0, 0, 256, -127,
			"selector zero remains on the production path");
		expectSelectionRejected(true, true, PlayerAppearance.MODERN_HAIR_CLIENT_VERSION,
			1, 7, 8, 2, "selector seven");
		expectSelectionRejected(true, true, PlayerAppearance.MODERN_HAIR_CLIENT_VERSION,
			1, 255, 8, 2, "selector 255");
		expectSelectionRejected(false, true, PlayerAppearance.MODERN_HAIR_CLIENT_VERSION,
			1, 1, 8, 2, "disabled server");
		expectSelectionRejected(true, false, PlayerAppearance.MODERN_HAIR_CLIENT_VERSION,
			1, 1, 8, 2, "wrong role");
		expectSelectionRejected(true, true, PlayerAppearance.MODERN_HAIR_CLIENT_VERSION - 1,
			1, 1, 8, 2, "old client");
		expectSelectionRejected(true, true, PlayerAppearance.MODERN_HAIR_CLIENT_VERSION,
			0, 1, 8, 2, "gender restriction zero");
		expectSelectionRejected(true, true, PlayerAppearance.MODERN_HAIR_CLIENT_VERSION,
			3, 1, 8, 2, "gender restriction three");
		expectSelectionRejected(true, true, PlayerAppearance.MODERN_HAIR_CLIENT_VERSION,
			1, 1, 1, 2, "wrong head");
		expectSelectionRejected(true, true, PlayerAppearance.MODERN_HAIR_CLIENT_VERSION,
			1, 1, 8, 5, "male/female body mismatch");
		expectSelectionRejected(true, true, PlayerAppearance.MODERN_HAIR_CLIENT_VERSION,
			2, 1, 8, 2, "female/male body mismatch");

		expectConfigurationAccepted(false, 0, false, DatabaseType.MYSQL, "production",
			true, true, 2, "fully disabled production state");
		expectConfigurationRejected(6, false, DatabaseType.SQLITE, "paperdoll_qa",
			false, false, 0, "missing JVM gate");
		expectConfigurationRejected(0, true, DatabaseType.SQLITE, "paperdoll_qa",
			false, false, 0, "missing config gate");
		expectConfigurationRejected(5, true, DatabaseType.SQLITE, "paperdoll_qa",
			false, false, 0, "wrong maximum");
		expectConfigurationRejected(6, true, DatabaseType.MYSQL, "paperdoll_qa",
			false, false, 0, "MySQL database");
		expectConfigurationRejected(6, true, DatabaseType.SQLITE, null,
			false, false, 0, "missing database name");
		expectConfigurationRejected(6, true, DatabaseType.SQLITE, "paperdoll",
			false, false, 0, "non-QA database name");
		expectConfigurationRejected(6, true, DatabaseType.SQLITE, "paperdoll_qa",
			true, false, 0, "production command lockdown");
		expectConfigurationRejected(6, true, DatabaseType.SQLITE, "paperdoll_qa",
			false, true, 0, "avatar generator");
		expectConfigurationRejected(6, true, DatabaseType.SQLITE, "paperdoll_qa",
			false, false, 1, "creation mode one");
		expectConfigurationRejected(6, true, DatabaseType.SQLITE, "paperdoll_qa",
			false, false, 2, "creation mode two");
		expectConfigurationAccepted(true, 6, true, DatabaseType.SQLITE, "paperdoll_qa",
			false, false, 0, "complete QA gate");

		int[] base = {8, 2, 3, 0, 0, 0, 0, 0, 0, 0, 0, 0};
		require(PaperdollV2EvaluationPolicy.appearanceBlockReason(true, true,
			base).length() == 0, "visible base identity must open the designer");
		int[] chainOverlay = base.clone();
		chainOverlay[6] = 21;
		chainOverlay[7] = 37;
		require(PaperdollV2EvaluationPolicy.appearanceBlockReason(true, true,
			chainOverlay).length() == 0,
			"overlay-slot body and legs must preserve designer hydration");
		int[] hiddenHead = base.clone();
		hiddenHead[0] = 0;
		require(PaperdollV2EvaluationPolicy.appearanceBlockReason(true, true,
			hiddenHead).contains("head"), "hidden base head must block");
		int[] plateBody = base.clone();
		plateBody[1] = 28;
		require(PaperdollV2EvaluationPolicy.appearanceBlockReason(true, true,
			plateBody).contains("body"), "plate body in base slot must block");
		int[] plateLegs = base.clone();
		plateLegs[2] = 37;
		require(PaperdollV2EvaluationPolicy.appearanceBlockReason(true, true,
			plateLegs).contains("legs"), "plate legs in base slot must block");
		require(PaperdollV2EvaluationPolicy.appearanceBlockReason(false, true,
			plateBody).length() == 0, "disabled evaluation must not change legacy screens");
		require(PaperdollV2EvaluationPolicy.appearanceBlockReason(true, false,
			plateBody).length() == 0, "non-developer legacy screens must remain available");

		System.out.println("Paperdoll V2 server policy PASS: assertions=" + assertions);
	}

	private static void expectRejected(int style, int head, int body, String label) {
		boolean rejected = false;
		try {
			PlayerAppearance.forPaperdollV2Evaluation(10, 15, 22, 43,
				head, body, style);
		} catch (IllegalArgumentException expected) {
			rejected = true;
		}
		require(rejected, label + " must be rejected by the evaluation factory");
	}

	private static void expectSelectionAccepted(boolean enabled, boolean developer,
		int clientVersion, int gender, int style, int head, int body, String label) {
		require(PlayerAppearanceUpdater.paperdollV2EvaluationRejectionReason(enabled,
			developer, clientVersion, gender, style, head, body).length() == 0,
			label + " must be accepted");
	}

	private static void expectSelectionRejected(boolean enabled, boolean developer,
		int clientVersion, int gender, int style, int head, int body, String label) {
		require(PlayerAppearanceUpdater.paperdollV2EvaluationRejectionReason(enabled,
			developer, clientVersion, gender, style, head, body).length() > 0,
			label + " must be rejected");
	}

	private static void expectConfigurationAccepted(boolean expectedEnabled, int maximum,
		boolean jvmGate, DatabaseType databaseType, String databaseName,
		boolean lockdown, boolean avatarGenerator, int creationMode, String label) {
		require(PaperdollV2EvaluationPolicy.configurationRejectionReason(
			maximum, jvmGate, databaseType, databaseName, lockdown, avatarGenerator,
			creationMode).length() == 0, label + " must not be rejected");
		require(PaperdollV2EvaluationPolicy.configurationEnabled(
			maximum, jvmGate, databaseType, databaseName, lockdown, avatarGenerator,
			creationMode) == expectedEnabled, label + " enabled state must match");
	}

	private static void expectConfigurationRejected(int maximum, boolean jvmGate,
		DatabaseType databaseType, String databaseName, boolean lockdown,
		boolean avatarGenerator, int creationMode, String label) {
		require(PaperdollV2EvaluationPolicy.configurationRejectionReason(
			maximum, jvmGate, databaseType, databaseName, lockdown, avatarGenerator,
			creationMode).length() > 0, label + " must be rejected");
		require(!PaperdollV2EvaluationPolicy.configurationEnabled(
			maximum, jvmGate, databaseType, databaseName, lockdown, avatarGenerator,
			creationMode), label + " must stay disabled");
	}

	private static void require(boolean condition, String message) {
		assertions++;
		if (!condition) throw new AssertionError(message);
	}
}
