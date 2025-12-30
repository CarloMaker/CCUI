package com.cc.visualcc;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.openapi.project.Project;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Manages approval preferences - both per session and persistent.
 * Allows users to auto-approve specific tools for the current project.
 */
public class ApprovalPreferences {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String PREFS_FILE = ".visualcc-approvals.json";

    private final Project project;
    private final String projectPath;
    private final File prefsFile;

    // Session-based approvals (cleared when IDE restarts)
    private final Set<String> sessionApprovedTools = new HashSet<>();

    // Persistent approvals (saved to disk)
    private final Set<String> persistentApprovedTools = new HashSet<>();

    public ApprovalPreferences(Project project) {
        this.project = project;

        // Handle case where project has no base path (e.g., new unsaved project)
        String path = project.getBasePath();
        if (path == null || path.isEmpty()) {
            // Fallback to user home directory
            path = System.getProperty("user.home");
            System.err.println("[ApprovalPreferences] WARNING: Project has no base path, using home directory: " + path);
        }
        this.projectPath = path;

        this.prefsFile = new File(projectPath, PREFS_FILE);
        loadPreferences();
    }

    /**
     * Check if a tool has been approved (either session or persistent)
     */
    public boolean isToolApproved(String toolName) {
        return sessionApprovedTools.contains(toolName) || persistentApprovedTools.contains(toolName);
    }

    /**
     * Approve a tool for the current session only
     */
    public void approveForSession(String toolName) {
        sessionApprovedTools.add(toolName);
    }

    /**
     * Approve a tool persistently (saved to disk)
     */
    public void approvePersistently(String toolName) {
        persistentApprovedTools.add(toolName);
        savePreferences();
    }

    /**
     * Remove approval for a tool
     */
    public void removeApproval(String toolName) {
        sessionApprovedTools.remove(toolName);
        persistentApprovedTools.remove(toolName);
        savePreferences();
    }

    /**
     * Clear all session approvals (called when session restarts)
     */
    public void clearSessionApprovals() {
        sessionApprovedTools.clear();
    }

    /**
     * Get all approved tools (session + persistent)
     */
    public Set<String> getAllApprovedTools() {
        Set<String> all = new HashSet<>();
        all.addAll(sessionApprovedTools);
        all.addAll(persistentApprovedTools);
        return all;
    }

    /**
     * Load preferences from disk
     */
    private void loadPreferences() {
        if (!prefsFile.exists()) {
            return;
        }

        try (FileReader reader = new FileReader(prefsFile)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();

            // Load persistent approvals
            if (json.has("persistentApprovals")) {
                JsonElement elem = json.get("persistentApprovals");
                if (elem.isJsonArray()) {
                    for (JsonElement toolEl : elem.getAsJsonArray()) {
                        persistentApprovedTools.add(toolEl.getAsString());
                    }
                }
            }

            System.out.println("[ApprovalPreferences] Loaded " + persistentApprovedTools.size() + " persistent approvals");
        } catch (IOException e) {
            System.err.println("[ApprovalPreferences] Error loading preferences: " + e.getMessage());
        }
    }

    /**
     * Save preferences to disk
     */
    private void savePreferences() {
        try {
            JsonObject json = new JsonObject();

            // Save persistent approvals as JSON array
            com.google.gson.JsonArray approvalsArray = new com.google.gson.JsonArray();
            for (String tool : persistentApprovedTools) {
                approvalsArray.add(tool);
            }
            json.add("persistentApprovals", approvalsArray);

            // Write to file
            try (FileWriter writer = new FileWriter(prefsFile)) {
                GSON.toJson(json, writer);
            }

            System.out.println("[ApprovalPreferences] Saved " + persistentApprovedTools.size() + " persistent approvals");
        } catch (IOException e) {
            System.err.println("[ApprovalPreferences] Error saving preferences: " + e.getMessage());
        }
    }

    /**
     * Clear all persistent approvals (for testing/reset)
     */
    public void clearAllPersistentApprovals() {
        persistentApprovedTools.clear();
        if (prefsFile.exists()) {
            prefsFile.delete();
        }
        System.out.println("[ApprovalPreferences] All persistent approvals cleared");
    }
}
