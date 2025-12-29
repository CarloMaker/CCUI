package com.cc.visualcc;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import com.intellij.icons.AllIcons;

import javax.swing.*;
import javax.swing.border.AbstractBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.geom.RoundRectangle2D;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Main chat panel for VisualCC.
 * Contains the message display area and input field.
 */
public class VisualCCChatPanel extends JPanel implements Disposable {

    // Muted professional color palette
    private static final Color BG_PRIMARY = new Color(30, 30, 30);       // Main background
    private static final Color BG_SECONDARY = new Color(37, 37, 38);     // Secondary background
    private static final Color BG_TERTIARY = new Color(45, 45, 46);      // Input/card background
    private static final Color BORDER_COLOR = new Color(55, 55, 58);     // Borders
    private static final Color ACCENT_COLOR = new Color(75, 85, 120);    // Muted blue-gray accent
    private static final Color ACCENT_HOVER = new Color(85, 95, 130);    // Hover state
    private static final Color TEXT_PRIMARY = new Color(200, 200, 200);  // Primary text (slightly dimmer)
    private static final Color TEXT_SECONDARY = new Color(130, 130, 130);// Secondary text
    private static final Color TEXT_MUTED = new Color(90, 90, 90);       // Muted text
    private static final Color SUCCESS_COLOR = new Color(80, 140, 90);   // Muted green

    private final Project project;
    private final JPanel messagesPanel;
    private final JBScrollPane scrollPane;
    private JTextArea inputField;
    private JButton sendButton;
    private JLabel statusLabel;
    private JLabel tokenLabel;
    private JLabel workingLabel;  // Shows "Claude is working..." with animated indicator
    private JCheckBox planModeCheckBox;  // Toggle for plan mode
    private VisualCCCliWrapper cliWrapper;
    private String placeholderText = "Type a message... (Ctrl+Enter to send)";
    private boolean isWorking = false;
    private long workingStartTime = 0;
    private Timer workingTimer;
    private String currentTool = null;  // Current tool being used (e.g., "Read", "Write")

