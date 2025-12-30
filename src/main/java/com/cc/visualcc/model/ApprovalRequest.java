package com.cc.visualcc.model;

/**
 * Data model for a permission/approval request from Claude CLI.
 * Encapsulates information about a tool operation that requires user approval.
 */
public class ApprovalRequest {
    private final String toolName;      // "Write", "Edit", "Bash", etc.
    private final String toolUseId;     // Unique ID for this tool use
    private final String filePath;      // Target file path (if applicable)
    private final String operation;     // "write", "edit", "delete", "command", etc.
    private final String description;   // Human-readable description
    private final String fullContent;   // Full content being written (for Write operations)

    private ApprovalRequest(Builder builder) {
        this.toolName = builder.toolName;
        this.toolUseId = builder.toolUseId;
        this.filePath = builder.filePath;
        this.operation = builder.operation;
        this.description = builder.description;
        this.fullContent = builder.fullContent;
    }

    public String getToolName() {
        return toolName;
    }

    public String getToolUseId() {
        return toolUseId;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getOperation() {
        return operation;
    }

    public String getDescription() {
        return description;
    }

    public String getFullContent() {
        return fullContent;
    }

    /**
     * Generate a human-readable display text for this approval request.
     */
    public String getDisplayText() {
        StringBuilder sb = new StringBuilder();
        sb.append("**Permission Request**\n\n");

        if (description != null && !description.isEmpty()) {
            sb.append(description).append("\n\n");
        }

        sb.append("**Details:**\n");
        sb.append("- Tool: ").append(toolName);

        if (operation != null) {
            sb.append("\n- Operation: ").append(operation);
        }

        if (filePath != null) {
            sb.append("\n- File: ").append(filePath);
        }

        if (fullContent != null && fullContent.length() > 0) {
            int previewLength = Math.min(200, fullContent.length());
            sb.append("\n- Content preview: ").append(fullContent.substring(0, previewLength));
            if (fullContent.length() > 200) {
                sb.append("...");
            }
        }

        sb.append("\n\nDo you want to approve this operation?");

        return sb.toString();
    }

    public static class Builder {
        private String toolName;
        private String toolUseId;
        private String filePath;
        private String operation;
        private String description;
        private String fullContent;

        public Builder toolName(String toolName) {
            this.toolName = toolName;
            return this;
        }

        public Builder toolUseId(String toolUseId) {
            this.toolUseId = toolUseId;
            return this;
        }

        public Builder filePath(String filePath) {
            this.filePath = filePath;
            return this;
        }

        public Builder operation(String operation) {
            this.operation = operation;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder fullContent(String fullContent) {
            this.fullContent = fullContent;
            return this;
        }

        public ApprovalRequest build() {
            if (toolName == null || toolName.isEmpty()) {
                throw new IllegalStateException("toolName is required");
            }
            if (toolUseId == null || toolUseId.isEmpty()) {
                throw new IllegalStateException("toolUseId is required");
            }
            return new ApprovalRequest(this);
        }
    }

    @Override
    public String toString() {
        return "ApprovalRequest{" +
                "toolName='" + toolName + '\'' +
                ", toolUseId='" + toolUseId + '\'' +
                ", filePath='" + filePath + '\'' +
                ", operation='" + operation + '\'' +
                '}';
    }
}
