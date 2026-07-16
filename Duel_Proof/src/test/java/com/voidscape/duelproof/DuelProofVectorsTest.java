package com.voidscape.duelproof;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

/** Dependency-free golden vectors for every runtime that consumes the proof module. */
public final class DuelProofVectorsTest {

	private DuelProofVectorsTest() {
	}

	public static void main(String[] args) {
		testSha256Vectors();
		testHmacVectors();
		testProofDerivationVectors();
		testRngVectors();
		testInitiativeVectors();
		testMeleeReplayVectors();
		testTerminalWitnessGoldenVector();
		testTerminalWitnessTamperRejection();
		testDestroyedStreams();
		testCodecAndCanonicalStarter();
		System.out.println("Duel proof Java vectors passed");
	}

	private static void testSha256Vectors() {
		assertHex("sha256 empty",
			"e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
			DuelProofCrypto.sha256(new byte[0]));
		assertHex("sha256 abc",
			"ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
			DuelProofCrypto.sha256(DuelProofCodec.ascii("abc")));
		assertRepeatedSha(55, "9f4390f8d30c2dd92ec9f095b65e2b9ae9b0a925a5258e241c9f1e910f734318");
		assertRepeatedSha(56, "b35439a4ac6f0948b6d6f9e3c6af0f5f590ce20f1bde7090ef7970686ec6738a");
		assertRepeatedSha(63, "7d3e74a05d7db15bce4ad9ec0658ea98e3f06eeecf16b4c6fff2da457ddc2f34");
		assertRepeatedSha(64, "ffe054fe7ae0cb6dc65c3af9b61d5209f439851db43d0ba5997337df154668eb");
		assertRepeatedSha(65, "635361c48bb9eab14198e76ea8ab7f1a41685d6ad62aa9146d301d4f17eb0ae0");
	}

	private static void testHmacVectors() {
		byte[] shortKey = new byte[20];
		Arrays.fill(shortKey, (byte) 0x0b);
		assertHex("RFC 4231 short-key HMAC",
			"b0344c61d8db38535ca8afceaf0bf12b881dc200c9833da726e9376c2e32cff7",
			DuelProofCrypto.hmacSha256(shortKey, DuelProofCodec.ascii("Hi There")));

		byte[] longKey = new byte[131];
		Arrays.fill(longKey, (byte) 0xaa);
		assertHex("RFC 4231 long-key HMAC",
			"60e431591ee0b67f0d8a26aacbf5b77f8e0bc6213728c5140546040f0ee37f54",
			DuelProofCrypto.hmacSha256(longKey,
				DuelProofCodec.ascii("Test Using Larger Than Block-Size Key - Hash Key First")));
	}

	private static void testProofDerivationVectors() {
		byte[] contextHash = DuelProofCrypto.contextHash(DuelProofCodec.ascii("golden-context-v1"));
		byte[] serverSeed = sequence(0x00);
		byte[] clientSeedA = sequence(0x20);
		byte[] clientSeedB = sequence(0x40);
		assertHex("context hash",
			"0ece2c40eddb1820215fae3c24cc3889142a4702ce7b01b5546c36e16664754c",
			contextHash);
		assertHex("server commitment",
			"b7a232999a69cebad366a0d1287b46e19046fb660fb8b25b13f0bf2995c3f48c",
			DuelProofCrypto.serverCommitment(contextHash, serverSeed));
		byte[] serverCommitment = DuelProofCrypto.serverCommitment(contextHash, serverSeed);
		byte[] clientCommitmentA = DuelProofCrypto.clientCommitment(
			contextHash, serverCommitment, 0, clientSeedA);
		byte[] clientCommitmentB = DuelProofCrypto.clientCommitment(
			contextHash, serverCommitment, 1, clientSeedB);
		assertHex("canonical client commitment A",
			"96c34ff72ffc996450f13c9815588700b7489732fc8a6f613d01b10c78666b91",
			clientCommitmentA);
		assertHex("canonical client commitment B",
			"901ed556502a8ffd1f244d0545b7e685f2d9ab17cd1e72c211cac56ac6290978",
			clientCommitmentB);
		assertHex("final pre-combat lock",
			"e99d907defc9d91e63d1689a1bbac174eea5dd9f6394c372b24c4aa7b67175a7",
			DuelProofCrypto.finalLockHash(Arrays.copyOf(serverSeed, 16), contextHash,
				serverCommitment, 7, clientCommitmentA, 42, clientCommitmentB));
		assertHex("master seed",
			"82a98f3412c218a4e0f95f5c2e7a4b07107fe931fc265242e4c429c9de6c0819",
			DuelProofCrypto.masterSeed(contextHash, serverSeed, clientSeedA, clientSeedB));
	}

	private static void testRngVectors() {
		byte[] masterSeed = DuelProofCodec.parseHexLowerExact(
			"82a98f3412c218a4e0f95f5c2e7a4b07107fe931fc265242e4c429c9de6c0819", 32);
		String[] words = {
			"3910988e", "71fb07df", "e8a98bbd", "6db20021",
			"2fcd87b1", "91086706", "f073d341", "e3c9f5d6",
			"28c7066b", "3e480c90", "9181d472", "70a6d675",
			"3a6075fa", "ee52bcde", "48725a6f", "f69430f2"
		};
		DuelProofRng stream = new DuelProofRng(masterSeed);
		for (int i = 0; i < words.length; i++) {
			assertLong("rng word " + i, Long.parseLong(words[i], 16), stream.nextUnsignedInt32());
		}
		assertLong("two generated blocks", 2L, stream.getGeneratedBlockCount());
		assertLong("sixteen candidate draws", 16L, stream.getCandidateDrawCount());

		DuelProofRng coin = new DuelProofRng(masterSeed);
		assertLong("starter coin", 0L, coin.nextCoin());
		assertLong("starter consumes one candidate", 1L, coin.getCandidateDrawCount());

		DuelProofRng unit = new DuelProofRng(masterSeed);
		assertLong("unit53", 2007790135426912L, unit.nextUnit53());
		DuelProofRng unitDouble = new DuelProofRng(masterSeed);
		assertLong("unit double bits", 0x3fcc884c4738fd80L,
			Double.doubleToLongBits(unitDouble.nextUnitDouble()));

		byte[] rejectionSeed = new byte[32];
		Arrays.fill(rejectionSeed, (byte) 0x01);
		DuelProofRng rejection = new DuelProofRng(rejectionSeed);
		assertLong("rejection-sampled result", 84728195L, rejection.nextInt(1073741825));
		assertLong("rejection consumes two candidates", 2L, rejection.getCandidateDrawCount());

		DuelProofRng boundOne = new DuelProofRng(masterSeed);
		assertLong("bound one result", 0L, boundOne.nextInt(1));
		assertLong("bound one still consumes a candidate", 1L, boundOne.getCandidateDrawCount());

		byte[] mutableSeed = Arrays.copyOf(masterSeed, masterSeed.length);
		DuelProofRng defensive = new DuelProofRng(mutableSeed);
		mutableSeed[0] ^= 0x7f;
		assertLong("rng defensively copies seed", 0x3910988eL, defensive.nextUnsignedInt32());
		expectIllegalArgument("zero bound", new Runnable() {
			@Override
			public void run() {
				new DuelProofRng(new byte[32]).nextInt(0);
			}
		});
	}

