package orsc.util;

public class BankUtil {
	private static final int[] CERT_IDS = {
		/* Ores **/
		517, 518, 519, 520, 521,
		/* Bars **/
		528, 529, 530, 531, 532,
		/* Fish **/
		533, 534, 535, 536, 628, 629, 630, 631,
		/* Logs **/
		711, 712, 713,
		/* Misc **/
		1270, 1271, 1272, 1273, 1274, 1275
	};

	public static boolean isCert(int itemID) {
		for (int id : CERT_IDS) {
			if (id == itemID) {
				return true;
			}
		}
		return false;
	}

	public static int uncertedID(int itemID) {
		switch (itemID) {
			case 517: return 151;
			case 518: return 155;
			case 519: return 153;
			case 520: return 383;
			case 521: return 152;
			case 528: return 170;
			case 529: return 171;
			case 530: return 173;
			case 531: return 384;
			case 532: return 172;
			case 533: return 373;
			case 534: return 372;
			case 535: return 370;
			case 536: return 369;
			case 628: return 555;
			case 629: return 554;
			case 630: return 546;
			case 631: return 545;
			case 711: return 635;
			case 712: return 634;
			case 713: return 633;
			case 1270: return 814;
			case 1271: return 220;
			case 1272: return 483;
			case 1273: return 486;
			case 1274: return 495;
			case 1275: return 492;
			default: return itemID;
		}
	}
}
