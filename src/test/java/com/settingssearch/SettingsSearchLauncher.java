package com.settingssearch;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public final class SettingsSearchLauncher
{
	@SuppressWarnings("unchecked")
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(SettingsSearchPlugin.class);
		RuneLite.main(args);
	}
}