	private static void testInitiativeVectors() {
		final byte[] drawZeroSeed = new byte[32];
		final byte[] drawOneSeed = new byte[32];
		Arrays.fill(drawOneSeed, (byte) 0x03);

		final DuelProofMeleeReplay lowerFirst = new DuelProofMeleeReplay(drawOneSeed);
		assertLong("lower ordinal zero overrides opposing draw one", 0L,
			lowerFirst.chooseStarterOrdinal(20, 80));
		assertLong("lower ordinal zero still records draw one", 1L,
			lowerFirst.getStarterDraw().getValue());
		assertLong("lower ordinal zero preserves downstream draw offset", 1L,
			lowerFirst.getCandidateDrawCount());
		lowerFirst.destroy();

		final DuelProofMeleeReplay lowerSecond = new DuelProofMeleeReplay(drawZeroSeed);
		assertLong("lower ordinal one overrides opposing draw zero", 1L,
			lowerSecond.chooseStarterOrdinal(80, 20));
		assertLong("lower ordinal one still records draw zero", 0L,
			lowerSecond.getStarterDraw().getValue());
		assertLong("lower ordinal one preserves downstream draw offset", 1L,
			lowerSecond.getCandidateDrawCount());
		lowerSecond.destroy();

		final DuelProofMeleeReplay tiedDrawZero = new DuelProofMeleeReplay(drawZeroSeed);
		assertLong("equal combat levels use draw zero", 0L,
			tiedDrawZero.chooseStarterOrdinal(50, 50));
		assertLong("equal-level draw zero is committed", 0L,
			tiedDrawZero.getStarterDraw().getValue());
		tiedDrawZero.destroy();

		final DuelProofMeleeReplay tiedDrawOne = new DuelProofMeleeReplay(drawOneSeed);
		assertLong("equal combat levels use draw one", 1L,
			tiedDrawOne.chooseStarterOrdinal(50, 50));
		assertLong("equal-level draw one is committed", 1L,
			tiedDrawOne.getStarterDraw().getValue());
		tiedDrawOne.destroy();

		expectIllegalArgument("initiative rejects zero first combat level", new Runnable() {
			@Override
			public void run() {
				new DuelProofMeleeReplay(drawZeroSeed).chooseStarterOrdinal(0, 50);
			}
		});
		expectIllegalArgument("initiative rejects zero second combat level", new Runnable() {
			@Override
			public void run() {
				new DuelProofMeleeReplay(drawZeroSeed).chooseStarterOrdinal(50, 0);
			}
		});
	}

