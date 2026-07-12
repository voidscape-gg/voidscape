package orsc;

public final class CrackerCampaignHudMessageTest {
	private CrackerCampaignHudMessageTest() {
	}

	public static void main(String[] args) {
		assertEquals(Integer.MIN_VALUE,
			mudclient.parseVoidscapeCrackerCampaignRemaining(null), "null is not an envelope");
		assertEquals(Integer.MIN_VALUE,
			mudclient.parseVoidscapeCrackerCampaignRemaining("ordinary chat"),
			"ordinary chat is not an envelope");

		assertEquals(0, parse("v1|0"), "zero hides");
		assertEquals(1, parse("v1|1"), "one displays");
		assertEquals(999, parse("v1|999"), "999 displays");
		assertEquals(1000, parse("v1|1000"), "1000 displays");
		assertEquals(1_000_000, parse("v1|1000000"), "maximum displays");

		for (String invalid : new String[]{
			"v2|1000", "v1|-1", "v1|1000001", "v1|2147483648", "v1|",
			"v1|abc", "v1| 1", "v1|1|extra", "garbage"
		}) {
			assertEquals(0, parse(invalid), "invalid payload hides: " + invalid);
		}

		System.out.println("Cracker campaign HUD envelope parser tests passed.");
	}

	private static int parse(String payload) {
		return mudclient.parseVoidscapeCrackerCampaignRemaining("@vscrackercampaign@" + payload);
	}

	private static void assertEquals(int expected, int actual, String label) {
		if (expected != actual) {
			throw new AssertionError(label + ": expected " + expected + ", got " + actual);
		}
	}
}
