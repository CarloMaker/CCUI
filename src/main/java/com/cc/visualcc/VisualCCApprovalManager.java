package com.cc.visualcc;

import com.cc.visualcc.model.ApprovalRequest;
import com.intellij.openapi.diagnostic.Logger;
import com.google.gson.JsonObject;

import javax.swing.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Manages approval requests and coordinates between the CLI wrapper and chat panel.
 * Handles the lifecycle of approval requests: display, user action, and response.
 */
public class VisualCCApprovalManager {
    private static final Logger LOG = Logger.getInstance(VisualCCApprovalManager.class);
    private static final DateTimeFormatter LOG_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private final VisualCCCliWrapper cliWrapper;
    private final VisualCCChatPanel chatPanel;
    private final ApprovalPreferences preferences;

    // Currently pending approval - volatile for thread safety
    private volatile ApprovalRequest pendingRequest = null;
    private volatile boolean isWaiting = false;

    public VisualCCApprovalManager(VisualCCCliWrapper cliWrapper, VisualCCChatPanel chatPanel, ApprovalPreferences preferences) {
        this.cliWrapper = cliWrapper;
        this.chatPanel = chatPanel;
        this.preferences = preferences;
    }

    /**
     * Check if a tool use requires approval (based on auto-approval setting).
     */
    public boolean shouldRequestApproval(boolean autoApprovalEnabled, String toolName) {
        // If auto-approve is enabled, bypass approval system
        if (autoApprovalEnabled) {
            log("Auto-approval enabled, bypassing approval check for: " + toolName);
            return false;
        }

        // Check if this tool is already approved (session or persistent)
        if (preferences.isToolApproved(toolName)) {
            log("Tool already approved (session or persistent): " + toolName);
            return false;
        }

        // Check if this tool requires approval
        return requiresApproval(toolName);
    }

    /**
     * Determine if a specific tool requires approval.
     */
    private boolean requiresApproval(String toolName) {
        switch (toolName) {
            case "Write":
            case "Edit":
            case "Bash":
                return true;
            default:
                return false;
        }
    }