	private static void testMeleeReplayVectors() {
		final DuelProofMeleeReplay capeReplay = new DuelProofMeleeReplay(new byte[32]);
		assertLong("zero-seed starter", 0L, capeReplay.chooseStarterOrdinal(99, 99));
		final DuelProofMeleeInput capeInput = new DuelProofMeleeInput(
			DuelProofSpec.COMBAT_STYLE_CONTROLLED, DuelProofSpec.COMBAT_STYLE_CONTROLLED,
			1, 10, 16,
			DuelProofSpec.PRAYER_TIER_NONE, DuelProofSpec.PRAYER_TIER_NONE,
			DuelProofSpec.PRAYER_TIER_NONE,
			0, 0, 0, true, false, false);
		final DuelProofMeleeSwing capeSwing = capeReplay.resolveSwing(0, capeInput);
		assertLong("attack-cape accuracy raw", 2748058581178360L,
			capeSwing.getDraws().get(0).getValue());
		assertLong("attack-cape activation raw", 1871517440794201L,
			capeSwing.getDraws().get(1).getValue());
		assertLong("attack-cape reroll raw", 891832625899225L,
			capeSwing.getDraws().get(2).getValue());
		assertLong("attack-cape candidate end", 8L, capeSwing.getCandidateEnd());
		assertDrawTypes("attack-cape draw order", capeSwing,
			DuelProofSpec.DRAW_ACCURACY,
			DuelProofSpec.DRAW_ATTACK_CAPE_ACTIVATION,
			DuelProofSpec.DRAW_ATTACK_CAPE_ACCURACY,
			DuelProofSpec.DRAW_DAMAGE);
		assertTrue("attack-cape final hit", capeSwing.getResult().isHit());
		assertTrue("attack cape prevented zero", capeSwing.getResult().isAttackCapePreventedZero());
		assertLong("attack-cape final damage", 2L, capeSwing.getResult().getDamage());
		assertLong("one attack-cape roll", 1L, capeSwing.getResult().getAttackCapeRolls());
		assertLong("one attack-cape reroll", 1L, capeSwing.getResult().getAttackCapeRerolls());
		capeReplay.destroy();

		final byte[] masterSeed = DuelProofCodec.parseHexLowerExact(
			"82a98f3412c218a4e0f95f5c2e7a4b07107fe931fc265242e4c429c9de6c0819", 32);
		final DuelProofMeleeInput strongInput = new DuelProofMeleeInput(
			DuelProofSpec.COMBAT_STYLE_CONTROLLED, DuelProofSpec.COMBAT_STYLE_CONTROLLED,
			60, 60, 60,
			DuelProofSpec.PRAYER_TIER_NONE, DuelProofSpec.PRAYER_TIER_NONE,
			DuelProofSpec.PRAYER_TIER_NONE,
			50, 50, 50, false, true, false);
		final DuelProofMeleeReplay strongReplay = new DuelProofMeleeReplay(masterSeed);
		assertLong("golden replay starter", 0L, strongReplay.chooseStarterOrdinal(99, 99));
		final DuelProofMeleeSwing strongSwing = strongReplay.resolveSwing(0, strongInput);
		assertLong("golden accuracy raw", 4010335451026737L,
			strongSwing.getDraws().get(0).getValue());
		assertLong("golden damage roll", 5429L, strongSwing.getDraws().get(1).getValue());
		assertLong("strength cape bucket", 19L, capeBucket(strongSwing.getDraws().get(2)));
		assertDrawTypes("strength draw order", strongSwing,
			DuelProofSpec.DRAW_ACCURACY, DuelProofSpec.DRAW_DAMAGE,
			DuelProofSpec.DRAW_STRENGTH_CAPE_ACTIVATION);
		assertLong("strength final damage", 9L, strongSwing.getResult().getDamage());
		assertTrue("strength cape activated", strongSwing.getResult().isStrengthCapeActivated());
		assertTrue("big hit creates momentum", strongSwing.hasMomentumAfter());
		assertLong("strength candidate end", 6L, strongSwing.getCandidateEnd());
		strongReplay.destroy();

		final DuelProofMeleeInput prayerStyleInput = new DuelProofMeleeInput(
			DuelProofSpec.COMBAT_STYLE_AGGRESSIVE, DuelProofSpec.COMBAT_STYLE_DEFENSIVE,
			60, 60, 60,
			DuelProofSpec.PRAYER_TIER_LOW, DuelProofSpec.PRAYER_TIER_MIDDLE,
			DuelProofSpec.PRAYER_TIER_HIGH,
			50, 50, 50, false, false, false);
		final DuelProofMeleeReplay prayerStyleReplay = new DuelProofMeleeReplay(masterSeed);
		assertLong("prayer/style starter", 0L,
			prayerStyleReplay.chooseStarterOrdinal(99, 99));
		final DuelProofMeleeSwing prayerStyleSwing =
			prayerStyleReplay.resolveSwing(0, prayerStyleInput);
		assertLong("prayer/style accuracy raw", 4010335451026737L,
			prayerStyleSwing.getDraws().get(0).getValue());
		assertLong("prayer/style damage bound", 8778L,
			prayerStyleSwing.getDraws().get(1).getBound());
		assertLong("prayer/style damage roll", 4061L,
			prayerStyleSwing.getDraws().get(1).getValue());
		assertLong("prayer/style final damage", 5L,
			prayerStyleSwing.getResult().getDamage());
		assertLong("prayer/style candidate start", 1L,
			prayerStyleSwing.getCandidateStart());
		assertLong("prayer/style candidate end", 4L,
			prayerStyleSwing.getCandidateEnd());
		assertFalse("prayer/style does not create momentum",
			prayerStyleSwing.hasMomentumAfter());
		assertDrawTypes("prayer/style draw order", prayerStyleSwing,
			DuelProofSpec.DRAW_ACCURACY, DuelProofSpec.DRAW_DAMAGE);

		final DuelProofMeleeResult changedResult = new DuelProofMeleeResult(
			true, true, 6, 6, 0, 0, 0,
			false, false, false, false, false, false);
		final DuelProofMeleeSwing changedResultSwing = new DuelProofMeleeSwing(
			prayerStyleSwing.getSwingNumber(), prayerStyleSwing.getActorOrdinal(),
			prayerStyleSwing.getInput(), changedResult, prayerStyleSwing.hadMomentumBefore(),
			prayerStyleSwing.hasMomentumAfter(), prayerStyleSwing.getCandidateStart(),
			prayerStyleSwing.getCandidateEnd(), prayerStyleSwing.getDraws());
		assertFalse("changed result fails replay", DuelProofMeleeReplay.verifiesPrefix(
			masterSeed, 99, 99, 0, Arrays.asList(changedResultSwing)));

		final List<DuelProofDraw> changedDraws =
			new ArrayList<DuelProofDraw>(prayerStyleSwing.getDraws());
		final DuelProofDraw accuracyDraw = changedDraws.get(0);
		changedDraws.set(0, new DuelProofDraw(accuracyDraw.getType(), accuracyDraw.getBound(),
			accuracyDraw.getValue(), accuracyDraw.getCandidateStart(),
			accuracyDraw.getCandidateEnd() - 1));
		final DuelProofMeleeSwing changedBoundarySwing = new DuelProofMeleeSwing(
			prayerStyleSwing.getSwingNumber(), prayerStyleSwing.getActorOrdinal(),
			prayerStyleSwing.getInput(), prayerStyleSwing.getResult(),
			prayerStyleSwing.hadMomentumBefore(), prayerStyleSwing.hasMomentumAfter(),
			prayerStyleSwing.getCandidateStart(), prayerStyleSwing.getCandidateEnd(), changedDraws);
		assertFalse("changed draw boundary fails replay", DuelProofMeleeReplay.verifiesPrefix(
			masterSeed, 99, 99, 0, Arrays.asList(changedBoundarySwing)));
		prayerStyleReplay.destroy();

		final DuelProofMeleeInput defenceInput = new DuelProofMeleeInput(
			DuelProofSpec.COMBAT_STYLE_CONTROLLED, DuelProofSpec.COMBAT_STYLE_CONTROLLED,
			60, 60, 60,
			DuelProofSpec.PRAYER_TIER_NONE, DuelProofSpec.PRAYER_TIER_NONE,
			DuelProofSpec.PRAYER_TIER_NONE,
			50, 50, 50, false, true, true);
		final DuelProofMeleeReplay defenceReplay = new DuelProofMeleeReplay(masterSeed);
		defenceReplay.chooseStarterOrdinal(99, 99);
		final DuelProofMeleeSwing defenceSwing = defenceReplay.resolveSwing(0, defenceInput);
		assertDrawTypes("defence suppresses strength draw", defenceSwing,
			DuelProofSpec.DRAW_ACCURACY, DuelProofSpec.DRAW_DAMAGE,
			DuelProofSpec.DRAW_DEFENCE_CAPE_ACTIVATION);
		assertTrue("defence cape activated", defenceSwing.getResult().isDefenceCapeActivated());
		assertTrue("defence cape applied", defenceSwing.getResult().isDefenceCapeApplied());
		assertLong("defence cape blocked damage", 3L,
			defenceSwing.getResult().getBlockedDamage());
		assertLong("defence cape final damage", 3L, defenceSwing.getResult().getDamage());
		assertFalse("strength cape not rolled after defence", defenceSwing.getResult().isStrengthCapeRolled());
		defenceReplay.destroy();

		final DuelProofMeleeInput zeroDamageInput = new DuelProofMeleeInput(
			DuelProofSpec.COMBAT_STYLE_ACCURATE, DuelProofSpec.COMBAT_STYLE_CONTROLLED,
			99, 0, 0,
			DuelProofSpec.PRAYER_TIER_NONE, DuelProofSpec.PRAYER_TIER_NONE,
			DuelProofSpec.PRAYER_TIER_NONE,
			1000, -64, 1, false, false, true);
		final DuelProofMeleeReplay zeroDamageReplay = new DuelProofMeleeReplay(masterSeed);
		zeroDamageReplay.chooseStarterOrdinal(99, 99);
		final DuelProofMeleeSwing zeroDamageSwing = zeroDamageReplay.resolveSwing(0, zeroDamageInput);
		assertTrue("successful zero remains a hit", zeroDamageSwing.getResult().isHit());
		assertLong("successful zero damage", 0L, zeroDamageSwing.getResult().getDamage());
		assertTrue("successful zero rolls defence cape", zeroDamageSwing.getResult().isDefenceCapeRolled());
		assertDrawTypes("successful-zero draw order", zeroDamageSwing,
			DuelProofSpec.DRAW_ACCURACY, DuelProofSpec.DRAW_DEFENCE_CAPE_ACTIVATION);
		zeroDamageReplay.destroy();

		final DuelProofMeleeReplay momentumReplay = new DuelProofMeleeReplay(masterSeed);
		final int starter = momentumReplay.chooseStarterOrdinal(99, 99);
		final DuelProofMeleeSwing first = momentumReplay.resolveSwing(0, strongInput);
		final DuelProofMeleeInput quietInput = new DuelProofMeleeInput(
			DuelProofSpec.COMBAT_STYLE_ACCURATE, DuelProofSpec.COMBAT_STYLE_CONTROLLED,
			99, 0, 0,
			DuelProofSpec.PRAYER_TIER_NONE, DuelProofSpec.PRAYER_TIER_NONE,
			DuelProofSpec.PRAYER_TIER_NONE,
			1000, -64, 1, false, false, false);
		final DuelProofMeleeSwing second = momentumReplay.resolveSwing(1, quietInput);
		final DuelProofMeleeInput changedStyleInput = new DuelProofMeleeInput(
			DuelProofSpec.COMBAT_STYLE_AGGRESSIVE, DuelProofSpec.COMBAT_STYLE_DEFENSIVE,
			99, 99, 1,
			DuelProofSpec.PRAYER_TIER_NONE, DuelProofSpec.PRAYER_TIER_NONE,
			DuelProofSpec.PRAYER_TIER_NONE,
			1000, 1000, 1, false, false, false);
		final DuelProofMeleeSwing third = momentumReplay.resolveSwing(0, changedStyleInput);
		assertTrue("actor momentum survives opponent turn", third.hadMomentumBefore());
		assertDrawTypes("momentum draw order", third,
			DuelProofSpec.DRAW_ACCURACY, DuelProofSpec.DRAW_DAMAGE,
			DuelProofSpec.DRAW_MOMENTUM_DAMAGE);
		final List<DuelProofMeleeSwing> transcript = momentumReplay.getSwings();
		assertTrue("full transcript replays",
			DuelProofMeleeReplay.verifiesPrefix(masterSeed, 99, 99, starter, transcript));
		assertTrue("valid tail-truncated prefix is explicit",
			DuelProofMeleeReplay.verifiesPrefix(masterSeed, 99, 99, starter,
				Arrays.asList(first, second)));
		expectUnsupported("transcript is immutable", new Runnable() {
			@Override
			public void run() {
				transcript.clear();
			}
		});

		final DuelProofMeleeInput tamperedInput = new DuelProofMeleeInput(
			DuelProofSpec.COMBAT_STYLE_CONTROLLED, changedStyleInput.getDefenderCombatStyle(),
			changedStyleInput.getAttackerAttackLevel(), changedStyleInput.getAttackerStrengthLevel(),
			changedStyleInput.getDefenderDefenceLevel(),
			changedStyleInput.getAttackerAttackPrayerTier(),
			changedStyleInput.getAttackerStrengthPrayerTier(),
			changedStyleInput.getDefenderDefencePrayerTier(),
			changedStyleInput.getAttackerWeaponAimPoints(),
			changedStyleInput.getAttackerWeaponPowerPoints(),
			changedStyleInput.getDefenderArmourPoints(), false, false, false);
		final DuelProofMeleeSwing tampered = new DuelProofMeleeSwing(third.getSwingNumber(),
			third.getActorOrdinal(), tamperedInput, third.getResult(), third.hadMomentumBefore(),
			third.hasMomentumAfter(), third.getCandidateStart(), third.getCandidateEnd(), third.getDraws());
		final List<DuelProofMeleeSwing> tamperedTranscript = new ArrayList<DuelProofMeleeSwing>(transcript);
		tamperedTranscript.set(2, tampered);
		assertFalse("changed style fails replay",
			DuelProofMeleeReplay.verifiesPrefix(masterSeed, 99, 99, starter,
				tamperedTranscript));
		assertFalse("removed middle swing fails replay", DuelProofMeleeReplay.verifiesPrefix(
			masterSeed, 99, 99, starter,
			Arrays.asList(first, third)));
		assertLong("middle swing is globally second", 2L, second.getSwingNumber());
		momentumReplay.destroy();
	}

