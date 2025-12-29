package com.cc.visualcc;

import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.icons.AllIcons;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Component for displaying a single message in the chat.
 * Styled to match Cline's clean, minimal aesthetic.
 */
public class VisualCCMessageComponent extends JPanel {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    // Muted professional color palette
    private static final Color BG_PRIMARY = new Color(30, 30, 30);
    private static final Color BG_CARD = new Color(37, 37, 38);
    private static final Color BG_CODE = new Color(28, 28, 30);
    private static final Color BORDER_COLOR = new Color(50, 50, 53);
    private static final Color TEXT_PRIMARY = new Color(195, 195, 195);
    private static final Color TEXT_SECONDARY = new Color(140, 140, 140);
    private static final Color TEXT_MUTED = new Color(100, 100, 100);
    
    // Muted message type colors
    private static final Color USER_ACCENT = new Color(90, 110, 145);     // Muted blue
    private static final Color CLAUDE_ACCENT = new Color(80, 130, 95);    // Muted green
    private static final Color TOOL_ACCENT = new Color(160, 140, 80);     // Muted gold
    private static final Color QUESTION_ACCENT = new Color(180, 120, 80); // Muted orange
    private static final Color SYSTEM_ACCENT = new Color(100, 100, 100);  // Gray
    private static final Color ERROR_ACCENT = new Color(170, 80, 80);     // Muted red

    private boolean isCollapsed = false;
    private JPanel contentPanel;
    private JLabel collapseIcon;

    public VisualCCMessageComponent(String sender, String text, VisualCCChatPanel.MessageType type) {
        setLayout(new BorderLayout());
        setOpaque(false);
        setBorder(JBUI.Borders.empty(4, 12));

        String timestamp = LocalDateTime.now().format(TIME_FORMAT);

        // Create message card
        JPanel card = createMessageCard(sender, text, type, timestamp);
        add(card, BorderLayout.CENTER);
    }

    private JPanel createMessageCard(String sender, String text, VisualCCChatPanel.MessageType type, String timestamp) {
        // Main card with rounded corners
        JPanel card = new JPanel(new BorderLayout(0, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(BG_CARD);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.dispose();
            }
        };
        card.setOpaque(false);
        card.setBorder(JBUI.Borders.empty(0));

        // Get styling based on type
        MessageStyle style = getMessageStyle(type);

        // Header with icon, sender, and timestamp
        JPanel header = createHeader(sender, timestamp, style, type);
        card.add(header, BorderLayout.NORTH);

        // Content area
        contentPanel = createContentPanel(text, style, type);
        card.add(contentPanel, BorderLayout.CENTER);

        return card;
    }

