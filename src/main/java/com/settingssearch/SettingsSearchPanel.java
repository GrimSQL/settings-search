package com.settingssearch;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

final class SettingsSearchPanel extends PluginPanel
{
	private static final Color CANVAS = ColorScheme.DARK_GRAY_COLOR;
	private static final Color SURFACE = ColorScheme.DARKER_GRAY_COLOR;
	private static final Color LINE = ColorScheme.MEDIUM_GRAY_COLOR;
	private static final Color TEXT = Color.WHITE;
	private static final Color MUTED = ColorScheme.LIGHT_GRAY_COLOR;
	private static final Color ACCENT = ColorScheme.BRAND_ORANGE;
	private static final int ROW_HEIGHT = 42;
	private static final String MATCHES = "matches";
	private static final String MESSAGE = "message";
	private static final Font BODY = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
	private static final Font SMALL = BODY.deriveFont(11f);
	private static final Font BOLD = BODY.deriveFont(Font.BOLD);

	private final Supplier<List<Setting>> indexLoader;
	private final BooleanSupplier fuzzySetting;
	private final JTextField query = new JTextField();
	private final JCheckBox fuzzy = new JCheckBox("Fuzzy search");
	private final JLabel status = new JLabel();
	private final DefaultListModel<Setting> model = new DefaultListModel<>();
	private final JList<Setting> results = new JList<>(model);
	private final CardLayout cards = new CardLayout();
	private final JPanel content = new JPanel(cards);
	private final JPanel message = new JPanel(new BorderLayout(0, 10));
	private final JTextArea messageText = textArea(3, MUTED);
	private final JPanel suggestions = new JPanel(new GridLayout(0, 1, 0, 5));
	private final JPanel preview = new JPanel(new BorderLayout(0, 8));
	private final JTextPane details = new JTextPane();
	private final JButton open = new JButton("Open plugin settings");
	private final JScrollPane listScroll = scrollPane(results);
	private final Timer searchTimer = new Timer(100, event -> search());

	private List<Setting> index;
	private boolean active;

