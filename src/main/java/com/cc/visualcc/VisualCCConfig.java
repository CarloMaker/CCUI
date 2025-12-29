package com.cc.visualcc;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Persistent configuration for VisualCC plugin.
 * Stores user preferences for Claude Code CLI integration.
 */
@State(
        name = "VisualCCConfig",
        storages = @Storage("VisualCC.xml")
)
public class VisualCCConfig implements PersistentStateComponent<VisualCCConfig.State> {

    public static class State {
        public boolean ENABLED = true;
        public String CLI_PATH = ""; // Path to claude CLI executable
        public boolean INCLUDE_TIMESTAMPS = true;
        public boolean AUTO_START = true; // Auto-start CLI when tool window opens
        public String THEME = "MODERN"; // MODERN, DARK, LIGHT
    }

    private State myState = new State();

    public static VisualCCConfig getInstance() {
        return ApplicationManager.getApplication().getService(VisualCCConfig.class);
    }

    @Override
    public @Nullable State getState() {
        return myState;
    }

    @Override
    public void loadState(@NotNull State state) {
        XmlSerializerUtil.copyBean(state, myState);
    }

    // Getters and setters
    public boolean isEnabled() {
        return myState.ENABLED;
    }

    public void setEnabled(boolean enabled) {
        myState.ENABLED = enabled;
    }

    public String getCliPath() {
        return myState.CLI_PATH;
    }

    public void setCliPath(String path) {
        myState.CLI_PATH = path;
    }

    public boolean includeTimestamps() {
        return myState.INCLUDE_TIMESTAMPS;
    }

    public void setIncludeTimestamps(boolean include) {
        myState.INCLUDE_TIMESTAMPS = include;
    }

    public boolean isAutoStart() {
        return myState.AUTO_START;
    }

    public void setAutoStart(boolean autoStart) {
        myState.AUTO_START = autoStart;
    }

    public String getTheme() {
        return myState.THEME;
    }

    public void setTheme(String theme) {
        myState.THEME = theme;
    }
}