	private static void testTerminalWitnessGoldenVector() {
		final TerminalFixture fixture = terminalFixture(DuelProofSpec.DUEL_RULE_NO_MAGIC,
			DuelProofSpec.TERMINAL_CAUSE_DIRECT_MELEE, terminalStrongInput());
		assertLong("terminal witness canonical length", 917L, fixture.encoded.length);
		assertHex("terminal witness canonical hash",
			"427fcb13155a12a9cc1fbb4eb522a66e60d6677a940781e1816a2fa3aa645535",
			DuelProofCrypto.sha256(fixture.encoded));

		final DuelProofTerminalVerifier.Verification verified =
			DuelProofTerminalVerifier.verify(fixture.encoded);
		assertLong("verified witness version", DuelProofSpec.WITNESS_VERSION,
			verified.getWitness().getWitnessVersion());
		assertLong("verified terminal starter", fixture.starterOrdinal,
			verified.getWitness().getStarterOrdinal());
		assertLong("verified terminal winner", fixture.starterOrdinal,
			verified.getWitness().getWinnerOrdinal());
		assertLong("verified terminal swing count", 1L, verified.getComputedSwings().size());
		assertLong("verified terminal candidate count", fixture.finalCandidateDrawCount,
			verified.getWitness().getFinalCandidateDrawCount());
		assertTrue("verified terminal swing hit",
			verified.getComputedSwings().get(0).getResult().isHit());
		assertTrue("verified terminal swing positive damage",
			verified.getComputedSwings().get(0).getResult().getDamage() > 0);
		assertTrue("verification hash is raw canonical SHA-256",
			DuelProofCrypto.constantTimeEquals(DuelProofCrypto.sha256(fixture.encoded),
				verified.getWitnessHash()));
		assertLong("verified context recoil limit", 40L,
			verified.getContext().getRecoilLimit());
		assertLong("verified first stake item", 10L,
			verified.getContext().getParticipant(0).getStakes().get(0).getItemId());
		assertString("verified second username", "beta",
			verified.getContext().getParticipant(1).getUsername());
		assertLong("verified committed combat level", 99L,
			verified.getContext().getParticipant(1).getCombatLevel());
		assertFalse("golden participant has no recoil ring",
			verified.getContext().getParticipant(0).isRecoilEquipped());
		assertLong("pre-combat context decoder sees both players", 2L,
			DuelProofTerminalVerifier.verifyContext(fixture.witness.getContextBytes())
				.getParticipants().size());

		final ContextParticipantSpec lowerFirst = contextParticipant(terminalStrongInput(),
			1, false, 0, 99, 99, 10).withCombatLevel(20);
		final ContextParticipantSpec higherSecond = contextParticipant(terminalStrongInput(),
			1, false, 0, 99, 99, 11).withCombatLevel(80);
		final TerminalFixture firstGetsInitiative = terminalFixture(
			DuelProofSpec.DUEL_RULE_NO_MAGIC, DuelProofSpec.TERMINAL_CAUSE_DIRECT_MELEE,
			terminalStrongInput(), lowerFirst, higherSecond, 0, 1,
			Arrays.asList(terminalStrongInput()));
		assertLong("terminal verifier accepts lower ordinal zero over draw one", 0L,
			DuelProofTerminalVerifier.verify(firstGetsInitiative.encoded)
				.getWitness().getStarterOrdinal());
		assertLong("terminal lower ordinal zero fixture committed opposing draw", 1L,
			firstGetsInitiative.starterDraw);

		final ContextParticipantSpec higherFirst = lowerFirst.withCombatLevel(80);
		final ContextParticipantSpec lowerSecond = higherSecond.withCombatLevel(20);
		final TerminalFixture secondGetsInitiative = terminalFixture(
			DuelProofSpec.DUEL_RULE_NO_MAGIC, DuelProofSpec.TERMINAL_CAUSE_DIRECT_MELEE,
			terminalStrongInput(), higherFirst, lowerSecond, 1, 0,
			Arrays.asList(terminalStrongInput()));
		assertLong("terminal verifier accepts lower ordinal one over draw zero", 1L,
			DuelProofTerminalVerifier.verify(secondGetsInitiative.encoded)
				.getWitness().getStarterOrdinal());
		assertLong("terminal lower ordinal one fixture committed opposing draw", 0L,
			secondGetsInitiative.starterDraw);

		assertTrue("terminal witness canonical round trip",
			DuelProofCrypto.constantTimeEquals(fixture.encoded,
				DuelProofTerminalWitnessCodec.encode(
					DuelProofTerminalWitnessCodec.decode(fixture.encoded))));

		final byte[] copiedProofId = verified.getWitness().getProofId();
		copiedProofId[0] ^= 0x7f;
		assertFalse("terminal witness proof id is defensive",
			DuelProofCrypto.constantTimeEquals(copiedProofId,
				verified.getWitness().getProofId()));
		expectUnsupported("terminal witness participants are immutable", new Runnable() {
			@Override
			public void run() {
				verified.getWitness().getParticipants().clear();
			}
		});
		expectUnsupported("computed terminal swings are immutable", new Runnable() {
			@Override
			public void run() {
				verified.getComputedSwings().clear();
			}
		});
		expectUnsupported("verified context stakes are immutable", new Runnable() {
			@Override
			public void run() {
				verified.getContext().getParticipant(0).getStakes().clear();
			}
		});

		final TerminalFixture recoil = terminalFixture(DuelProofSpec.DUEL_RULE_NO_MAGIC,
			DuelProofSpec.TERMINAL_CAUSE_RECOIL, terminalStrongInput());
		final DuelProofTerminalVerifier.Verification recoilVerification =
			DuelProofTerminalVerifier.verify(recoil.encoded);
		final DuelProofTerminalWitness recoilWitness = recoilVerification.getWitness();
		assertLong("recoil winner is the final actor's opponent", 1 - recoil.starterOrdinal,
			recoilWitness.getWinnerOrdinal());
		assertTrue("recoil defender committed a charged ring",
			recoilVerification.getContext().getParticipant(1).isRecoilEquipped());

		final ContextParticipantSpec directAttacker = contextParticipant(terminalStrongInput(),
			1, false, 0, 99, 99, 10);
		final ContextParticipantSpec ringedDirectDefender = contextParticipant(
			terminalStrongInput(), 1, true, 40, 99, 99, 11);
		final TerminalFixture directBeforeRecoil = terminalFixture(
			DuelProofSpec.DUEL_RULE_NO_MAGIC, DuelProofSpec.TERMINAL_CAUSE_DIRECT_MELEE,
			terminalStrongInput(), directAttacker, ringedDirectDefender, 0,
			Arrays.asList(terminalStrongInput()));
		assertLong("direct lethal damage has priority over defender recoil", 0L,
			DuelProofTerminalVerifier.verify(directBeforeRecoil.encoded)
				.getWitness().getWinnerOrdinal());

		final ContextParticipantSpec survivesOneRecoil = contextParticipant(
			terminalStrongInput(), 2, false, 0, 99, 99, 10);
		final ContextParticipantSpec oneChargeRing = contextParticipant(
			terminalStrongInput(), 10000, true, 1, 99, 99, 11);
		final TerminalFixture ringShattersBeforeTerminal = terminalFixture(
			DuelProofSpec.DUEL_RULE_NO_MAGIC, DuelProofSpec.TERMINAL_CAUSE_DIRECT_MELEE,
			terminalStrongInput(), survivesOneRecoil, oneChargeRing, 0,
			Arrays.asList(terminalStrongInput(), terminalStrongInput()));
		final DuelProofTerminalVerifier.Verification shatterVerification =
			DuelProofTerminalVerifier.verify(ringShattersBeforeTerminal.encoded);
		assertTrue("pre-terminal positive hit consumes the last recoil charge",
			shatterVerification.getComputedSwings().get(0).getResult().getDamage() > 0);
		assertLong("opponent kills the one-HP survivor after recoil shatters", 1L,
			shatterVerification.getWitness().getWinnerOrdinal());
	}