    public VisualCCChatPanel(Project project) {
        this.project = project;
        setLayout(new BorderLayout(0, 0));
        setBackground(BG_PRIMARY);

        // ===== HEADER PANEL =====
        JPanel headerPanel = createHeaderPanel();
        add(headerPanel, BorderLayout.NORTH);

        // ===== MESSAGES AREA =====
        messagesPanel = new JPanel();
        messagesPanel.setLayout(new BoxLayout(messagesPanel, BoxLayout.Y_AXIS));
        messagesPanel.setBackground(BG_PRIMARY);
        messagesPanel.setBorder(JBUI.Borders.empty(8, 0));

        scrollPane = new JBScrollPane(messagesPanel);
        scrollPane.setBackground(BG_PRIMARY);
        scrollPane.getViewport().setBackground(BG_PRIMARY);
        scrollPane.setVerticalScrollBarPolicy(JBScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setBorder(BorderFactory.createMatteBorder(1, 0, 1, 0, BORDER_COLOR));
        add(scrollPane, BorderLayout.CENTER);

        // ===== INPUT PANEL =====
        JPanel inputPanel = createInputPanel();
        add(inputPanel, BorderLayout.SOUTH);

        // Welcome message
        System.out.println("[VisualCC] Adding welcome message...");
        addMessage("System", "Welcome to VisualCC! Claude Code CLI interface ready.\n" +
                "Type your message below to start.", MessageType.SYSTEM);
        System.out.println("[VisualCC] Chat panel initialized");
    }

    private JPanel createHeaderPanel() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(BG_SECONDARY);
        header.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_COLOR),
            JBUI.Borders.empty(8, 12)
        ));

        // Left: Title with icon
        JPanel titlePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        titlePanel.setOpaque(false);

        JLabel titleLabel = new JLabel("VisualCC");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        titleLabel.setForeground(TEXT_PRIMARY);
        titlePanel.add(titleLabel);

        // Right: Toolbar buttons
        JPanel toolbarPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        toolbarPanel.setOpaque(false);

        JButton newChatBtn = createToolbarButton(AllIcons.General.Add, "New Chat");
        JButton historyBtn = createToolbarButton(AllIcons.Actions.Find, "History");
        JButton settingsBtn = createToolbarButton(AllIcons.General.Settings, "Settings");

        newChatBtn.addActionListener(e -> clearMessages());

        toolbarPanel.add(newChatBtn);
        toolbarPanel.add(historyBtn);
        toolbarPanel.add(settingsBtn);

        header.add(titlePanel, BorderLayout.WEST);
        header.add(toolbarPanel, BorderLayout.EAST);

        return header;
    }

    private JButton createToolbarButton(Icon icon, String tooltip) {
        JButton btn = new JButton(icon) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                if (getModel().isRollover()) {
                    g2.setColor(BG_TERTIARY);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
                }
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setForeground(TEXT_SECONDARY);
        btn.setBackground(null);
        btn.setBorder(JBUI.Borders.empty(4, 8));
        btn.setFocusPainted(false);
        btn.setContentAreaFilled(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setToolTipText(tooltip);
        return btn;
    }

    private JPanel createInputPanel() {
        JPanel container = new JPanel(new BorderLayout());
        container.setBackground(BG_PRIMARY);
        container.setBorder(JBUI.Borders.empty(12));

        // Input wrapper with rounded border
        JPanel inputWrapper = new JPanel(new BorderLayout(8, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(BG_TERTIARY);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2.setColor(BORDER_COLOR);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                g2.dispose();
            }
        };
        inputWrapper.setOpaque(false);
        inputWrapper.setBorder(JBUI.Borders.empty(10, 12));

        // Text input with placeholder
        inputField = new JTextArea() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (getText().isEmpty() && !hasFocus()) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    g2.setColor(TEXT_MUTED);
                    g2.setFont(getFont());
                    Insets insets = getInsets();
                    g2.drawString(placeholderText, insets.left + 2, g.getFontMetrics().getAscent() + insets.top);
                    g2.dispose();
                }
            }
        };
        inputField.setRows(2);
        inputField.setLineWrap(true);
        inputField.setWrapStyleWord(true);
        inputField.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        inputField.setBackground(BG_TERTIARY);
        inputField.setForeground(TEXT_PRIMARY);
        inputField.setCaretColor(TEXT_PRIMARY);
        inputField.setBorder(null);
        inputField.setOpaque(false);

        // Repaint on focus change for placeholder
        inputField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) { inputField.repaint(); }
            @Override
            public void focusLost(FocusEvent e) { inputField.repaint(); }
        });

        // Send on Ctrl+Enter
        inputField.getInputMap(JComponent.WHEN_FOCUSED).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.CTRL_DOWN_MASK), "send");
        inputField.getActionMap().put("send", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                sendMessage();
            }
        });

        // Send button (circular, icon-style)
        sendButton = new JButton(AllIcons.Actions.Execute) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                Color bgColor = getModel().isRollover() ? ACCENT_HOVER : ACCENT_COLOR;
                g2.setColor(bgColor);
                g2.fillOval(0, 0, getWidth() - 1, getHeight() - 1);

                g2.dispose();
                super.paintComponent(g);
            }
        };
        sendButton.setPreferredSize(new Dimension(36, 36));
        sendButton.setBackground(ACCENT_COLOR);
        sendButton.setBorder(null);
        sendButton.setFocusPainted(false);
        sendButton.setContentAreaFilled(false);
        sendButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        sendButton.setToolTipText("Send message (Ctrl+Enter)");

        sendButton.addActionListener(e -> {
            System.out.println("[VisualCC] Send button clicked!");
            sendMessage();
        });

        JScrollPane inputScroll = new JScrollPane(inputField);
        inputScroll.setBorder(null);
        inputScroll.setOpaque(false);
        inputScroll.getViewport().setOpaque(false);
        inputScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        inputScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        // Button panel to center the send button vertically
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        buttonPanel.setOpaque(false);
        buttonPanel.add(sendButton);

        inputWrapper.add(inputScroll, BorderLayout.CENTER);
        inputWrapper.add(buttonPanel, BorderLayout.EAST);

        // Status bar below input
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setOpaque(false);
        statusBar.setBorder(JBUI.Borders.empty(8, 4, 0, 4));

        // Left: Hint label
        JLabel hintLabel = new JLabel("Ctrl+Enter to send | @ for context");
        hintLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        hintLabel.setForeground(TEXT_MUTED);

        // Center: Working indicator and Plan Mode toggle
        JPanel centerPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        centerPanel.setOpaque(false);

        workingLabel = new JLabel("");
        workingLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        workingLabel.setForeground(new Color(100, 160, 200));  // Light blue for working state

        // Plan Mode checkbox
        planModeCheckBox = new JCheckBox("Plan Mode");
        planModeCheckBox.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        planModeCheckBox.setForeground(new Color(120, 140, 170));  // Muted blue-gray
        planModeCheckBox.setSelected(false);
        planModeCheckBox.setOpaque(false);
        planModeCheckBox.setToolTipText("Enable Plan Mode - Claude will plan before implementing");
        planModeCheckBox.addActionListener(e -> {
            boolean selected = planModeCheckBox.isSelected();
            if (cliWrapper != null) {
                cliWrapper.setPlanMode(selected);
            }
        });

        centerPanel.add(workingLabel);
        centerPanel.add(planModeCheckBox);

        // Right: Status and tokens
        JPanel rightStatusPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightStatusPanel.setOpaque(false);

        statusLabel = new JLabel("Ready");
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        statusLabel.setForeground(TEXT_MUTED);

        tokenLabel = new JLabel("0 tokens");
        tokenLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        tokenLabel.setForeground(TEXT_MUTED);

        rightStatusPanel.add(statusLabel);
        rightStatusPanel.add(tokenLabel);

        statusBar.add(hintLabel, BorderLayout.WEST);
        statusBar.add(centerPanel, BorderLayout.CENTER);
        statusBar.add(rightStatusPanel, BorderLayout.EAST);

        container.add(inputWrapper, BorderLayout.CENTER);
        container.add(statusBar, BorderLayout.SOUTH);

        return container;
    }

    public void startCLI() {
        if (cliWrapper == null) {
            cliWrapper = new VisualCCCliWrapper(project, this);
            cliWrapper.start();
        }
    }

    public void sendMessage() {
        System.out.println("[VisualCC] sendMessage() called");
        String text = inputField.getText().trim();
        System.out.println("[VisualCC] Input text: '" + text + "'");
        if (text.isEmpty()) {
            System.out.println("[VisualCC] Input is empty, returning");
            return;
        }

        // Add user message to chat
        System.out.println("[VisualCC] Adding user message to chat");
        addMessage("User", text, MessageType.USER);
        inputField.setText("");

        // sendInput now handles both normal messages and answers to questions
        // (following Kline approach where everything goes through sendInput)
        System.out.println("[VisualCC] CLI wrapper is null: " + (cliWrapper == null));
        if (cliWrapper != null) {
            System.out.println("[VisualCC] Sending to CLI: " + text);
            cliWrapper.sendInput(text);  // This will handle both cases
        } else {
            System.out.println("[VisualCC] CLI not started, starting...");
            addMessage("System", "CLI not started. Starting...", MessageType.SYSTEM);
            startCLI();
            System.out.println("[VisualCC] CLI started, sending: " + text);
            cliWrapper.sendInput(text);
        }
    }

    public void addMessage(String sender, String text, MessageType type) {
        System.out.println("[VisualCC] addMessage called - sender: " + sender + ", type: " + type);
        SwingUtilities.invokeLater(() -> {
            System.out.println("[VisualCC] Creating message component on EDT");
            VisualCCMessageComponent messageComponent = new VisualCCMessageComponent(sender, text, type);
            messagesPanel.add(messageComponent);
            messagesPanel.add(Box.createVerticalStrut(4));
            messagesPanel.revalidate();
            messagesPanel.repaint();
            scrollPane.revalidate();
            scrollPane.repaint();
            System.out.println("[VisualCC] Message component added, panel size: " + messagesPanel.getComponentCount());

            // Auto-scroll to bottom after a short delay to ensure layout is complete
            SwingUtilities.invokeLater(() -> {
                SwingUtilities.invokeLater(() -> {
                    JScrollBar verticalBar = scrollPane.getVerticalScrollBar();
                    verticalBar.setValue(verticalBar.getMaximum());
                });
            });
        });
    }

    public void clearMessages() {
        SwingUtilities.invokeLater(() -> {
            messagesPanel.removeAll();
            messagesPanel.revalidate();
            messagesPanel.repaint();
        });
    }

    public void setPlaceholderText(String text) {
        this.placeholderText = text;
        SwingUtilities.invokeLater(() -> inputField.repaint());
    }

    /**
     * Set the panel to "waiting for answer" mode
     * Changes the placeholder to indicate user should answer a question
     */
    public void setWaitingForAnswer(boolean waiting) {
        if (waiting) {
            setPlaceholderText("Rispondi alla domanda di Claude... (Ctrl+Enter to send)");
        } else {
            setPlaceholderText("Type a message... (Ctrl+Enter to send)");
        }
    }

    /**
     * Sets the working state - shows animated indicator when Claude is processing
     */
    public void setWorkingState(boolean working) {
        this.isWorking = working;

        SwingUtilities.invokeLater(() -> {
            if (working) {
                workingStartTime = System.currentTimeMillis();
                currentTool = null;  // Reset current tool
                workingLabel.setText("Claude sta elaborando...");

                // Start timer to update working indicator
                if (workingTimer == null) {
                    workingTimer = new Timer(500, e -> updateWorkingIndicator());
                    workingTimer.start();
                }
                statusLabel.setText("Working");
                statusLabel.setForeground(new Color(100, 160, 200));
            } else {
                if (workingTimer != null) {
                    workingTimer.stop();
                    workingTimer = null;
                }
                workingLabel.setText("");
                currentTool = null;
                statusLabel.setText("Ready");
                statusLabel.setForeground(TEXT_MUTED);
            }
        });
    }

    /**
     * Sets the current tool being used (e.g., "Read", "Write", "Bash")
     */
    public void setCurrentTool(String toolName) {
        this.currentTool = toolName;
        // Force immediate update of the working label
        updateWorkingIndicator();
    }

    /**
     * Updates the working indicator with elapsed time
     */
    private void updateWorkingIndicator() {
        if (!isWorking) return;

        long elapsed = System.currentTimeMillis() - workingStartTime;
        long seconds = elapsed / 1000;

        // Animated dots
        String[] dots = {".", "..", "...", "...."};
        int dotIndex = (int) ((elapsed / 500) % dots.length);

        SwingUtilities.invokeLater(() -> {
            String baseText;
            if (currentTool != null && !currentTool.isEmpty()) {
                baseText = "Usando: " + currentTool + dots[dotIndex];
            } else {
                baseText = "Claude sta elaborando" + dots[dotIndex];
            }

            if (seconds < 60) {
                workingLabel.setText(baseText + " (" + seconds + "s)");
            } else {
                long minutes = seconds / 60;
                long secs = seconds % 60;
                workingLabel.setText(baseText + " (" + minutes + "m " + secs + "s)");
            }
        });
    }

    /**
     * Updates token usage information
     */
    public void updateTokenInfo(int inputTokens, int outputTokens, int cacheTokens) {
        SwingUtilities.invokeLater(() -> {
            int totalTokens = inputTokens + outputTokens;
            // Approximate context window (200K for most models)
            int maxTokens = 200000;
            int remaining = maxTokens - totalTokens;

            if (cacheTokens > 0) {
                tokenLabel.setText(String.format("Tokens: %dK/%dK (cache: %dK)",
                    totalTokens / 1000, maxTokens / 1000, cacheTokens / 1000));
            } else {
                tokenLabel.setText(String.format("Tokens: %dK/%dK",
                    totalTokens / 1000, maxTokens / 1000));
            }
        });
    }

    /**
     * Sets a custom status message
     */
    public void setStatus(String status, Color color) {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText(status);
            if (color != null) {
                statusLabel.setForeground(color);
            }
        });
    }

    @Override
    public void dispose() {
        if (workingTimer != null) {
            workingTimer.stop();
            workingTimer = null;
        }
        if (cliWrapper != null) {
            cliWrapper.dispose();
        }
    }

    public enum MessageType {
        USER,
        CLAUDE,
        SYSTEM,
        TOOL,
        QUESTION
    }
}
