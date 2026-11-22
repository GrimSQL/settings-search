package com.settingssearch;

import java.awt.BorderLayout;
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
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.ui.PluginPanel;

final class SettingsSearchPanel extends PluginPanel
{
	private static final Color CANVAS = new Color(0x22252A);
	private static final Color SURFACE = new Color(0x2B2F36);
	private static final Color LINE = new Color(0x424952);
	private static final Color TEXT = new Color(0xF4F0E9);
	private static final Color MUTED = new Color(0xB7BFC8);
	private static final Color ACCENT = new Color(0xFFCA84);
	private static final Color SELECTED = new Color(0x40362C);
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
	private final JPanel content = new JPanel(new BorderLayout());
	private final JPanel message = new JPanel(new BorderLayout(0, 10));
	private final JTextArea messageText = textArea(3, MUTED);
	private final JPanel suggestions = new JPanel(new GridLayout(0, 1, 0, 5));
	private final JPanel preview = new JPanel(new BorderLayout(0, 8));
	private final JTextArea details = textArea(4, TEXT);
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
		title.setFont(BODY.deriveFont(Font.BOLD, 20f));
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
		header.add(fuzzy, BorderLayout.SOUTH);
		add(header, BorderLayout.NORTH);

		status.setFont(BOLD);
		status.setForeground(MUTED);
		status.setBorder(BorderFactory.createEmptyBorder(0, 2, 7, 0));
		results.setBackground(CANVAS);
		results.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		results.setCellRenderer(new SettingRenderer());
		results.setFixedCellHeight(46);
		results.getAccessibleContext().setAccessibleName("Matching settings");
		listScroll.setBorder(BorderFactory.createMatteBorder(1, 0, 1, 0, LINE));
		listScroll.getViewport().setBackground(CANVAS);
		listScroll.getVerticalScrollBar().setUnitIncrement(46);
		content.setOpaque(false);
		JPanel resultsPanel = new JPanel(new BorderLayout());
		resultsPanel.setOpaque(false);
		resultsPanel.add(status, BorderLayout.NORTH);
		resultsPanel.add(content, BorderLayout.CENTER);
		add(resultsPanel, BorderLayout.CENTER);

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

		details.setBackground(SURFACE);
		details.setOpaque(true);
		details.setBorder(BorderFactory.createEmptyBorder(8, 9, 8, 9));
		details.getAccessibleContext().setAccessibleName("Selected setting description");
		JScrollPane detailScroll = scrollPane(details);
		detailScroll.setBorder(BorderFactory.createLineBorder(LINE));
		open.setFont(BOLD);
		open.setForeground(CANVAS);
		open.setBackground(ACCENT);
		open.setPreferredSize(new Dimension(160, 34));
		preview.setOpaque(false);
		preview.add(detailScroll, BorderLayout.CENTER);
		preview.add(open, BorderLayout.SOUTH);
		add(preview, BorderLayout.SOUTH);

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
		showMessage("Try a search", "Search across your installed plugins by name or keyword.", true);
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
			showMessage("Try a search", "Search across your installed plugins by name or keyword.", true);
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
			showMessage("No matching settings", hint, false);
			return;
		}

		status.setText(matches.size() == 1 ? "1 setting found" : matches.size() + " settings found");
		showContent(listScroll);
		model.addAll(matches);
		results.setSelectedIndex(0);
		results.ensureIndexIsVisible(0);
	}

	private void showMessage(String heading, String body, boolean showSuggestions)
	{
		status.setText(heading);
		messageText.setText(body);
		suggestions.setVisible(showSuggestions);
		showContent(message);
	}

	private void showContent(JComponent component)
	{
		if (component.getParent() != content)
		{
			content.removeAll();
			content.add(component, BorderLayout.CENTER);
			content.revalidate();
			content.repaint();
		}
	}

	private void showSelection()
	{
		Setting selected = results.getSelectedValue();
		preview.setVisible(selected != null);
		open.setEnabled(selected != null);
		details.setText(selected == null ? "" : selected.name + "\n" + selected.location()
			+ (selected.description.isBlank() ? "" : "\n\n" + selected.description));
		details.setCaretPosition(0);
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
			name.setForeground(selected ? ACCENT : TEXT);
			location.setForeground(MUTED);
			setBackground(selected ? SELECTED : CANVAS);
			setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(0, 1, 1, 1, focused ? ACCENT : LINE),
				BorderFactory.createEmptyBorder(5, 8, 5, 8)));
			return this;
		}

		@Override
		public Dimension getPreferredSize()
		{
			return new Dimension(PANEL_WIDTH - 40, 46);
		}
	}
}
