package com.cc.visualcc;

import com.intellij.util.ui.JBUI;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Component for displaying Claude's questions with clickable option buttons.
 * Questions are shown in a collapsible card with buttons below.
 */
public class VisualCCQuestionComponent extends JPanel {

    private static final Color BG_PRIMARY = new Color(30, 30, 30);
    private static final Color BG_CARD = new Color(37, 37, 38);
    private static final Color BORDER_COLOR = new Color(50, 50, 53);
    private static final Color TEXT_PRIMARY = new Color(195, 195, 195);
    private static final Color TEXT_MUTED = new Color(100, 100, 100);
    private static final Color QUESTION_ACCENT = new Color(180, 120, 80); // Orange
    private static final Color BUTTON_BG = new Color(70, 130, 180); // Blue
    private static final Color BUTTON_HOVER = new Color(90, 150, 200);

    private final String questionsText;
    private final List<String> options;
    private final Consumer<String> onOptionSelected;

    private boolean actionTaken = false;
    private JPanel contentPanel;
    private JLabel collapseIcon;
    private boolean isCollapsed = false;

    public VisualCCQuestionComponent(String questionsText, List<String> options, Consumer<String> onOptionSelected) {
        this.questionsText = questionsText;
        this.options = options;
        this.onOptionSelected = onOptionSelected;

        setLayout(new BorderLayout());
        setOpaque(false);
        setBorder(JBUI.Borders.empty(4, 12, 4, 12));

        JPanel card = createQuestionCard();
        add(card, BorderLayout.CENTER);
    }

    private JPanel createQuestionCard() {
        JPanel card = new JPanel(new BorderLayout(0, 8)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(BG_CARD);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.setColor(QUESTION_ACCENT);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                g2.dispose();
            }
        };
        card.setOpaque(false);
        card.setBorder(JBUI.Borders.empty(8));

        // Header with collapse icon
        JPanel header = createHeader();
        card.add(header, BorderLayout.NORTH);

        // Content area (questions text)
        contentPanel = createContentPanel();
        card.add(contentPanel, BorderLayout.CENTER);

        // Options buttons section
        if (!options.isEmpty()) {
            JPanel optionsPanel = createOptionsPanel();
            card.add(optionsPanel, BorderLayout.SOUTH);
        }

        return card;
    }

    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout(8, 0));
        header.setOpaque(false);
        header.setBorder(JBUI.Borders.empty(0, 8, 6, 8));

        // Left: collapse icon + question icon + title
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        leftPanel.setOpaque(false);

        // Collapse/expand icon
        collapseIcon = new JLabel(com.intellij.icons.AllIcons.General.ArrowDown);
        collapseIcon.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        collapseIcon.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                toggleCollapse();
            }
        });
        leftPanel.add(collapseIcon);

        // Question icon
        JLabel iconLabel = new JLabel(com.intellij.icons.AllIcons.General.QuestionDialog);
        leftPanel.add(iconLabel);

        // Title
        JLabel titleLabel = new JLabel("Domande di Claude");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        titleLabel.setForeground(QUESTION_ACCENT);
        leftPanel.add(titleLabel);

        header.add(leftPanel, BorderLayout.WEST);

        return header;
    }

    private JPanel createContentPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.setBorder(JBUI.Borders.empty(0, 8, 8, 8));

        // Questions text area
        JTextArea textArea = new JTextArea(questionsText);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setOpaque(false);
        textArea.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        textArea.setForeground(TEXT_PRIMARY);
        textArea.setBorder(null);
        textArea.setEditable(false);
        textArea.setFocusable(false);

        panel.add(textArea, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createOptionsPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setOpaque(false);
        panel.setBorder(JBUI.Borders.empty(4, 8, 4, 8));

        // Separator line
        JSeparator separator = new JSeparator();
        separator.setForeground(BORDER_COLOR);
        panel.add(separator, BorderLayout.NORTH);

        // Label
        JLabel label = new JLabel("Risposte suggerite:");
        label.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        label.setForeground(TEXT_MUTED);
        panel.add(label, BorderLayout.CENTER);

        // Buttons
        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttonsPanel.setOpaque(false);

        for (String option : options) {
            JButton button = createOptionButton(option);
            buttonsPanel.add(button);
        }

        panel.add(buttonsPanel, BorderLayout.SOUTH);

        return panel;
    }

    private JButton createOptionButton(String optionText) {
        JButton button = new JButton(optionText) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                if (getModel().isRollover() && !actionTaken) {
                    g2.setColor(BUTTON_HOVER);
                } else {
                    g2.setColor(BUTTON_BG);
                }
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
                g2.dispose();
                super.paintComponent(g);
            }
        };

        button.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        button.setForeground(Color.WHITE);
        button.setBorder(JBUI.Borders.empty(6, 12));
        button.setFocusPainted(false);
        button.setContentAreaFilled(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        button.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                handleOptionClick(optionText);
            }
        });

        return button;
    }

    private void handleOptionClick(String optionText) {
        if (actionTaken) return;
        actionTaken = true;

        // Disable all buttons
        disableAllButtons();

        // Call callback with selected option
        if (onOptionSelected != null) {
            onOptionSelected.accept(optionText);
        }
    }

    private void disableAllButtons() {
        // Find and disable all buttons in this component
        for (Component comp : getComponents()) {
            if (comp instanceof JPanel) {
                disableButtonsInPanel((JPanel) comp);
            }
        }
    }

    private void disableButtonsInPanel(JPanel panel) {
        for (Component comp : panel.getComponents()) {
            if (comp instanceof JButton) {
                JButton btn = (JButton) comp;
                btn.setEnabled(false);
                btn.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
            } else if (comp instanceof JPanel) {
                disableButtonsInPanel((JPanel) comp);
            }
        }
    }

    private void toggleCollapse() {
        isCollapsed = !isCollapsed;
        contentPanel.setVisible(!isCollapsed);
        collapseIcon.setIcon(isCollapsed ?
            com.intellij.icons.AllIcons.General.ArrowRight :
            com.intellij.icons.AllIcons.General.ArrowDown);
        revalidate();
        repaint();
    }

    public boolean isActionTaken() {
        return actionTaken;
    }

    public void dispose() {
        actionTaken = true;
    }
}
