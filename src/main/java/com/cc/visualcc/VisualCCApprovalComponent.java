package com.cc.visualcc;

import com.cc.visualcc.model.ApprovalRequest;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

/**
 * Component for displaying an approval request with Approve/Deny buttons.
 * Extends the message component pattern with interactive approval UI.
 */
public class VisualCCApprovalComponent extends JPanel {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    // Color scheme
    private static final Color BG_PRIMARY = new Color(30, 30, 30);
    private static final Color BG_CARD = new Color(37, 37, 38);
    private static final Color BORDER_COLOR = new Color(50, 50, 53);
    private static final Color TEXT_PRIMARY = new Color(195, 195, 195);
    private static final Color TEXT_MUTED = new Color(100, 100, 100);

    // Approval-specific colors
    private static final Color APPROVAL_ACCENT = new Color(180, 140, 60); // Gold/yellow for approval requests
    private static final Color APPROVE_GREEN = new Color(80, 140, 90);
    private static final Color APPROVE_GREEN_HOVER = new Color(100, 160, 110);
    private static final Color DENY_RED = new Color(170, 80, 80);
    private static final Color DENY_RED_HOVER = new Color(190, 100, 100);

    private final ApprovalRequest request;
    private final Consumer<ApprovalRequest> onApprove;
    private final Consumer<ApprovalRequest> onDeny;
    private final Consumer<ApprovalRequest> onApproveAlways;
    private final Consumer<ApprovalRequest> onApproveSession;
    private JButton approveButton;
    private JButton denyButton;
    private JButton approveAlwaysButton;
    private JButton approveSessionButton;
    private boolean actionTaken = false;

    public VisualCCApprovalComponent(ApprovalRequest request,
                                     Consumer<ApprovalRequest> onApprove,
                                     Consumer<ApprovalRequest> onDeny,
                                     Consumer<ApprovalRequest> onApproveAlways,
                                     Consumer<ApprovalRequest> onApproveSession) {
        this.request = request;
        this.onApprove = onApprove;
        this.onDeny = onDeny;
        this.onApproveAlways = onApproveAlways;
        this.onApproveSession = onApproveSession;

        setLayout(new BorderLayout());
        setOpaque(false);
        setBorder(JBUI.Borders.empty(4, 12));

        String timestamp = LocalDateTime.now().format(TIME_FORMAT);
        JPanel card = createApprovalCard(timestamp);
        add(card, BorderLayout.CENTER);
    }

    private JPanel createApprovalCard(String timestamp) {
        JPanel card = new JPanel(new BorderLayout(0, 8)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(BG_CARD);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                // Draw border with approval accent
                g2.setColor(APPROVAL_ACCENT);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                g2.dispose();
            }
        };
        card.setOpaque(false);
        card.setBorder(JBUI.Borders.empty(8));

        // Header
        JPanel header = createHeader(timestamp);
        card.add(header, BorderLayout.NORTH);

        // Content
        JPanel content = createContent();
        card.add(content, BorderLayout.CENTER);

        // Action buttons
        JPanel actions = createActionButtons();
        card.add(actions, BorderLayout.SOUTH);