	private static void testTerminalWitnessTamperRejection() {
		final TerminalFixture fixture = terminalFixture(DuelProofSpec.DUEL_RULE_NO_MAGIC,
			DuelProofSpec.TERMINAL_CAUSE_DIRECT_MELEE, terminalStrongInput());

		expectTerminalInvalid("terminal witness rejects an appended tail",
			Arrays.copyOf(fixture.encoded, fixture.encoded.length + 1));
		expectTerminalInvalid("terminal witness rejects truncation",
			Arrays.copyOf(fixture.encoded, fixture.encoded.length - 1));

		final byte[] changedMagic = Arrays.copyOf(fixture.encoded, fixture.encoded.length);
		changedMagic[0] ^= 1;
		expectTerminalInvalid("terminal witness rejects changed magic", changedMagic);

		final byte[] changedVersion = Arrays.copyOf(fixture.encoded, fixture.encoded.length);
		changedVersion[11] = 3;
		expectTerminalInvalid("terminal witness rejects unsupported witness version", changedVersion);

		final ContextParticipantSpec invalidCombatLevel = contextParticipant(
			terminalStrongInput(), 1, false, 0, 99, 99, 10).withCombatLevel(0);
		final byte[] zeroCombatContext = terminalContext(fixture.witness.getProofId(),
			DuelProofSpec.DUEL_RULE_NO_MAGIC, terminalStrongInput(), invalidCombatLevel,
			contextParticipant(terminalStrongInput(), 1, false, 0, 99, 99, 11));
		expectIllegalArgument("context rejects a zero committed combat level", new Runnable() {
			@Override
			public void run() {
				DuelProofTerminalVerifier.verifyContext(zeroCombatContext);
			}
		});

		final int contextLength = unsignedShort(fixture.encoded, 44);
		final int participantCountOffset = 46 + contextLength + DuelProofSpec.HASH_BYTES * 4;
		final int firstParticipantOffset = participantCountOffset + 1;
		final int firstSeedOffset = firstParticipantOffset + 1 + 4 + DuelProofSpec.HASH_BYTES;
		final int firstAckOffset = firstSeedOffset + DuelProofSpec.SEED_BYTES;
		final int terminalMetadataOffset = firstParticipantOffset + 2 * (1 + 4
			+ DuelProofSpec.HASH_BYTES + DuelProofSpec.SEED_BYTES + DuelProofSpec.HASH_BYTES);
		final int starterOffset = terminalMetadataOffset;
		final int winnerOffset = starterOffset + 1;
		final int finalCandidateCountOffset = starterOffset + 3 + 8 + 4;
		final int firstSwingOffset = finalCandidateCountOffset + 8;

		final byte[] changedSeed = Arrays.copyOf(fixture.encoded, fixture.encoded.length);
		changedSeed[firstSeedOffset] ^= 1;
		expectTerminalInvalid("terminal witness rejects a changed participant seed", changedSeed);

		final byte[] changedAck = Arrays.copyOf(fixture.encoded, fixture.encoded.length);
		changedAck[firstAckOffset] ^= 1;
		expectTerminalInvalid("terminal witness rejects a changed participant lock ack", changedAck);

		final byte[] changedStarter = Arrays.copyOf(fixture.encoded, fixture.encoded.length);
		changedStarter[starterOffset] = (byte) (1 - fixture.starterOrdinal);
		expectTerminalInvalid("terminal witness rejects a changed starter", changedStarter);

		final byte[] changedWinner = Arrays.copyOf(fixture.encoded, fixture.encoded.length);
		changedWinner[winnerOffset] = (byte) (1 - fixture.starterOrdinal);
		expectTerminalInvalid("terminal witness rejects direct winner inversion", changedWinner);

		final byte[] changedCandidateCount = Arrays.copyOf(fixture.encoded, fixture.encoded.length);
		changedCandidateCount[finalCandidateCountOffset + 7] ^= 1;
		expectTerminalInvalid("terminal witness rejects changed final candidate count",
			changedCandidateCount);

		final byte[] changedActor = Arrays.copyOf(fixture.encoded, fixture.encoded.length);
		changedActor[firstSwingOffset + 4] = (byte) (1 - fixture.starterOrdinal);
		expectTerminalInvalid("terminal witness rejects changed swing actor", changedActor);

		final byte[] reservedBoolean = Arrays.copyOf(fixture.encoded, fixture.encoded.length);
		reservedBoolean[reservedBoolean.length - 1] = 2;
		expectTerminalInvalid("terminal witness rejects a reserved boolean", reservedBoolean);

		final TerminalFixture magicAllowed = terminalFixture(0,
			DuelProofSpec.TERMINAL_CAUSE_DIRECT_MELEE, terminalStrongInput());
		expectTerminalInvalid("terminal witness requires the No Magic context rule",
			magicAllowed.encoded);

		final TerminalFixture zeroDamage = terminalFixture(DuelProofSpec.DUEL_RULE_NO_MAGIC,
			DuelProofSpec.TERMINAL_CAUSE_DIRECT_MELEE, terminalZeroDamageInput());
		expectTerminalInvalid("terminal witness rejects a non-damaging final swing",
			zeroDamage.encoded);

		final DuelProofMeleeInput changedStaticInput = new DuelProofMeleeInput(
			DuelProofSpec.COMBAT_STYLE_AGGRESSIVE, DuelProofSpec.COMBAT_STYLE_DEFENSIVE,
			98, 99, 1, DuelProofSpec.PRAYER_TIER_HIGH,
			DuelProofSpec.PRAYER_TIER_MIDDLE, DuelProofSpec.PRAYER_TIER_LOW,
			1000, 1000, 0, true, true, true);
		final ContextParticipantSpec lethalFirst = contextParticipant(terminalStrongInput(),
			1, false, 0, 99, 99, 10);
		final ContextParticipantSpec lethalSecond = contextParticipant(terminalStrongInput(),
			1, false, 0, 99, 99, 11);
		final TerminalFixture changedStatic = terminalFixture(DuelProofSpec.DUEL_RULE_NO_MAGIC,
			DuelProofSpec.TERMINAL_CAUSE_DIRECT_MELEE, terminalStrongInput(),
			lethalFirst, lethalSecond, -1, Arrays.asList(changedStaticInput));
		expectTerminalInvalid("terminal witness rejects formula state not locked by context",
			changedStatic.encoded);

		final ContextParticipantSpec durableFirst = contextParticipant(terminalStrongInput(),
			10000, false, 0, 99, 99, 10);
		final ContextParticipantSpec durableSecond = contextParticipant(terminalStrongInput(),
			10000, false, 0, 99, 99, 11);
		final TerminalFixture positivePrefix = terminalFixture(DuelProofSpec.DUEL_RULE_NO_MAGIC,
			DuelProofSpec.TERMINAL_CAUSE_DIRECT_MELEE, terminalStrongInput(),
			durableFirst, durableSecond, -1, Arrays.asList(terminalStrongInput()));
		expectTerminalInvalid("terminal witness rejects a positive non-lethal prefix",
			positivePrefix.encoded);

		final TerminalFixture earlyDeath = terminalFixture(DuelProofSpec.DUEL_RULE_NO_MAGIC,
			DuelProofSpec.TERMINAL_CAUSE_DIRECT_MELEE, terminalStrongInput(),
			lethalFirst, lethalSecond, -1,
			Arrays.asList(terminalStrongInput(), terminalStrongInput()));
		expectTerminalInvalid("terminal witness rejects swings after an earlier death",
			earlyDeath.encoded);

		final ContextParticipantSpec lowAttacker = contextParticipant(terminalStrongInput(),
			1, false, 0, 99, 99, 10);
		final ContextParticipantSpec unringedDefender = contextParticipant(terminalStrongInput(),
			10000, false, 0, 99, 99, 11);
		final TerminalFixture recoilWithoutRing = terminalFixture(
			DuelProofSpec.DUEL_RULE_NO_MAGIC, DuelProofSpec.TERMINAL_CAUSE_RECOIL,
			terminalStrongInput(), lowAttacker, unringedDefender, 0,
			Arrays.asList(terminalStrongInput()));
		expectTerminalInvalid("terminal witness rejects recoil without a charged ring",
			recoilWithoutRing.encoded);

		final ContextParticipantSpec capacityWithoutRing = contextParticipant(
			terminalStrongInput(), 1, false, 1, 99, 99, 10);
		final TerminalFixture invalidRecoilContext = terminalFixture(
			DuelProofSpec.DUEL_RULE_NO_MAGIC, DuelProofSpec.TERMINAL_CAUSE_DIRECT_MELEE,
			terminalStrongInput(), capacityWithoutRing, lethalSecond, -1,
			Arrays.asList(terminalStrongInput()));
		expectTerminalInvalid("context rejects recoil capacity without the explicit ring flag",
			invalidRecoilContext.encoded);

		final ContextParticipantSpec unauthenticatedRingFlag = new ContextParticipantSpec(
			99, 1, false, true, 40, 99, 99, 10);
		final TerminalFixture ringFlagWithoutGear = terminalFixture(
			DuelProofSpec.DUEL_RULE_NO_MAGIC, DuelProofSpec.TERMINAL_CAUSE_DIRECT_MELEE,
			terminalStrongInput(), unauthenticatedRingFlag, lethalSecond, -1,
			Arrays.asList(terminalStrongInput()));
		expectTerminalInvalid("context rejects a recoil flag detached from committed gear",
			ringFlagWithoutGear.encoded);

		final ContextParticipantSpec unflaggedRingGear = new ContextParticipantSpec(
			99, 1, true, false, 0, 99, 99, 10);
		final TerminalFixture ringGearWithoutFlag = terminalFixture(
			DuelProofSpec.DUEL_RULE_NO_MAGIC, DuelProofSpec.TERMINAL_CAUSE_DIRECT_MELEE,
			terminalStrongInput(), unflaggedRingGear, lethalSecond, -1,
			Arrays.asList(terminalStrongInput()));
		expectTerminalInvalid("context rejects committed recoil gear without its flag",
			ringGearWithoutFlag.encoded);

		final TerminalFixture noPrayerViolation = terminalFixture(
			DuelProofSpec.DUEL_RULE_NO_MAGIC | DuelProofSpec.DUEL_RULE_NO_PRAYER,
			DuelProofSpec.TERMINAL_CAUSE_DIRECT_MELEE, terminalStrongInput(),
			lethalFirst, lethalSecond, -1, Arrays.asList(terminalStrongInput()));
		expectTerminalInvalid("No Prayer witness rejects prayer tiers",
			noPrayerViolation.encoded);

		final ContextParticipantSpec impossiblePrayerFirst = contextParticipant(
			terminalStrongInput(), 1, false, 0, 1, 1, 10);
		final ContextParticipantSpec impossiblePrayerSecond = contextParticipant(
			terminalStrongInput(), 1, false, 0, 1, 1, 11);
		final TerminalFixture impossiblePrayer = terminalFixture(
			DuelProofSpec.DUEL_RULE_NO_MAGIC, DuelProofSpec.TERMINAL_CAUSE_DIRECT_MELEE,
			terminalStrongInput(), impossiblePrayerFirst, impossiblePrayerSecond, -1,
			Arrays.asList(terminalStrongInput()));
		expectTerminalInvalid("witness rejects prayer tiers above committed Prayer",
			impossiblePrayer.encoded);

		expectTerminalInvalid("terminal witness rejects bytes beyond the 256 KiB cap",
			new byte[DuelProofSpec.MAX_WITNESS_BYTES + 1]);
		expectIllegalArgument("terminal witness rejects context beyond 65535 bytes", new Runnable() {
			@Override
			public void run() {
				DuelProofTerminalWitness.createV2(fixture.witness.getProofId(),
					new byte[DuelProofSpec.MAX_WITNESS_CONTEXT_BYTES + 1],
					fixture.witness.getContextHash(), fixture.witness.getServerCommitment(),
					fixture.witness.getServerSeed(), fixture.witness.getFinalLockHash(),
					fixture.witness.getParticipants(), fixture.witness.getStarterOrdinal(),
					fixture.witness.getWinnerOrdinal(), fixture.witness.getTerminalCause(),
					fixture.witness.getFinishedAtMs(), fixture.witness.getFinalCandidateDrawCount(),
					fixture.witness.getSwings());
			}
		});
		final List<DuelProofTerminalSwing> excessiveSwings =
			new ArrayList<DuelProofTerminalSwing>(DuelProofSpec.MAX_WITNESS_SWINGS + 1);
		for (int number = 1; number <= DuelProofSpec.MAX_WITNESS_SWINGS + 1; number++) {
			excessiveSwings.add(new DuelProofTerminalSwing(number, number & 1,
				terminalStrongInput()));
		}
		expectIllegalArgument("terminal witness rejects more than 4096 swings", new Runnable() {
			@Override
			public void run() {
				DuelProofTerminalWitness.createV2(fixture.witness.getProofId(),
					fixture.witness.getContextBytes(), fixture.witness.getContextHash(),
					fixture.witness.getServerCommitment(), fixture.witness.getServerSeed(),
					fixture.witness.getFinalLockHash(), fixture.witness.getParticipants(),
					fixture.witness.getStarterOrdinal(), fixture.witness.getWinnerOrdinal(),
					fixture.witness.getTerminalCause(), fixture.witness.getFinishedAtMs(),
					fixture.witness.getFinalCandidateDrawCount(), excessiveSwings);
			}
		});
	}