    private JPanel createHeader(String sender, String timestamp, MessageStyle style, VisualCCChatPanel.MessageType type) {
        JPanel header = new JPanel(new BorderLayout(8, 0));
        header.setOpaque(false);
        header.setBorder(JBUI.Borders.empty(10, 12, 6, 12));

        // Left side: collapse icon + status icon + sender
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        leftPanel.setOpaque(false);

        // Collapse/expand icon (for non-system messages)
        if (type != VisualCCChatPanel.MessageType.SYSTEM) {
            collapseIcon = new JLabel(AllIcons.General.ArrowDown);
            collapseIcon.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            collapseIcon.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    toggleCollapse();
                }
            });
            leftPanel.add(collapseIcon);
        }

        // Status/type icon with color
        JLabel statusIcon = new JLabel(style.icon);
        leftPanel.add(statusIcon);

        // Sender label
        JLabel senderLabel = new JLabel(style.displayName);
        senderLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        senderLabel.setForeground(style.accentColor);
        leftPanel.add(senderLabel);

        header.add(leftPanel, BorderLayout.WEST);

        // Right side: timestamp
        JLabel timeLabel = new JLabel(timestamp);
        timeLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        timeLabel.setForeground(TEXT_MUTED);
        header.add(timeLabel, BorderLayout.EAST);

        return header;
    }

    private JPanel createContentPanel(String text, MessageStyle style, VisualCCChatPanel.MessageType type) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.setBorder(JBUI.Borders.empty(0, 12, 10, 12));

        // Check if text contains code blocks or file paths
        if (containsCodeBlock(text)) {
            panel.add(createFormattedContent(text, style), BorderLayout.CENTER);
        } else if (containsFilePath(text)) {
            panel.add(createFilePathContent(text, style), BorderLayout.CENTER);
        } else {
            // Simple text content
            JTextArea textArea = createTextArea(text, style.textColor);
            panel.add(textArea, BorderLayout.CENTER);
        }

        return panel;
    }

    private JTextArea createTextArea(String text, Color textColor) {
        JTextArea textArea = new JTextArea(text);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setOpaque(false);
        textArea.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        textArea.setForeground(textColor);
        textArea.setBorder(null);
        textArea.setEditable(false);
        textArea.setFocusable(false);
        return textArea;
    }

    private boolean containsCodeBlock(String text) {
        return text.contains("```");
    }

    private boolean containsFilePath(String text) {
        return text.matches(".*[/\\\\][\\w.-]+\\.[\\w]+.*") || 
               text.toLowerCase().contains("wants to read") ||
               text.toLowerCase().contains("file:");
    }

    private JPanel createFormattedContent(String text, MessageStyle style) {
        JPanel container = new JPanel();
        container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
        container.setOpaque(false);

        String[] parts = text.split("```");
        for (int i = 0; i < parts.length; i++) {
            if (i % 2 == 0) {
                // Regular text
                if (!parts[i].trim().isEmpty()) {
                    JTextArea textArea = createTextArea(parts[i].trim(), style.textColor);
                    textArea.setAlignmentX(Component.LEFT_ALIGNMENT);
                    container.add(textArea);
                    container.add(Box.createVerticalStrut(6));
                }
            } else {
                // Code block
                JPanel codeBlock = createCodeBlock(parts[i]);
                codeBlock.setAlignmentX(Component.LEFT_ALIGNMENT);
                container.add(codeBlock);
                container.add(Box.createVerticalStrut(6));
            }
        }

        return container;
    }

    private JPanel createCodeBlock(String code) {
        // Extract language if present
        String language = "";
        String codeContent = code;
        if (code.contains("\n")) {
            int firstNewline = code.indexOf("\n");
            String firstLine = code.substring(0, firstNewline).trim();
            if (!firstLine.isEmpty() && firstLine.matches("[a-zA-Z]+")) {
                language = firstLine;
                codeContent = code.substring(firstNewline + 1);
            }
        }

        JPanel block = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(BG_CODE);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.setColor(BORDER_COLOR);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                g2.dispose();
            }
        };
        block.setOpaque(false);
        block.setBorder(JBUI.Borders.empty(8, 10));

        // Language label header
        if (!language.isEmpty()) {
            JLabel langLabel = new JLabel(language);
            langLabel.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            langLabel.setForeground(TEXT_MUTED);
            langLabel.setBorder(JBUI.Borders.empty(0, 0, 4, 0));
            block.add(langLabel, BorderLayout.NORTH);
        }

        JTextArea codeArea = new JTextArea(codeContent.trim());
        codeArea.setFont(new Font("JetBrains Mono", Font.PLAIN, 12));
        codeArea.setForeground(new Color(180, 180, 180));
        codeArea.setOpaque(false);
        codeArea.setEditable(false);
        codeArea.setFocusable(false);
        codeArea.setBorder(null);
        block.add(codeArea, BorderLayout.CENTER);

        return block;
    }

    private JPanel createFilePathContent(String text, MessageStyle style) {
        JPanel container = new JPanel();
        container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
        container.setOpaque(false);

        // Add text before file path
        String[] lines = text.split("\n");
        for (String line : lines) {
            if (looksLikeFilePath(line)) {
                JPanel fileBlock = createFileBlock(line.trim());
                fileBlock.setAlignmentX(Component.LEFT_ALIGNMENT);
                container.add(fileBlock);
            } else if (!line.trim().isEmpty()) {
                JTextArea textArea = createTextArea(line, style.textColor);
                textArea.setAlignmentX(Component.LEFT_ALIGNMENT);
                container.add(textArea);
            }
            container.add(Box.createVerticalStrut(4));
        }

        return container;
    }

    private boolean looksLikeFilePath(String line) {
        return line.matches(".*[/\\\\][\\w.-]+\\.[\\w]+.*") && 
               (line.contains("/") || line.contains("\\"));
    }

    private JPanel createFileBlock(String filePath) {
        JPanel block = new JPanel(new BorderLayout(8, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(35, 35, 38));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.setColor(BORDER_COLOR);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                g2.dispose();
            }
        };
        block.setOpaque(false);
        block.setBorder(JBUI.Borders.empty(8, 10));
        block.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        // File icon
        JLabel fileIcon = new JLabel(AllIcons.FileTypes.Text);
        block.add(fileIcon, BorderLayout.WEST);

        // File path
        JLabel pathLabel = new JLabel(filePath);
        pathLabel.setFont(new Font("JetBrains Mono", Font.PLAIN, 12));
        pathLabel.setForeground(new Color(130, 170, 255)); // Blue link color
        block.add(pathLabel, BorderLayout.CENTER);

        return block;
    }

    private void toggleCollapse() {
        isCollapsed = !isCollapsed;
        contentPanel.setVisible(!isCollapsed);
        collapseIcon.setIcon(isCollapsed ? AllIcons.General.ArrowRight : AllIcons.General.ArrowDown);
        revalidate();
        repaint();
    }

    private MessageStyle getMessageStyle(VisualCCChatPanel.MessageType type) {
        switch (type) {
            case USER:
                return new MessageStyle("You", AllIcons.Nodes.Function, USER_ACCENT, TEXT_PRIMARY);
            case CLAUDE:
                return new MessageStyle("Claude", AllIcons.Actions.Checked, CLAUDE_ACCENT, TEXT_PRIMARY);
            case TOOL:
                return new MessageStyle("Tool", AllIcons.Actions.Execute, TOOL_ACCENT, TEXT_PRIMARY);
            case QUESTION:
                return new MessageStyle("Question", AllIcons.General.QuestionDialog, QUESTION_ACCENT, TEXT_PRIMARY);
            case SYSTEM:
            default:
                return new MessageStyle("System", AllIcons.General.Information, SYSTEM_ACCENT, TEXT_SECONDARY);
        }
    }

    // Helper class for message styling
    private static class MessageStyle {
        final String displayName;
        final Icon icon;
        final Color accentColor;
        final Color textColor;

        MessageStyle(String displayName, Icon icon, Color accentColor, Color textColor) {
            this.displayName = displayName;
            this.icon = icon;
            this.accentColor = accentColor;
            this.textColor = textColor;
        }
    }
}