        return card;
    }

    private JPanel createHeader(String timestamp) {
        JPanel header = new JPanel(new BorderLayout(8, 0));
        header.setOpaque(false);
        header.setBorder(JBUI.Borders.empty(0, 12, 6, 12));

        // Left: icon + title
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        leftPanel.setOpaque(false);

        // Warning icon
        JLabel iconLabel = new JLabel(com.intellij.icons.AllIcons.General.BalloonWarning);
        leftPanel.add(iconLabel);

        // Title
        JLabel titleLabel = new JLabel("Approval Required");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        titleLabel.setForeground(APPROVAL_ACCENT);
        leftPanel.add(titleLabel);

        header.add(leftPanel, BorderLayout.WEST);

        // Right: timestamp
        JLabel timeLabel = new JLabel(timestamp);
        timeLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        timeLabel.setForeground(TEXT_MUTED);
        header.add(timeLabel, BorderLayout.EAST);

        return header;
    }

    private JPanel createContent() {
        JPanel content = new JPanel(new BorderLayout());
        content.setOpaque(false);
        content.setBorder(JBUI.Borders.empty(0, 12, 8, 12));

        JTextArea textArea = new JTextArea(request.getDisplayText());
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setOpaque(false);
        textArea.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        textArea.setForeground(TEXT_PRIMARY);
        textArea.setBorder(null);
        textArea.setEditable(false);
        textArea.setFocusable(false);

        content.add(textArea, BorderLayout.CENTER);
        return content;
    }

    private JPanel createActionButtons() {
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        buttonPanel.setOpaque(false);
        buttonPanel.setBorder(JBUI.Borders.empty(0, 12, 8, 12));

        // Approve button (one-time)
        approveButton = createActionButton("Approve", APPROVE_GREEN, APPROVE_GREEN_HOVER);
        approveButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                handleApprove();
            }
        });
        buttonPanel.add(approveButton);

        // Approve Session button (this IDE session only)
        approveSessionButton = createActionButton("Approve Session", new Color(70, 130, 180), new Color(90, 150, 200));
        approveSessionButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                handleApproveSession();
            }
        });
        buttonPanel.add(approveSessionButton);

        // Approve Always button (persistent, saved to disk)
        approveAlwaysButton = createActionButton("Approve Always", new Color(100, 100, 160), new Color(120, 120, 180));
        approveAlwaysButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                handleApproveAlways();
            }
        });
        buttonPanel.add(approveAlwaysButton);

        // Deny button
        denyButton = createActionButton("Deny", DENY_RED, DENY_RED_HOVER);
        denyButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                handleDeny();
            }
        });
        buttonPanel.add(denyButton);

        return buttonPanel;
    }

    private JButton createActionButton(String text, Color bgColor, Color hoverColor) {
        return new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                if (getModel().isRollover() && !actionTaken) {
                    g2.setColor(hoverColor);
                } else {
                    g2.setColor(bgColor);
                }
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
                g2.dispose();
                super.paintComponent(g);
            }
        };
    }

    private void handleApprove() {
        if (actionTaken) return;
        actionTaken = true;
        disableButtons();

        // Mouse events already execute on EDT, no need for invokeLater
        if (onApprove != null) {
            onApprove.accept(request);
        }
    }

    private void handleDeny() {
        if (actionTaken) return;
        actionTaken = true;
        disableButtons();

        // Mouse events already execute on EDT, no need for invokeLater
        if (onDeny != null) {
            onDeny.accept(request);
        }
    }

    private void handleApproveSession() {
        if (actionTaken) return;
        actionTaken = true;
        disableButtons();

        // Mouse events already execute on EDT, no need for invokeLater
        if (onApproveSession != null) {
            onApproveSession.accept(request);
        }
    }

    private void handleApproveAlways() {
        if (actionTaken) return;
        actionTaken = true;
        disableButtons();

        // Mouse events already execute on EDT, no need for invokeLater
        if (onApproveAlways != null) {
            onApproveAlways.accept(request);
        }
    }

    private void disableButtons() {
        approveButton.setEnabled(false);
        denyButton.setEnabled(false);
        approveSessionButton.setEnabled(false);
        approveAlwaysButton.setEnabled(false);
        approveButton.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        denyButton.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        approveSessionButton.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        approveAlwaysButton.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
    }

    /**
     * Called when this approval component is being removed or replaced.
     * Ensures callbacks can't be fired after removal.
     * Note: The actionTaken flag is the primary protection against duplicate callbacks.
     */
    public void dispose() {
        actionTaken = true;
        // Note: We don't remove mouse listeners here since:
        // 1. The actionTaken flag prevents callbacks from firing
        // 2. Removing listeners by index [0] is fragile
        // 3. This component will be garbage collected after removal from parent
    }

    public ApprovalRequest getRequest() {
        return request;
    }
}