	private static TerminalFixture terminalFixture(final int ruleMask, final int terminalCause,
											 final DuelProofMeleeInput terminalInput) {
		final ContextParticipantSpec first;
		final ContextParticipantSpec second;
		final int desiredStarter;
		if (terminalCause == DuelProofSpec.TERMINAL_CAUSE_RECOIL) {
			first = contextParticipant(terminalInput, 1, false, 0, 99, 99, 10);
			second = contextParticipant(terminalInput, 10000, true, 40, 99, 99, 11);
			desiredStarter = 0;
		} else {
			first = contextParticipant(terminalInput, 1, false, 0, 99, 99, 10);
			second = contextParticipant(terminalInput, 1, false, 0, 99, 99, 11);
			desiredStarter = -1;
		}
		return terminalFixture(ruleMask, terminalCause, terminalInput, first, second,
			desiredStarter, Arrays.asList(terminalInput));
	}

	private static TerminalFixture terminalFixture(final int ruleMask, final int terminalCause,
											 final DuelProofMeleeInput contextInput,
											 final ContextParticipantSpec firstContext,
											 final ContextParticipantSpec secondContext,
											 final int desiredStarter,
											 final List<DuelProofMeleeInput> swingInputs) {
		return terminalFixture(ruleMask, terminalCause, contextInput, firstContext,
			secondContext, desiredStarter, -1, swingInputs);
	}

	private static TerminalFixture terminalFixture(final int ruleMask, final int terminalCause,
											 final DuelProofMeleeInput contextInput,
											 final ContextParticipantSpec firstContext,
											 final ContextParticipantSpec secondContext,
											 final int desiredStarter,
											 final int desiredStarterDraw,
											 final List<DuelProofMeleeInput> swingInputs) {
		final byte[] firstSeed = sequence(0x20);
		final byte[] secondSeed = sequence(0x40);
		final byte[] proofId = Arrays.copyOf(sequence(0x60), DuelProofSpec.PROOF_ID_BYTES);
		final byte[] contextBytes = terminalContext(proofId, ruleMask, contextInput,
			firstContext, secondContext);
		final byte[] contextHash = DuelProofCrypto.contextHash(contextBytes);
		final byte[] serverSeed = findServerSeed(contextHash, firstSeed, secondSeed,
			desiredStarter, desiredStarterDraw, firstContext.combatLevel,
			secondContext.combatLevel);
		final byte[] serverCommitment = DuelProofCrypto.serverCommitment(contextHash, serverSeed);
		final byte[] firstCommitment = DuelProofCrypto.clientCommitment(contextHash,
			serverCommitment, 0, firstSeed);
		final byte[] secondCommitment = DuelProofCrypto.clientCommitment(contextHash,
			serverCommitment, 1, secondSeed);
		final byte[] finalLock = DuelProofCrypto.finalLockHash(proofId, contextHash,
			serverCommitment, 7, firstCommitment, 42, secondCommitment);
		final byte[] masterSeed = DuelProofCrypto.masterSeed(contextHash, serverSeed,
			firstSeed, secondSeed);
		final DuelProofMeleeReplay replay = new DuelProofMeleeReplay(masterSeed);
		final int starterOrdinal = replay.chooseStarterOrdinal(
			firstContext.combatLevel, secondContext.combatLevel);
		final int starterDraw = (int) replay.getStarterDraw().getValue();
		final List<DuelProofTerminalSwing> swings =
			new ArrayList<DuelProofTerminalSwing>(swingInputs.size());
		for (int index = 0; index < swingInputs.size(); index++) {
			final int actorOrdinal = index % 2 == 0 ? starterOrdinal : 1 - starterOrdinal;
			replay.resolveSwing(actorOrdinal, swingInputs.get(index));
			swings.add(new DuelProofTerminalSwing(index + 1, actorOrdinal,
				swingInputs.get(index)));
		}
		final long finalCandidateDrawCount = replay.getCandidateDrawCount();
		replay.destroy();
		Arrays.fill(masterSeed, (byte) 0);

		final List<DuelProofTerminalParticipant> participants = Arrays.asList(
			new DuelProofTerminalParticipant(0, 7, firstCommitment, firstSeed, finalLock),
			new DuelProofTerminalParticipant(1, 42, secondCommitment, secondSeed, finalLock));
		final int terminalActor = swings.get(swings.size() - 1).getActorOrdinal();
		final int winnerOrdinal = terminalCause == DuelProofSpec.TERMINAL_CAUSE_DIRECT_MELEE
			? terminalActor : 1 - terminalActor;
		final DuelProofTerminalWitness witness = DuelProofTerminalWitness.createV2(proofId,
			contextBytes, contextHash, serverCommitment, serverSeed, finalLock, participants,
			starterOrdinal, winnerOrdinal, terminalCause, 1730000000123L,
			finalCandidateDrawCount, swings);
		return new TerminalFixture(witness, DuelProofTerminalWitnessCodec.encode(witness),
			starterOrdinal, starterDraw, finalCandidateDrawCount);
	}

