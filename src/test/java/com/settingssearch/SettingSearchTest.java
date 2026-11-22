package com.settingssearch;

import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SettingSearchTest
{
	private final Setting notification = setting("Idle Notification", "Idle Notifier", "Alerts",
		"Warn when you stop interacting", "idleNotification");
	private final Setting descriptionMatch = setting("Screen Flash", "RuneLite", "Notifications",
		"Flash the screen when a notification arrives", "flashNotification");
	private final List<Setting> index = List.of(descriptionMatch, notification);

	@Test
	public void ranksSettingNamesAboveOtherFields()
	{
		assertEquals(List.of(notification, descriptionMatch), SettingSearch.find(index, "notification", true));
		assertEquals(notification, SettingSearch.find(index, "idle notification", true).get(0));
	}

	@Test
	public void combinesWordsFromDifferentFieldsInEitherOrder()
	{
		assertEquals(List.of(notification), SettingSearch.find(index, "alerts notifier interacting", false));
		assertEquals(List.of(descriptionMatch), SettingSearch.find(index, "flash runelite", false));
	}

	@Test
	public void acceptsAdjacentTranspositionsAndNormalTyposOnlyInFuzzyMode()
	{
		assertEquals(notification, SettingSearch.find(index, "notificaiton", true).get(0));
		assertEquals(List.of(notification), SettingSearch.find(index, "notifierr", true));
		assertTrue(SettingSearch.find(index, "notificaiton", false).isEmpty());
		assertTrue(SettingSearch.find(index, "zx", true).isEmpty());
	}

	@Test
	public void normalizesCaseAccentsPunctuationAndPartialWords()
	{
		assertEquals(List.of(notification), SettingSearch.find(index, "  IDLE, NOTIF!  ", false));
		assertEquals(List.of(notification), SettingSearch.find(index, "alérts", false));
		assertTrue(SettingSearch.find(index, " ! ", true).isEmpty());
	}

	private static Setting setting(String name, String plugin, String section, String description, String key)
	{
		return new Setting(name, plugin, section, description, key, new String[0], () -> {});
	}
}
