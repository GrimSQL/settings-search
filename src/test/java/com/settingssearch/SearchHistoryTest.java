package com.settingssearch;

import com.google.gson.Gson;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SearchHistoryTest
{
	private final AtomicReference<String> saved = new AtomicReference<>();
	private final SearchHistory history = new SearchHistory(new Gson(), saved::get, saved::set);

	@Test
	public void restoresSearchAndSelectedSettingFromProfile()
	{
		Setting target = new Setting("Zoom limit", "Camera", "Zoom", "", "zoomLimit", new String[0], () -> {});
		history.remember("Zoom", target);
		history.remember(" zoom ", null);

		SearchHistory restored = new SearchHistory(new Gson(), saved::get, saved::set);
		assertEquals(1, restored.entries().size());
		SearchHistory.Entry entry = restored.entries().get(0);
		assertEquals("zoom", entry.query);
		assertEquals("Zoom limit", entry.settingName);
		assertTrue(entry.matches(target));
		assertFalse(entry.matches(new Setting("Zoom limit", "Camera", "Zoom", "", "anotherKey",
			new String[0], () -> {})));
	}

	@Test
	public void keepsOnlyRecentSearchesAndRecoversFromInvalidStoredData()
	{
		saved.set("invalid JSON");
		assertTrue(history.entries().isEmpty());
		history.remember(" ", null);
		assertEquals("invalid JSON", saved.get());

		for (int i = 0; i <= 10; i++)
		{
			history.remember("Search " + i, null);
		}
		assertEquals(10, history.entries().size());
		assertEquals("Search 10", history.entries().get(0).query);
		assertEquals("Search 1", history.entries().get(9).query);
	}
}
