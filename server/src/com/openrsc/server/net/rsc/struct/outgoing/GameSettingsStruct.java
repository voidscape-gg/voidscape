package com.openrsc.server.net.rsc.struct.outgoing;

import com.openrsc.server.net.rsc.enums.OpcodeOut;
import com.openrsc.server.net.rsc.struct.AbstractStruct;

import java.util.List;

public class GameSettingsStruct extends AbstractStruct<OpcodeOut> {

	public int cameraModeAuto;
	public int mouseButtonOne;
	public int soundDisabled;
	public int playerKiller; // retro rsc
	public int pkChangesLeft; // retro rsc started at 2
	public List<Integer> customOptions; // custom options added
	public int voidPath;
	public int combatExpRateTenths;
	public int skillingExpRateTenths;
	public int totalPlayedSeconds;
	public int hdIntensity;
	public int hdSaturation;
	public int hdBloom;
	public int hdVignette;
	public int hdWaterShimmer;
	public int hdSunlight;
}