    /**
     * Display an approval request to the user.
     */
    public void requestApproval(ApprovalRequest request) {
        log("Requesting approval for: " + request);

        SwingUtilities.invokeLater(() -> {
            try {
                // Store pending request
                pendingRequest = request;
                isWaiting = true;

                // Create approval component with callbacks for 4 buttons
                VisualCCApprovalComponent component = new VisualCCApprovalComponent(
                        request,
                        this::handleApprove,
                        this::handleDeny,
                        this::handleApproveAlways,
                        this::handleApproveSession
                );

                // Add to chat panel
                chatPanel.addApprovalComponent(component);
                chatPanel.scrollToBottom();
                chatPanel.setStatus("Waiting for approval...", new java.awt.Color(180, 140, 60));

                log("Approval UI displayed for tool: " + request.getToolName());

            } catch (Exception e) {
                log("Error displaying approval UI: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    /**
     * Handle user's approval decision.
     */
    private void handleApprove(ApprovalRequest request) {
        log("User APPROVED request: " + request);

        SwingUtilities.invokeLater(() -> {
            try {
                // Clear pending state
                pendingRequest = null;
                isWaiting = false;

                // Notify CLI wrapper to proceed
                cliWrapper.onApprovalDecision(request, true);

                // Update UI
                chatPanel.setStatus("Approved. Continuing...", new java.awt.Color(80, 140, 90));
                chatPanel.setWorkingState(true);

                log("Approval sent to CLI for tool: " + request.getToolName());

            } catch (Exception e) {
                log("Error handling approval: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    /**
     * Handle user's denial decision.
     */
    private void handleDeny(ApprovalRequest request) {
        log("User DENIED request: " + request);

        SwingUtilities.invokeLater(() -> {
            try {
                // Clear pending state
                pendingRequest = null;
                isWaiting = false;

                // Notify CLI wrapper to pause
                cliWrapper.onApprovalDecision(request, false);

                // Update UI - pause and ask for instructions
                chatPanel.addMessage("System",
                        "Operation denied. " + request.getToolName() + " on " +
                                (request.getFilePath() != null ? request.getFilePath() : "target") +
                                " was cancelled.\n\nPlease provide instructions on how to proceed.",
                        VisualCCChatPanel.MessageType.SYSTEM);
                chatPanel.setPlaceholderText("Provide instructions on how to proceed...");
                chatPanel.setStatus("Operation denied. Waiting for instructions...", new java.awt.Color(170, 80, 80));

                log("Denial sent to CLI, waiting for user instructions");

            } catch (Exception e) {
                log("Error handling denial: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    /**
     * Handle user's "Approve Always" decision - persistent approval.
     */
    private void handleApproveAlways(ApprovalRequest request) {
        log("User APPROVED ALWAYS for tool: " + request.getToolName());

        SwingUtilities.invokeLater(() -> {
            try {
                // Add to persistent approvals
                preferences.approvePersistently(request.getToolName());
                log("Added tool to persistent approvals: " + request.getToolName());

                // Clear pending state
                pendingRequest = null;
                isWaiting = false;

                // Notify CLI wrapper to proceed
                cliWrapper.onApprovalDecision(request, true);

                // Update UI
                chatPanel.setStatus("Approved always. Continuing...", new java.awt.Color(100, 100, 160));
                chatPanel.setWorkingState(true);

                log("Persistent approval sent to CLI for tool: " + request.getToolName());

            } catch (Exception e) {
                log("Error handling approve always: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    /**
     * Handle user's "Approve Session" decision - session-only approval.
     */
    private void handleApproveSession(ApprovalRequest request) {
        log("User APPROVED SESSION for tool: " + request.getToolName());

        SwingUtilities.invokeLater(() -> {
            try {
                // Add to session approvals
                preferences.approveForSession(request.getToolName());
                log("Added tool to session approvals: " + request.getToolName());

                // Clear pending state
                pendingRequest = null;
                isWaiting = false;

                // Notify CLI wrapper to proceed
                cliWrapper.onApprovalDecision(request, true);

                // Update UI
                chatPanel.setStatus("Approved for session. Continuing...", new java.awt.Color(70, 130, 180));
                chatPanel.setWorkingState(true);

                log("Session approval sent to CLI for tool: " + request.getToolName());

            } catch (Exception e) {
                log("Error handling approve session: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    /**
     * Parse an approval request from a tool_use JSON object.
     */
    public ApprovalRequest parseApprovalRequest(String toolName, String toolUseId, JsonObject input) {
        log("Parsing approval request - Tool: " + toolName + ", ID: " + toolUseId);

        ApprovalRequest.Builder builder = new ApprovalRequest.Builder()
                .toolName(toolName)
                .toolUseId(toolUseId);

        // Extract file path if present
        if (input.has("file_path")) {
            String filePath = input.get("file_path").getAsString();
            builder.filePath(filePath);
        }

        // Extract content if present (for Write operations)
        if (input.has("content")) {
            String content = input.get("content").getAsString();
            builder.fullContent(content);
        }

        // Build description based on tool type
        String description = buildDescription(toolName, input);
        builder.description(description);

        // Set operation type
        String operation = toolName.toLowerCase();
        if (toolName.equals("Bash")) {
            operation = "command";
        }
        builder.operation(operation);

        ApprovalRequest request = builder.build();
        log("Parsed approval request: " + request);
        return request;
    }

    /**
     * Build a human-readable description for the approval request.
     */
    private String buildDescription(String toolName, JsonObject input) {
        switch (toolName) {
            case "Write":
                String filePath = input.has("file_path") ? input.get("file_path").getAsString() : "file";
                return "Claude wants to **write** to the file: `" + filePath + "`";

            case "Edit":
                String editPath = input.has("file_path") ? input.get("file_path").getAsString() : "file";
                return "Claude wants to **edit** the file: `" + editPath + "`";

            case "Bash":
                String command = input.has("command") ? input.get("command").getAsString() : "command";
                // Truncate long commands
                if (command.length() > 60) {
                    command = command.substring(0, 60) + "...";
                }
                return "Claude wants to **execute** command: `" + command + "`";

            default:
                return "Claude wants to perform operation: **" + toolName + "**";
        }
    }

    /**
     * Check if currently waiting for an approval decision.
     */
    public boolean isWaitingForApproval() {
        return isWaiting;
    }

    /**
     * Get the currently pending approval request.
     */
    public ApprovalRequest getPendingRequest() {
        return pendingRequest;
    }

    /**
     * Clear the pending approval state (e.g., when session restarts).
     */
    public void clearPending() {
        log("Clearing pending approval state");
        pendingRequest = null;
        isWaiting = false;
    }

    private void log(String message) {
        String timestamp = LocalDateTime.now().format(LOG_FORMAT);
        String logLine = "[" + timestamp + "] [ApprovalManager] " + message;
        LOG.info(logLine);
        System.out.println(logLine);
    }
}