	SettingsSearchPanel(Supplier<List<Setting>> indexLoader, BooleanSupplier fuzzySetting,
		Consumer<Boolean> saveFuzzy)
	{
		super(false);
		this.indexLoader = indexLoader;
		this.fuzzySetting = fuzzySetting;
		searchTimer.setRepeats(false);
		setLayout(new BorderLayout(0, 12));
		setBorder(BorderFactory.createEmptyBorder(14, 12, 12, 12));
		setBackground(CANVAS);

		JLabel title = new JLabel("Settings Search");
		title.setFont(BODY.deriveFont(Font.BOLD, 18f));
		title.setForeground(TEXT);
		title.setLabelFor(query);
		query.setFont(BODY);
		query.setForeground(TEXT);
		query.setCaretColor(TEXT);
		query.setBackground(SURFACE);
		query.setPreferredSize(new Dimension(140, 34));
		query.getAccessibleContext().setAccessibleName("Search plugin settings");
		query.putClientProperty("JTextField.placeholderText", "Find a setting");
		query.putClientProperty("JTextField.showClearButton", true);
		query.putClientProperty("JTextField.leadingComponent",
			new JLabel(new ImageIcon(SettingsSearchPlugin.searchIcon(MUTED))));
		query.setToolTipText("Search by setting, plugin, section, or description");

		fuzzy.setFont(BODY);
		fuzzy.setForeground(TEXT);
		fuzzy.setOpaque(false);
		fuzzy.setSelected(fuzzySetting.getAsBoolean());
		fuzzy.setToolTipText(SettingsSearchConfig.FUZZY_DESCRIPTION);
		fuzzy.getAccessibleContext().setAccessibleDescription(SettingsSearchConfig.FUZZY_DESCRIPTION);

		JPanel header = new JPanel(new BorderLayout(0, 9));
		header.setOpaque(false);
		header.add(title, BorderLayout.NORTH);
		header.add(query, BorderLayout.CENTER);
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
		listScroll.setBorder(BorderFactory.createLineBorder(LINE));
		listScroll.getViewport().setBackground(SURFACE);
		listScroll.getVerticalScrollBar().setUnitIncrement(ROW_HEIGHT);
		listScroll.setPreferredSize(new Dimension(0, ROW_HEIGHT * 4));
		listScroll.setMinimumSize(new Dimension(0, ROW_HEIGHT * 2));
		content.setOpaque(false);
		add(content, BorderLayout.CENTER);

		message.setOpaque(false);
		message.setBorder(BorderFactory.createEmptyBorder(8, 2, 0, 2));
		message.add(messageText, BorderLayout.NORTH);
		suggestions.setOpaque(false);
		for (String suggestion : List.of("idle notification", "zoom limit"))
		{
			JButton button = new JButton(suggestion);
			button.setHorizontalAlignment(JButton.LEFT);
			button.setFont(BODY);
			button.setForeground(ACCENT);
			button.setBackground(SURFACE);
			button.addActionListener(event ->
			{
				query.setText(suggestion);
				search();
				query.requestFocusInWindow();
			});
			suggestions.add(button);
		}
		JPanel messageBody = new JPanel(new BorderLayout());
		messageBody.setOpaque(false);
		messageBody.add(suggestions, BorderLayout.NORTH);
		message.add(messageBody, BorderLayout.CENTER);

		details.setFont(BODY.deriveFont(13f));
		details.setEditable(false);
		details.setBackground(SURFACE);
		details.setForeground(TEXT);
		details.setBorder(BorderFactory.createEmptyBorder(2, 0, 4, 4));
		details.getAccessibleContext().setAccessibleName("Selected setting description");
		JScrollPane detailScroll = scrollPane(details);
		detailScroll.getViewport().setBackground(SURFACE);
		detailScroll.setBorder(BorderFactory.createEmptyBorder());

		open.setFont(BOLD);
		open.setForeground(SURFACE);
		open.setBackground(ACCENT);
		open.setPreferredSize(new Dimension(160, 34));
		preview.setBackground(SURFACE);
		preview.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(LINE),
			BorderFactory.createEmptyBorder(9, 9, 9, 9)));
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
		content.add(message, MESSAGE);

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
				if (event.getButton() == MouseEvent.BUTTON1 && event.getClickCount() == 2 && row >= 0
					&& results.getCellBounds(row, row).contains(event.getPoint()))
				{
					openSelected();
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
			if (!model.isEmpty())
			{
				results.requestFocusInWindow();
			}
		});
		showMessage("", "Search across your installed plugins by name or keyword.", true);
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
		active = false;
		searchTimer.stop();
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
			showMessage("", "Search across your installed plugins by name or keyword.", true);
			return;
		}

		if (index == null)
		{
			index = indexLoader.get();
		}
		List<Setting> matches = SettingSearch.find(index, text, fuzzy.isSelected());
		if (matches.isEmpty())
		{
			String hint = fuzzy.isSelected() ? "Try fewer words or a different keyword."
				: "Try fewer words or enable Fuzzy search to find close matches.";
			showMessage("0 results", "No matching settings.\n" + hint, false);
			return;
		}

		status.setText(matches.size() == 1 ? "1 result" : matches.size() + " results");
		cards.show(content, MATCHES);
		model.addAll(matches);
		results.setSelectedIndex(0);
		results.ensureIndexIsVisible(0);
	}

	private void showMessage(String heading, String body, boolean showSuggestions)
	{
		status.setText(heading);
		messageText.setText(body);
		suggestions.setVisible(showSuggestions);
		cards.show(content, MESSAGE);
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
		document.setCharacterAttributes(0, document.getLength(), detailStyle(TEXT, 13, false), true);
		document.setCharacterAttributes(0, selected.name.length(), detailStyle(TEXT, 16, true), true);
		document.setCharacterAttributes(selected.name.length() + 1, location.length(),
			detailStyle(MUTED, 12, false), true);
		SimpleAttributeSet spacing = new SimpleAttributeSet();
		StyleConstants.setLineSpacing(spacing, 0.18f);
		document.setParagraphAttributes(0, document.getLength(), spacing, false);
		details.setCaretPosition(0);
	}

	private static SimpleAttributeSet detailStyle(Color foreground, int size, boolean bold)
	{
		SimpleAttributeSet style = new SimpleAttributeSet();
		StyleConstants.setFontFamily(style, BODY.getFamily());
		StyleConstants.setForeground(style, foreground);
		StyleConstants.setFontSize(style, size);
		StyleConstants.setBold(style, bold);
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
			selected.open.run();
		}
	}

	private static JTextArea textArea(int rows, Color foreground)
	{
		JTextArea area = new JTextArea(rows, 0);
		area.setFont(BODY);
		area.setForeground(foreground);
		area.setOpaque(false);
		area.setEditable(false);
		area.setLineWrap(true);
		area.setWrapStyleWord(true);
		return area;
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
			setBackground(selected ? LINE : SURFACE);
			setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(0, 2, 1, 0, selected || focused ? ACCENT : SURFACE),
				BorderFactory.createEmptyBorder(4, 7, 4, 8)));
			return this;
		}

		@Override
		public Dimension getPreferredSize()
		{
			return new Dimension(PANEL_WIDTH - 40, ROW_HEIGHT);
		}
	}
}
