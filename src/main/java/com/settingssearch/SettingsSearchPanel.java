package com.settingssearch;

import com.google.common.html.HtmlEscapers;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.basic.BasicGraphicsUtils;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.IconTextField;
import net.runelite.client.ui.components.PluginErrorPanel;

final class SettingsSearchPanel extends PluginPanel
{
	private static final Color CANVAS = ColorScheme.DARK_GRAY_COLOR;
	private static final Color SURFACE = ColorScheme.DARKER_GRAY_COLOR;
	private static final Color TEXT = Color.WHITE;
	private static final Color MUTED = ColorScheme.LIGHT_GRAY_COLOR;
	private static final Color ACCENT = ColorScheme.BRAND_ORANGE;
	private static final int ROW_HEIGHT = 40;
	private static final String MATCHES = "matches";
	private static final String MESSAGE = "message";
	private static final String EMPTY = "empty";
	private static final Font BODY = FontManager.getRunescapeFont();
	private static final Font SMALL = FontManager.getRunescapeSmallFont();
	private static final Font BOLD = FontManager.getRunescapeBoldFont();

	private final Supplier<List<Setting>> indexLoader;
	private final BooleanSupplier fuzzySetting;
	private final SearchHistory history;
	private final IconTextField query = new IconTextField();
	private final JButton historyButton = new JButton(UIManager.getIcon("Tree.expandedIcon"));
	private final JPopupMenu historyMenu = new JPopupMenu();
	private final JCheckBox fuzzy = new JCheckBox("Fuzzy search");
	private final JLabel status = new JLabel();
	private final DefaultListModel<Setting> model = new DefaultListModel<>();
	private final JList<Setting> results = new JList<>(model);
	private final CardLayout cards = new CardLayout();
	private final JPanel content = new JPanel(cards);
	private final PluginErrorPanel messageText = new PluginErrorPanel();
	private final JPanel preview = new JPanel(new BorderLayout(0, 8));
	private final JTextPane details = new JTextPane();
	private final JButton open = new JButton("Open plugin settings");
	private final JScrollPane listScroll = scrollPane(results);
	private final Timer searchTimer = new Timer(100, event -> search());

	private List<Setting> index;
	private String renderedQuery = "";
	private boolean active;

	SettingsSearchPanel(Supplier<List<Setting>> indexLoader, BooleanSupplier fuzzySetting,
		Consumer<Boolean> saveFuzzy, SearchHistory history)
	{
		super(false);
		this.indexLoader = indexLoader;
		this.fuzzySetting = fuzzySetting;
		this.history = history;
		searchTimer.setRepeats(false);
		setLayout(new BorderLayout(0, 5));
		setBorder(BorderFactory.createEmptyBorder(5, 8, 8, 8));
		setBackground(CANVAS);

		query.setIcon(IconTextField.Icon.SEARCH);
		query.setBackground(SURFACE);
		query.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
		query.setPreferredSize(new Dimension(100, 30));
		query.getAccessibleContext().setAccessibleName("Search plugin settings");
		query.setToolTipText("Search by setting, plugin, section, or description");
		query.addClearListener(this::search);

		historyButton.setBackground(SURFACE);
		historyButton.setMargin(new Insets(0, 0, 0, 0));
		historyButton.setPreferredSize(new Dimension(26, 30));
		historyButton.setToolTipText("Recent searches");
		historyButton.getAccessibleContext().setAccessibleName("Recent searches");
		historyButton.addActionListener(event -> showHistory());
		JPanel searchRow = new JPanel(new BorderLayout(2, 0));
		searchRow.setOpaque(false);
		searchRow.add(query, BorderLayout.CENTER);
		searchRow.add(historyButton, BorderLayout.EAST);

		fuzzy.setFont(BODY);
		fuzzy.setForeground(TEXT);
		fuzzy.setOpaque(false);
		fuzzy.setSelected(fuzzySetting.getAsBoolean());
		fuzzy.setToolTipText(SettingsSearchConfig.FUZZY_DESCRIPTION);
		fuzzy.getAccessibleContext().setAccessibleDescription(SettingsSearchConfig.FUZZY_DESCRIPTION);

		JPanel header = new JPanel(new BorderLayout(0, 4));
		header.setOpaque(false);
		header.add(searchRow, BorderLayout.NORTH);
		JPanel options = new JPanel(new BorderLayout(5, 0));
		options.setOpaque(false);
		status.setFont(SMALL);
		status.setForeground(MUTED);
		options.add(fuzzy, BorderLayout.WEST);
		options.add(status, BorderLayout.EAST);
		header.add(options, BorderLayout.SOUTH);
		add(header, BorderLayout.NORTH);

		results.setBackground(SURFACE);
		results.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		results.setCellRenderer(new SettingRenderer());
		results.setFixedCellHeight(ROW_HEIGHT);
		results.getAccessibleContext().setAccessibleName("Matching settings");
		listScroll.setBorder(BorderFactory.createEmptyBorder());
		listScroll.getViewport().setBackground(SURFACE);
		listScroll.getVerticalScrollBar().setUnitIncrement(ROW_HEIGHT);
		listScroll.setPreferredSize(new Dimension(0, ROW_HEIGHT * 4));
		listScroll.setMinimumSize(new Dimension(0, ROW_HEIGHT * 2));
		content.setOpaque(false);
		add(content, BorderLayout.CENTER);

		details.setFont(BODY);
		details.setEditable(false);
		details.setBackground(SURFACE);
		details.setForeground(TEXT);
		details.setBorder(BorderFactory.createEmptyBorder(2, 0, 4, 4));
		details.getAccessibleContext().setAccessibleName("Selected setting description");
		JScrollPane detailScroll = scrollPane(details);
		detailScroll.getViewport().setBackground(SURFACE);
		detailScroll.setBorder(BorderFactory.createEmptyBorder());

		open.setFont(BODY);
		open.setBackground(CANVAS);
		open.setPreferredSize(new Dimension(160, 28));
		preview.setBackground(SURFACE);
		preview.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		preview.setPreferredSize(new Dimension(0, 162));
		preview.setMinimumSize(new Dimension(0, 100));
		preview.add(detailScroll, BorderLayout.CENTER);
		preview.add(open, BorderLayout.SOUTH);

		JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, true, listScroll, preview);
		split.setBackground(CANVAS);
		split.setBorder(BorderFactory.createEmptyBorder());
		split.setDividerSize(9);
		split.setResizeWeight(0.54);
		split.getAccessibleContext().setAccessibleName("Resize search results and setting description");
		content.add(split, MATCHES);
		JPanel message = new JPanel(new BorderLayout());
		message.setOpaque(false);
		message.add(messageText, BorderLayout.NORTH);
		content.add(message, MESSAGE);
		JPanel empty = new JPanel();
		empty.setOpaque(false);
		content.add(empty, EMPTY);