	private static byte[] findServerSeed(final byte[] contextHash, final byte[] firstSeed,
										 final byte[] secondSeed, final int desiredStarter,
										 final int desiredStarterDraw,
										 final int firstCombatLevel,
										 final int secondCombatLevel) {
		final byte[] serverSeed = sequence(0x00);
		if (desiredStarter == -1 && desiredStarterDraw == -1) {
			return serverSeed;
		}
		for (int candidate = 0; candidate < 256; candidate++) {
			serverSeed[0] = (byte) candidate;
			final byte[] masterSeed = DuelProofCrypto.masterSeed(contextHash, serverSeed,
				firstSeed, secondSeed);
			final DuelProofMeleeReplay replay = new DuelProofMeleeReplay(masterSeed);
			final int starter = replay.chooseStarterOrdinal(firstCombatLevel, secondCombatLevel);
			final int starterDraw = (int) replay.getStarterDraw().getValue();
			replay.destroy();
			Arrays.fill(masterSeed, (byte) 0);
			if ((desiredStarter == -1 || starter == desiredStarter)
				&& (desiredStarterDraw == -1 || starterDraw == desiredStarterDraw)) {
				return serverSeed;
			}
		}
		throw new AssertionError("unable to find deterministic starter seed");
	}

	private static byte[] terminalContext(final byte[] proofId, final int ruleMask,
										  final DuelProofMeleeInput contextInput,
										  final ContextParticipantSpec first,
										  final ContextParticipantSpec second) {
		try {
			final ByteArrayOutputStream bytes = new ByteArrayOutputStream(512);
			final DataOutputStream output = new DataOutputStream(bytes);
			output.write(DuelProofCodec.ascii("VSDPCTX3"));
			output.writeInt(DuelProofSpec.CONTEXT_VERSION);
			output.writeInt(DuelProofSpec.PROTOCOL_VERSION);
			output.writeInt(DuelProofSpec.RNG_VERSION);
			output.writeInt(DuelProofSpec.CLASSIC_MELEE_FORMULA_VERSION);
			output.write(proofId);
			output.writeByte(ruleMask);
			output.writeInt(40);
			writeTerminalContextParticipant(output, 0, 7, "alpha", contextInput, first);
			writeTerminalContextParticipant(output, 1, 42, "beta", contextInput, second);
			output.flush();
			return bytes.toByteArray();
		} catch (final IOException impossible) {
			throw new AssertionError(impossible);
		}
	}

	private static void writeTerminalContextParticipant(final DataOutputStream output,
												final int ordinal, final int playerId,
												final String username,
												final DuelProofMeleeInput contextInput,
												final ContextParticipantSpec context)
		throws IOException {
		output.writeByte(ordinal);
		output.writeInt(playerId);
		final byte[] usernameBytes = DuelProofCodec.ascii(username);
		output.writeByte(usernameBytes.length);
		output.write(usernameBytes);
		output.writeInt(context.combatLevel);
		writeSkill(output, contextInput.getAttackerAttackLevel(),
			Math.max(1, contextInput.getAttackerAttackLevel()));
		writeSkill(output, contextInput.getDefenderDefenceLevel(),
			Math.max(1, contextInput.getDefenderDefenceLevel()));
		writeSkill(output, contextInput.getAttackerStrengthLevel(),
			Math.max(1, contextInput.getAttackerStrengthLevel()));
		writeSkill(output, context.hits, Math.max(context.hits, 99));
		writeSkill(output, context.currentPrayer, context.maximumPrayer);
		output.writeByte(DuelProofSpec.COMBAT_STYLE_CONTROLLED);
		output.writeInt(contextInput.getAttackerWeaponAimPoints());
		output.writeInt(contextInput.getAttackerWeaponPowerPoints());
		output.writeInt(contextInput.getDefenderArmourPoints());
		output.writeBoolean(contextInput.isAttackCapeEligible());
		output.writeBoolean(contextInput.isStrengthCapeEligible());
		output.writeBoolean(contextInput.isDefenceCapeEligible());
		output.writeInt(0);
		output.writeByte(14);
		for (int slot = 0; slot < 14; slot++) {
			output.writeInt(context.auditRecoilItem && slot == 13 ? 1314 : -1);
			output.writeInt(context.auditRecoilItem && slot == 13 ? 1 : 0);
			output.writeBoolean(false);
		}
		output.writeBoolean(context.recoilEquipped);
		output.writeInt(context.recoilRemaining);
		output.writeByte(1);
		output.writeByte(0);
		output.writeInt(context.stakeItemId);
		output.writeInt(100);
		output.writeBoolean(false);
	}

	private static void writeSkill(final DataOutputStream output, final int current,
									final int maximum) throws IOException {
		output.writeInt(current);
		output.writeInt(maximum);
	}

	private static ContextParticipantSpec contextParticipant(
		final DuelProofMeleeInput input, final int hits, final boolean recoilEquipped,
		final int recoilRemaining, final int currentPrayer, final int maximumPrayer,
		final int stakeItemId) {
		return new ContextParticipantSpec(99, hits, recoilEquipped, recoilEquipped, recoilRemaining,
			currentPrayer, maximumPrayer, stakeItemId);
	}

	private static DuelProofMeleeInput terminalStrongInput() {
		return new DuelProofMeleeInput(DuelProofSpec.COMBAT_STYLE_AGGRESSIVE,
			DuelProofSpec.COMBAT_STYLE_DEFENSIVE, 99, 99, 1,
			DuelProofSpec.PRAYER_TIER_HIGH, DuelProofSpec.PRAYER_TIER_MIDDLE,
			DuelProofSpec.PRAYER_TIER_LOW, 1000, 1000, 0, true, true, true);
	}

	private static DuelProofMeleeInput terminalZeroDamageInput() {
		return new DuelProofMeleeInput(DuelProofSpec.COMBAT_STYLE_ACCURATE,
			DuelProofSpec.COMBAT_STYLE_CONTROLLED, 99, 0, 0,
			DuelProofSpec.PRAYER_TIER_NONE, DuelProofSpec.PRAYER_TIER_NONE,
			DuelProofSpec.PRAYER_TIER_NONE, 1000, -64, 1, false, false, false);
	}

	private static final class ContextParticipantSpec {
		private final int combatLevel;
		private final int hits;
		private final boolean auditRecoilItem;
		private final boolean recoilEquipped;
		private final int recoilRemaining;
		private final int currentPrayer;
		private final int maximumPrayer;
		private final int stakeItemId;

