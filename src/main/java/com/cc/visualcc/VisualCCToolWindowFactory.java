package com.cc.visualcc;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.serviceContainer.NonInjectable;
import org.jetbrains.annotations.NotNull;

/**
 * Factory for creating the VisualCC Tool Window.
 * Creates a side panel with the chat UI for Claude Code CLI interaction.
 */
public class VisualCCToolWindowFactory implements ToolWindowFactory {

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        System.out.println("[VisualCC] Creating tool window content for project: " + project.getName());
        VisualCCChatPanel chatPanel = new VisualCCChatPanel(project);

        ContentFactory contentFactory = ContentFactory.SERVICE.getInstance();
        Content content = contentFactory.createContent(chatPanel, "", false);
        toolWindow.getContentManager().addContent(content);

        System.out.println("[VisualCC] Content added to tool window");

        // Auto-start the CLI wrapper when tool window is shown
        chatPanel.startCLI();
        System.out.println("[VisualCC] CLI start initiated");
    }
}
