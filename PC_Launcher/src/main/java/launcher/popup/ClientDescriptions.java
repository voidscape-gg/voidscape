package launcher.popup;

import launcher.elements.ClientSettingsCard;

import javax.swing.*;
import java.util.HashMap;

public class ClientDescriptions {
	private static boolean initted = false;
	public static HashMap<String, JLabel> preservationClientDescriptions = new HashMap<String, JLabel>();
	public static HashMap<String, JLabel> cabbageClientDescriptions = new HashMap<String, JLabel>();
	public static HashMap<String, JLabel> O1scapeClientDescriptions = new HashMap<String, JLabel>();
	public static HashMap<String, JLabel> openpkClientDescriptions = new HashMap<String, JLabel>();
	public static HashMap<String, JLabel> uraniumClientDescriptions = new HashMap<String, JLabel>();
	public static HashMap<String, JLabel> coleslawClientDescriptions = new HashMap<String, JLabel>();

	public static JLabel unknownClient = new JLabel("<html><font color=\"white\">??? please update ClientDescriptions.java for this client/server combo</font></html>");

	public static void init() {
		if (initted) return;
		initted = true;

		// RSCP & RSC Uranium explanations
		JLabel winruneGeneralExplanation = new JLabel(
			"<html><font color=\"white\">WinRune is a wrapper for original RuneScape Classic clients.<br/><br/>" +
				"This means that it takes a regular client, in this case mudclient 177,<br/>" +
				"and puts it in a picture frame, in this case designed to look like<br/>" +
				"the same desktop exe wrapper that Jagex used back in 2003.<br/><br/>" +
				"Using this client, you will have a near identical experience to what<br/>" +
				"RuneScape players in 2003 ran on their computers.</font></html>"
		);

		JLabel rscplusGeneralExplanation = new JLabel(
			"<html><font color=\"white\">This legacy resizable client path is kept for players<br/>" +
				"who prefer a larger local window and client-side convenience tools.<br/><br/>" +
				"Voidscape's default launcher path remains the supported route.<br/>" +
				"If a legacy option is unavailable or behaves unexpectedly,<br/>" +
				"use the standard Voidscape client from Play.</font></html>"
		);

		JLabel webclientGeneralExplanation = new JLabel(
			"<html><font color=\"white\">RuneScape began in the web browser. Its accessibility in-browser<br/>" +
				"is a huge part of why the game became so popular. Unfortunately,<br/>" +
				"around 2015 many of the technologies necessary for this original<br/>" +
				"experience became unsupported and harder for users to access.<br/><br/>" +
				"Thanks to the efforts of many people, we are able to offer this<br/>" +
				"original experience again by converting the original client<br/>" +
				"(mudclient 177 from 2003-10-31) into javascript & webasm.</font></html>"
		);

		JLabel openrscNotRecommendedForAuthenticServers = new JLabel(
			"<html><font color=\"white\">This legacy client path exists for compatibility testing.<br/><br/>" +
				"It is <font color=\"red\">not recommended for normal Voidscape play,</font><br/>" +
				"because the supported launcher path has the current Voidscape<br/>" +
				"configuration, cache, and update behavior.</font></html>"
		);

		JLabel aposbotGeneralExplanation = new JLabel(
			"<html><font color=\"yellow\">Partial quotation from APOSbot's src release README (2016):</font><font color=\"white\"><br/> " +
				"\"APOS is a RuneScape Classic bot created by RLN and friends in 2009,<br/>" +
				"and released on wartnet.org. It was nearly entirely rewritten by<br/>" +
				"me from 2011-2016 on the new site aposbot.com after Wartnet vanished,<br/>" +
				"but later aposbot.com vanished in 2016 along with RLN. The lesson?<br/>" +
				"Websites don't last forever, so hopefully this message lasts in<br/>" +
				"people's minds. [...]<br/><br/>" +
				"\"The APOS code itself? It does the job, it's simple, and that's all<br/>" +
				"I can really say. It could be a lot better, but why? This is RSC.<br/>" +
				"You want something fast and nasty. A legacy bot for a legacy game.<br/>" +
				"It reflects on a lot of RSC heritage, having method names that<br/>" +
				"trace back to the original AutoRune released in 2002 by Kaitnieks<br/>" +
				"(AtObject), as well as Reines' STS (released in 2004). [...]</font><br/>" +
				"<font color=\"yellow\">\"Cheers. This has been Storm.\"</font></html>"

		);

		JLabel mudclient38GeneralExplanation = new JLabel(
			"<html><font color=\"white\">Mudclient38 (8 May 2001) is the last client from Jagex to have the<br/>" +
				"concepts of GoodMagic &amp; EvilMagic, GoodPrayer &amp; EvilPrayer,<br/>" +
				"Influence, and several other early concepts that Andrew would<br/>" +
				"remove two days later, with the release of mudclient39.<br/><br/>" +
				"Unfortunately, the real mudclient38 was lost to time, but thanks<br/>" +
				"to ancient fansites and old screenshots, we were able to recreate<br/>" +
				"the client based off of mudclient39, which still survives.</font></html>"
		);

		JLabel rsctimesGeneralExplanation = new JLabel(
			"<html><font color=\"white\">RSC&times; is a 3rd party client based on mudclient38<br/>" +
				"It was introduced for use on the 2001scape server and<br/>" +
				"implements modern client features, such as resizable mode,<br/>" +
				"onto the mudclient38 client.</font></html>"
		);

		JLabel openrscNotRecommendedForAuthentic2001Servers = new JLabel(
			"<html><font color=\"white\">This legacy client path exists for compatibility testing.<br/><br/>" +
				"It is <font color=\"red\">not recommended for normal Voidscape play,</font><br/>" +
				"because it is not the supported client route for this server<br/>" +
				"and may not match the current rules or cache.</font></html>"
		);

		JLabel openrscClientRSCCabbageExplanation = new JLabel(
			"<html><font color=\"white\">This client path is kept for legacy server compatibility.<br/>" +
				"Voidscape players should use the standard Play button so the<br/>" +
				"launcher can apply the current host, cache, and update settings.</font></html>"
		);

		JLabel openrscClientRSCColeslawExplanation = new JLabel(
			"<html><font color=\"white\">This client path is kept for legacy server compatibility.<br/>" +
				"If another legacy option is unavailable, use the standard<br/>" +
				"Voidscape launcher path instead of an unsupported client.</font></html>"
		);

		JLabel idlerscGeneralExplanation = new JLabel(
			"<html><font color=\"white\">IdleRSC development began in April 2020 due to<br/>" +
				"\"a distinct lack of botting clients available for RSC post-closure.\"<br/>" +
				"This would eventually be fixed when RSC Uranium came out with support<br/>" +
				"for APOS, but IdleRSC remains the only botting client compatible with<br/>" +
				"RSC Coleslaw features (such as Runecrafting &amp; Harvesting).<br/><br/>" +
				"IdleRSC uses code injection and reflection to hook into a legacy<br/>" +
				"client. It has its own custom scripting API and also is backwards <br/>" +
				"compatibile with APOS and SBot scripts.<br/><br/>" +
				"</font><font color=\"yellow\">\"The RSC botting scene WILL NEVER DIE!<br/>" +
				"\"IdleRSC is the next iteration after APOS, STS, SBot, and AutoRune!\"<br/>" +
				"&mdash; Dvorak, original author of IdleRSC</font></html>"
		);

		JLabel openrscClientOpenPKExplanation = new JLabel(
			"<html><font color=\"white\">This client path is kept for legacy server compatibility.<br/>" +
				"Special interfaces and object data may not match the current<br/>" +
				"Voidscape cache, so the supported launcher path is preferred.</font></html>"
		);

		JLabel webclient2001scapeExplanation = new JLabel(
			"<html><font color=\"white\">RuneScape began in the web browser. Its accessibility in-browser<br/>" +
				"is a huge part of why the game became so popular. Unfortunately,<br/>" +
				"around 2015 many of the technologies necessary for this original<br/>" +
				"experience became unsupported and harder for users to access.<br/><br/>" +
				"Thanks to the efforts of many people, we are able to offer this<br/>" +
				"original experience again by converting the recreated mudclient 38<br/>" +
				"(from 2001-05-08) into javascript & webasm.</font></html>"
		);

		// Preservation
		preservationClientDescriptions.put(ClientSettingsCard.WINRUNE, winruneGeneralExplanation);
		preservationClientDescriptions.put(ClientSettingsCard.RSCPLUS, rscplusGeneralExplanation);
		preservationClientDescriptions.put(ClientSettingsCard.WEBCLIENT, webclientGeneralExplanation);
		preservationClientDescriptions.put(ClientSettingsCard.OPENRSC, openrscNotRecommendedForAuthenticServers);

		// Cabbage
		cabbageClientDescriptions.put(ClientSettingsCard.OPENRSC, openrscClientRSCCabbageExplanation);

		// 2001scape
		O1scapeClientDescriptions.put(ClientSettingsCard.MUD38, mudclient38GeneralExplanation);
		O1scapeClientDescriptions.put(ClientSettingsCard.RSCTIMES, rsctimesGeneralExplanation);
		O1scapeClientDescriptions.put(ClientSettingsCard.OPENRSC, openrscNotRecommendedForAuthentic2001Servers);
		O1scapeClientDescriptions.put(ClientSettingsCard.WEBCLIENT, webclient2001scapeExplanation);

		// OpenPK
		openpkClientDescriptions.put(ClientSettingsCard.OPENRSC, openrscClientOpenPKExplanation);

		// Uranium
		uraniumClientDescriptions = (HashMap<String, JLabel>)preservationClientDescriptions.clone();
		uraniumClientDescriptions.put(ClientSettingsCard.APOSBOT, aposbotGeneralExplanation);

		// Coleslaw
		coleslawClientDescriptions.put(ClientSettingsCard.OPENRSC, openrscClientRSCColeslawExplanation);
		coleslawClientDescriptions.put(ClientSettingsCard.IDLERSC, idlerscGeneralExplanation);
	}
}