		private ContextParticipantSpec(final int combatLevel, final int hits,
								   final boolean auditRecoilItem,
								   final boolean recoilEquipped, final int recoilRemaining,
									   final int currentPrayer,
									   final int maximumPrayer, final int stakeItemId) {
			this.combatLevel = combatLevel;
			this.hits = hits;
			this.auditRecoilItem = auditRecoilItem;
			this.recoilEquipped = recoilEquipped;
			this.recoilRemaining = recoilRemaining;
			this.currentPrayer = currentPrayer;
			this.maximumPrayer = maximumPrayer;
			this.stakeItemId = stakeItemId;
		}

		private ContextParticipantSpec withCombatLevel(final int value) {
			return new ContextParticipantSpec(value, hits, auditRecoilItem, recoilEquipped,
				recoilRemaining, currentPrayer, maximumPrayer, stakeItemId);
		}
	}

	private static int unsignedShort(final byte[] bytes, final int offset) {
		return ((bytes[offset] & 0xff) << 8) | (bytes[offset + 1] & 0xff);
	}

	private static void expectTerminalInvalid(final String label, final byte[] bytes) {
		expectIllegalArgument(label, new Runnable() {
			@Override
			public void run() {
				DuelProofTerminalVerifier.verify(bytes);
			}
		});
	}

	private static final class TerminalFixture {
		private final DuelProofTerminalWitness witness;
		private final byte[] encoded;
		private final int starterOrdinal;
		private final int starterDraw;
		private final long finalCandidateDrawCount;

		private TerminalFixture(final DuelProofTerminalWitness witness, final byte[] encoded,
								final int starterOrdinal, final int starterDraw,
								final long finalCandidateDrawCount) {
			this.witness = witness;
			this.encoded = encoded;
			this.starterOrdinal = starterOrdinal;
			this.starterDraw = starterDraw;
			this.finalCandidateDrawCount = finalCandidateDrawCount;
		}
	}

	private static void testDestroyedStreams() {
		final DuelProofRng random = new DuelProofRng(new byte[32]);
		random.nextCoin();
		random.destroy();
		expectIllegalState("destroyed RNG", new Runnable() {
			@Override
			public void run() {
				random.nextCoin();
			}
		});

		final DuelProofMeleeReplay replay = new DuelProofMeleeReplay(new byte[32]);
		expectIllegalState("starter getter before selection", new Runnable() {
			@Override
			public void run() {
				replay.getStarterOrdinal();
			}
		});
		expectIllegalState("starter draw getter before selection", new Runnable() {
			@Override
			public void run() {
				replay.getStarterDraw();
			}
		});
		replay.chooseStarterOrdinal(99, 99);
		replay.destroy();
		expectIllegalState("destroyed replay", new Runnable() {
			@Override
			public void run() {
				replay.getSwings();
			}
		});
	}

	private static void testCodecAndCanonicalStarter() {
		byte[] bytes = {0x00, 0x0f, (byte) 0xa5, (byte) 0xff};
		String encoded = DuelProofCodec.hexLower(bytes);
		assertString("lowercase hex", "000fa5ff", encoded);
		if (!DuelProofCrypto.constantTimeEquals(bytes,
			DuelProofCodec.parseHexLowerExact(encoded, bytes.length))) {
			throw new AssertionError("hex round trip did not match");
		}
		if (DuelProofCrypto.constantTimeEquals(bytes, new byte[] {0x00, 0x0f, (byte) 0xa4, (byte) 0xff})) {
			throw new AssertionError("different values compared equal");
		}
		if (DuelProofCrypto.constantTimeEquals(bytes, new byte[] {0x00})) {
			throw new AssertionError("different lengths compared equal");
		}
		expectIllegalArgument("uppercase hex", new Runnable() {
			@Override
			public void run() {
				DuelProofCodec.parseHexLowerExact("AA", 1);
			}
		});

		assertLong("canonical first forward", 7L, DuelProofSpec.canonicalFirstPlayerId(42, 7));
		assertLong("canonical first reverse", 7L, DuelProofSpec.canonicalFirstPlayerId(7, 42));
		assertLong("lower-level first player ignores tie bit one", 42L,
			DuelProofSpec.starterPlayerId(42, 20, 7, 80, 1));
		assertLong("lower-level first player survives reversed id order", 42L,
			DuelProofSpec.starterPlayerId(7, 80, 42, 20, 1));
		assertLong("lower-level second player ignores tie bit zero", 7L,
			DuelProofSpec.starterPlayerId(42, 80, 7, 20, 0));
		assertLong("lower-level second player survives reversed id order", 7L,
			DuelProofSpec.starterPlayerId(7, 20, 42, 80, 0));
		assertLong("equal-level bit zero maps to canonical lower id", 7L,
			DuelProofSpec.starterPlayerId(42, 50, 7, 50, 0));
		assertLong("equal-level bit one maps to canonical higher id", 42L,
			DuelProofSpec.starterPlayerId(7, 50, 42, 50, 1));
		assertLong("equal-level canonical mapping survives reversed arguments", 42L,
			DuelProofSpec.starterPlayerId(42, 50, 7, 50, 1));
		expectIllegalArgument("invalid starter bit", new Runnable() {
			@Override
			public void run() {
				DuelProofSpec.starterPlayerId(7, 50, 42, 50, 2);
			}
		});
		expectIllegalArgument("invalid committed combat level", new Runnable() {
			@Override
			public void run() {
				DuelProofSpec.starterPlayerId(7, 0, 42, 50, 0);
			}
		});
		expectIllegalArgument("invalid client ordinal", new Runnable() {
			@Override
			public void run() {
				DuelProofCrypto.clientCommitment(new byte[32], new byte[32], 2, new byte[32]);
			}
		});
	}

	private static void assertRepeatedSha(int length, String expected) {
		byte[] message = new byte[length];
		Arrays.fill(message, (byte) 'a');
		assertHex("sha256 a x " + length, expected, DuelProofCrypto.sha256(message));
	}

	private static byte[] sequence(int start) {
		byte[] bytes = new byte[32];
		for (int i = 0; i < bytes.length; i++) {
			bytes[i] = (byte) (start + i);
		}
		return bytes;
	}

	private static void assertHex(String label, String expected, byte[] actual) {
		assertString(label, expected, DuelProofCodec.hexLower(actual));
	}

	private static void assertString(String label, String expected, String actual) {
		if (!expected.equals(actual)) {
			throw new AssertionError(label + ": expected " + expected + " but got " + actual);
		}
	}

	private static void assertLong(String label, long expected, long actual) {
		if (expected != actual) {
			throw new AssertionError(label + ": expected " + expected + " but got " + actual);
		}
	}

	private static long capeBucket(final DuelProofDraw draw) {
		return (int) ((draw.getValue() / 9007199254740992.0) * 99) + 1;
	}

	private static void assertDrawTypes(final String label, final DuelProofMeleeSwing swing,
									final int... expectedTypes) {
		if (swing.getDraws().size() != expectedTypes.length) {
			throw new AssertionError(label + ": expected " + expectedTypes.length
				+ " draws but got " + swing.getDraws().size());
		}
		for (int i = 0; i < expectedTypes.length; i++) {
			assertLong(label + " draw " + i, expectedTypes[i], swing.getDraws().get(i).getType());
		}
	}

	private static void assertTrue(final String label, final boolean value) {
		if (!value) {
			throw new AssertionError(label + ": expected true");
		}
	}

	private static void assertFalse(final String label, final boolean value) {
		if (value) {
			throw new AssertionError(label + ": expected false");
		}
	}

	private static void expectIllegalArgument(String label, Runnable action) {
		try {
			action.run();
			throw new AssertionError(label + ": expected IllegalArgumentException");
		} catch (IllegalArgumentException expected) {
			// Expected.
		}
	}

	private static void expectIllegalState(final String label, final Runnable action) {
		try {
			action.run();
			throw new AssertionError(label + ": expected IllegalStateException");
		} catch (IllegalStateException expected) {
			// Expected.
		}
	}

	private static void expectUnsupported(final String label, final Runnable action) {
		try {
			action.run();
			throw new AssertionError(label + ": expected UnsupportedOperationException");
		} catch (UnsupportedOperationException expected) {
			// Expected.
		}
	}
}
