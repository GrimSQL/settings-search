package com.settingssearch;

import com.google.inject.Provides;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.api.MenuAction;
import net.runelite.client.config.ChatColorConfig;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigDescriptor;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigItemDescriptor;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.ConfigSectionDescriptor;
import net.runelite.client.config.RuneLiteConfig;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.ExternalPluginsChanged;
import net.runelite.client.events.OverlayMenuClicked;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayMenuEntry;
import net.runelite.client.util.Text;

@PluginDescriptor(
	name = "Settings Search",
	description = "Find individual settings across installed plugins",
	tags = {"config", "settings", "fuzzy", "search"}
)
public class SettingsSearchPlugin extends Plugin
{
	@Inject
	private PluginManager pluginManager;
	@Inject
	private ConfigManager configManager;
	@Inject
	private SettingsSearchConfig config;
	@Inject
	private ClientToolbar toolbar;
	@Inject
	private EventBus eventBus;

	// Panel and navigation are owned by Swing's event dispatch thread.
	private SettingsSearchPanel panel;
	private NavigationButton navigation;

	@Override
	protected void startUp()
	{
		panel = new SettingsSearchPanel(this::buildIndex, config::fuzzySearch,
			enabled -> configManager.setConfiguration(SettingsSearchConfig.GROUP, SettingsSearchConfig.FUZZY_KEY, enabled));
		navigation = NavigationButton.builder()
			.tooltip("Settings Search")
			.icon(searchIcon(ColorScheme.LIGHT_GRAY_COLOR))
			.priority(1)
			.panel(panel)
			.build();
		toolbar.addNavigation(navigation);
	}

	@Override
	protected void shutDown()
	{
		if (navigation != null)
		{
			toolbar.removeNavigation(navigation);
			panel.onDeactivate();
			navigation = null;
			panel = null;
		}
	}

	@Provides
	SettingsSearchConfig provideConfig(ConfigManager manager)
	{
		return manager.getConfig(SettingsSearchConfig.class);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (SettingsSearchConfig.GROUP.equals(event.getGroup())
			&& SettingsSearchConfig.FUZZY_KEY.equals(event.getKey()))
		{
			SwingUtilities.invokeLater(() ->
			{
				if (panel != null)
				{
					panel.refreshFuzzySetting();
				}
			});
		}
	}

	@Subscribe
	public void onExternalPluginsChanged(ExternalPluginsChanged event)
	{
		SwingUtilities.invokeLater(() ->
		{
			if (panel != null)
			{
				panel.invalidateIndex();
			}
		});
	}

	private List<Setting> buildIndex()
	{
		List<Setting> settings = new ArrayList<>();
		addSettings(settings, configManager.getConfig(RuneLiteConfig.class),
			namedTarget("RuneLite"), new String[]{"client"});
		addSettings(settings, configManager.getConfig(ChatColorConfig.class),
			namedTarget("Chat Color"), new String[]{"colour", "messages"});
		for (Plugin plugin : pluginManager.getPlugins())
		{
			PluginDescriptor descriptor = plugin.getClass().getAnnotation(PluginDescriptor.class);
			if (plugin != this && descriptor != null && !descriptor.hidden())
			{
				Config config = pluginManager.getPluginConfigProxy(plugin);
				if (config != null)
				{
					addSettings(settings, config, plugin, descriptor.tags());
				}
			}
		}
		return settings;
	}

	private void addSettings(List<Setting> settings, Config config, Plugin plugin, String[] tags)
	{
		ConfigDescriptor configDescriptor = configManager.getConfigDescriptor(config);
		Map<String, String> sections = new HashMap<>();
		for (ConfigSectionDescriptor section : configDescriptor.getSections())
		{
			sections.put(section.getKey(), plainText(section.getSection().name()));
		}
		for (ConfigItemDescriptor itemDescriptor : configDescriptor.getItems())
		{
			ConfigItem item = itemDescriptor.getItem();
			if (!item.hidden())
			{
				settings.add(new Setting(plainText(item.name()), plugin.getName(),
					sections.getOrDefault(item.section(), ""), plainText(item.description()),
					item.keyName(), tags, () -> openConfiguration(plugin)));
			}
		}
	}

	private static String plainText(String value)
	{
		return Text.removeTags(value.replaceAll("(?i)<br\\s*/?>", " ")).strip();
	}

	private void openConfiguration(Plugin plugin)
	{
		// RuneLite uses this event to open a plugin's settings from an overlay menu.
		// This overlay is never registered or displayed.
		Overlay target = new Overlay(plugin)
		{
			@Override
			public Dimension render(Graphics2D graphics)
			{
				return null;
			}
		};
		eventBus.post(new OverlayMenuClicked(
			new OverlayMenuEntry(MenuAction.RUNELITE_OVERLAY_CONFIG, "Configure", plugin.getName()), target));
	}

	private static Plugin namedTarget(String name)
	{
		// The two client settings pages are listed by name without a plugin instance.
		return new Plugin()
		{
			@Override
			public String getName()
			{
				return name;
			}
		};
	}

	static BufferedImage searchIcon(Color color)
	{
		BufferedImage icon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = icon.createGraphics();
		try
		{
			graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			graphics.setColor(color);
			graphics.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			graphics.drawOval(2, 2, 8, 8);
			graphics.drawLine(9, 9, 14, 14);
		}
		finally
		{
			graphics.dispose();
		}
		return icon;
	}
}