		query.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent event)
			{
				searchTimer.restart();
			}

			@Override
			public void removeUpdate(DocumentEvent event)
			{
				searchTimer.restart();
			}

			@Override
			public void changedUpdate(DocumentEvent event)
			{
				searchTimer.restart();
			}
		});
		fuzzy.addActionListener(event ->
		{
			saveFuzzy.accept(fuzzy.isSelected());
			search();
		});
		results.addListSelectionListener(event -> showSelection());
		results.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent event)
			{
				int row = results.locationToIndex(event.getPoint());
				if (event.getButton() != MouseEvent.BUTTON1 || row < 0
					|| !results.getCellBounds(row, row).contains(event.getPoint()))
				{
					return;
				}
				if (event.getClickCount() == 2)
				{
					openSelected();
				}
				else if (!searchTimer.isRunning())
				{
					history.remember(renderedQuery, model.get(row));
				}
			}
		});
		open.addActionListener(event -> openSelected());
		results.registerKeyboardAction(event -> openSelected(),
			KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), WHEN_FOCUSED);
		query.addActionListener(event ->
		{
			if (searchTimer.isRunning())
			{
				search();
			}
			history.remember(renderedQuery, null);
			if (!model.isEmpty())
			{
				results.requestFocusInWindow();
			}
		});
		cards.show(content, EMPTY);
		showSelection();
	}

	@Override
	public void onActivate()
	{
		active = true;
		fuzzy.setSelected(fuzzySetting.getAsBoolean());
		search();
		query.requestFocusInWindow();
	}

	@Override
	public void onDeactivate()
	{
		if (active)
		{
			String text = query.getText();
			history.remember(text.isBlank() ? renderedQuery : text, null);
		}
		active = false;
		searchTimer.stop();
		historyMenu.setVisible(false);
	}

	void refreshFuzzySetting()
	{
		boolean enabled = fuzzySetting.getAsBoolean();
		if (fuzzy.isSelected() != enabled)
		{
			fuzzy.setSelected(enabled);
			if (active)
			{
				search();
			}
		}
	}

	void invalidateIndex()
	{
		index = null;
		if (active)
		{
			search();
		}
	}

	private void search()
	{
		searchTimer.stop();
		model.clear();
		String text = query.getText();
		if (SettingSearch.normalize(text).isEmpty())
		{
			history.remember(renderedQuery, null);
			renderedQuery = "";
			status.setText("");
			cards.show(content, EMPTY);
			return;
		}
		renderedQuery = text;

		if (index == null)
		{
			index = indexLoader.get();
		}
		List<Setting> matches = SettingSearch.find(index, text, fuzzy.isSelected());
		if (matches.isEmpty())
		{
			String hint = fuzzy.isSelected() ? "Try fewer words or<br>a different keyword."
				: "Try fewer words or<br>enable Fuzzy search.";
			status.setText("");
			messageText.setContent("No matching settings", hint);
			cards.show(content, MESSAGE);
			return;
		}

		status.setText(matches.size() == 1 ? "1 result" : matches.size() + " results");
		cards.show(content, MATCHES);
		model.addAll(matches);
		results.setSelectedIndex(0);
		results.ensureIndexIsVisible(0);
	}

	private void showHistory()
	{
		historyMenu.removeAll();
		List<SearchHistory.Entry> entries = history.entries();
		if (entries.isEmpty())
		{
			JMenuItem empty = new JMenuItem("No recent searches");
			empty.setEnabled(false);
			historyMenu.add(empty);
		}
		int width = query.getParent().getWidth();
		for (SearchHistory.Entry entry : entries)
		{
			JMenuItem item = new JMenuItem();
			item.setFont(BODY);
			String result = entry.settingName == null ? "" : entry.settingName + " / " + entry.plugin;
			String title = clippedMenuText(item, entry.query, width - 24);
			String subtitle = result.isEmpty() ? "" : "<br><small>" + clippedMenuText(item, result, width - 24) + "</small>";
			item.setText("<html>" + title + subtitle + "</html>");
			item.getAccessibleContext().setAccessibleName(result.isEmpty() ? entry.query : entry.query + ", " + result);
			item.setToolTipText("<html>" + HtmlEscapers.htmlEscaper().escape(entry.query)
				+ (result.isEmpty() ? "" : "<br>" + HtmlEscapers.htmlEscaper().escape(result)) + "</html>");
			item.addActionListener(event -> restoreSearch(entry));
			historyMenu.add(item);
		}
		historyMenu.setPopupSize(width, historyMenu.getLayout().preferredLayoutSize(historyMenu).height);
		historyMenu.show(query, 0, query.getHeight());
	}

	private static String clippedMenuText(JMenuItem item, String text, int width)
	{
		String clipped = BasicGraphicsUtils.getClippedString(item, item.getFontMetrics(item.getFont()), text, width);
		return HtmlEscapers.htmlEscaper().escape(clipped);
	}

	private void restoreSearch(SearchHistory.Entry entry)
	{
		query.setText(entry.query);
		search();
		for (int row = 0; row < model.size(); row++)
		{
			if (entry.matches(model.get(row)))
			{
				results.setSelectedIndex(row);
				results.ensureIndexIsVisible(row);
				break;
			}
		}
		history.remember(entry.query, null);
		query.requestFocusInWindow();
	}

	private void showSelection()
	{
		Setting selected = results.getSelectedValue();
		open.setEnabled(selected != null);
		if (selected == null)
		{
			details.setText("");
			return;
		}

		String location = selected.location();
		details.setText(selected.name + "\n" + location + "\n\n" + selected.description);
		StyledDocument document = details.getStyledDocument();
		document.setCharacterAttributes(0, document.getLength(), detailStyle(TEXT, BODY), true);
		document.setCharacterAttributes(0, selected.name.length(), detailStyle(TEXT, BOLD), true);
		document.setCharacterAttributes(selected.name.length() + 1, location.length(),
			detailStyle(MUTED, SMALL), true);
		SimpleAttributeSet spacing = new SimpleAttributeSet();
		StyleConstants.setLineSpacing(spacing, 0.18f);
		document.setParagraphAttributes(0, document.getLength(), spacing, false);
		details.setCaretPosition(0);
	}

	private static SimpleAttributeSet detailStyle(Color foreground, Font font)
	{
		SimpleAttributeSet style = new SimpleAttributeSet();
		StyleConstants.setFontFamily(style, font.getFamily());
		StyleConstants.setForeground(style, foreground);
		StyleConstants.setFontSize(style, font.getSize());
		StyleConstants.setBold(style, font.isBold());
		return style;
	}

	private void openSelected()
	{
		if (searchTimer.isRunning())
		{
			search();
			return;
		}
		Setting selected = results.getSelectedValue();
		if (selected != null)
		{
			history.remember(renderedQuery, selected);
			selected.open.run();
		}
	}

	private static JScrollPane scrollPane(JComponent component)
	{
		JScrollPane scroll = new JScrollPane(component);
		scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		return scroll;
	}

	private static final class SettingRenderer extends JPanel implements ListCellRenderer<Setting>
	{
		private final JLabel name = new JLabel();
		private final JLabel location = new JLabel();

		private SettingRenderer()
		{
			setLayout(new GridLayout(2, 1));
			name.setFont(BOLD);
			location.setFont(SMALL);
			name.putClientProperty("html.disable", true);
			location.putClientProperty("html.disable", true);
			add(name);
			add(location);
		}

		@Override
		public Component getListCellRendererComponent(JList<? extends Setting> list, Setting setting,
			int index, boolean selected, boolean focused)
		{
			name.setText(setting.name);
			location.setText(setting.location());
			name.setForeground(TEXT);
			location.setForeground(MUTED);
			setBackground(selected ? ColorScheme.DARKER_GRAY_HOVER_COLOR : SURFACE);
			setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(0, 0, 1, 0, selected || focused ? ACCENT : CANVAS),
				BorderFactory.createEmptyBorder(4, 8, 4, 8)));
			return this;
		}

		@Override
		public Dimension getPreferredSize()
		{
			return new Dimension(PANEL_WIDTH - 40, ROW_HEIGHT);
		}
	}
}
