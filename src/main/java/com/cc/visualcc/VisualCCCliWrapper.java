package com.cc.visualcc;

import com.cc.visualcc.model.ApprovalRequest;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.*;
import java.awt.Color;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Wrapper for Claude Code CLI process.
 * Uses JSON stdin input with stream-json output format (like Kilo Code).
 */
public class VisualCCCliWrapper {

    // File logging
    private FileWriter logFileWriter;
    private static final DateTimeFormatter LOG_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private final Project project;
    private final VisualCCChatPanel chatPanel;
    private Process cliProcess;
    private Thread outputThread;
    private Thread errorThread;
    private final AtomicBoolean running = new AtomicBoolean(false);

    // Process I/O
    private OutputStream stdinStream;  // Keep open for approvals

    // Approval manager
    private VisualCCApprovalManager approvalManager;

    // Approval preferences (session + persistent)
    private ApprovalPreferences approvalPreferences;

    // Plan mode setting
    private boolean planMode = false;

    // Auto-approval setting
    private boolean autoApprovalEnabled = true;  // Default to auto-approve

    // User question response handling - following Kline approach
    private boolean waitingForAnswer = false;
    private StringBuilder accumulatedUserMessage = new StringBuilder();  // Accumulate questions + answers

    // Conversation history to maintain context across CLI sessions
    // Now passed in from chat panel to persist across STOP/restart
    private JsonArray conversationHistory;

    // JSON parsing
    private final Gson gson = new Gson();

    /**
     * Constructor for creating a new wrapper with existing conversation history
     * This allows context to persist across STOP/restart scenarios
     */
    public VisualCCCliWrapper(Project project, VisualCCChatPanel chatPanel, com.google.gson.JsonArray existingHistory) {
        this.project = project;
        this.chatPanel = chatPanel;
        this.approvalPreferences = new ApprovalPreferences(project);
        this.approvalManager = new VisualCCApprovalManager(this, chatPanel, approvalPreferences);
        this.conversationHistory = existingHistory != null ? existingHistory : new com.google.gson.JsonArray();
        initFileLogger();
        log(">>> Wrapper created with history size: " + conversationHistory.size());
    }

    /**
     * Legacy constructor for backward compatibility - creates empty history
     */
    public VisualCCCliWrapper(Project project, VisualCCChatPanel chatPanel) {
        this(project, chatPanel, null);
    }

    /**
     * Initialize file logger
     */
    private void initFileLogger() {
        try {
            String logDir = System.getProperty("user.home") + File.separator + ".visualcc";
            File dir = new File(logDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            File logFile = new File(logDir + File.separator + "visualcc.log");
            logFileWriter = new FileWriter(logFile, false);  // Overwrite each session
            log("==========================================");
            log("VisualCC Logger Initialized");
            log("Log file: " + logFile.getAbsolutePath());
            log("==========================================");
        } catch (IOException e) {
            System.err.println("[VisualCC] Failed to initialize file logger: " + e.getMessage());
        }
    }

    /**
     * Log message to both console and file
     */
    private void log(String message) {
        String timestamp = LocalDateTime.now().format(LOG_FORMAT);
        String logLine = "[" + timestamp + "] " + message;

        // Always print to console
        System.out.println(logLine);

        // Also write to file if available
        if (logFileWriter != null) {
            try {
                logFileWriter.write(logLine + "\n");
                logFileWriter.flush();
            } catch (IOException e) {
                System.err.println("[VisualCC] Failed to write to log file: " + e.getMessage());
            }
        }
    }

    public void start() {
        log(">>> start() called");
        String claudeCommand = findClaudeCommand();
        log("Claude command: " + claudeCommand);
        if (claudeCommand == null) {
            log("ERROR: Claude command not found!");
            chatPanel.addMessage("System",
                    "Claude Code CLI not found!\n\n" +
                    "Please install Claude Code CLI from: https://claude.ai/code\n\n" +
                    "Or configure the path in Settings → Tools → VisualCC",
                    VisualCCChatPanel.MessageType.SYSTEM);
            return;
        }

        running.set(true);
        chatPanel.addMessage("System", "VisualCC ready. Connected to: " + claudeCommand,
                VisualCCChatPanel.MessageType.SYSTEM);
        log("VisualCC started successfully");
    }

    private String findClaudeCommand() {
        // Try common locations
        String[] possibleCommands = {
                "claude",                    // If in PATH
                "claude-code",               // Alternative name
        };

        // Windows-specific npm paths
        if (SystemInfo.isWindows) {
            String npmPath = System.getenv("APPDATA") + "\\npm\\claude.cmd";
            if (new File(npmPath).exists()) {
                return npmPath;
            }
        }

        // Check config first
        String configuredPath = VisualCCConfig.getInstance().getCliPath();
        if (configuredPath != null && !configuredPath.trim().isEmpty()) {
            if (new File(configuredPath).exists()) {
                return configuredPath;
            }
        }

        // Try PATH
        for (String cmd : possibleCommands) {
            try {
                Process p = Runtime.getRuntime().exec(
                        SystemInfo.isWindows ? new String[]{"cmd", "/c", "where", cmd}
                                            : new String[]{"which", cmd});
                if (p.waitFor() == 0) {
                    return cmd.split("\\\\|/")[0]; // Get first match
                }
            } catch (Exception ignored) {
            }
        }

        return null;
    }

    public void sendInput(String userMessage) {
        log(">>> sendInput() called with: '" + userMessage + "'");
        log(">>> waitingForAnswer = " + waitingForAnswer);

        // Kline approach: if waiting for answer, accumulate the response
        if (waitingForAnswer) {
            log(">>> MODE: Accumulating ANSWER to question");
            accumulatedUserMessage.append("\n\n[My answer: ").append(userMessage).append("]");
            log(">>> Accumulated message: " + accumulatedUserMessage.toString());
            waitingForAnswer = false;
            chatPanel.setWaitingForAnswer(false);
            log(">>> Sending accumulated message (original + answer)");
            // Now send the accumulated message (original + answer)
            sendInputInternal(accumulatedUserMessage.toString());
            accumulatedUserMessage.setLength(0); // Clear for next time
            return;
        }

        // Normal message - start fresh
        log(">>> MODE: Normal message");
        sendInputInternal(userMessage);
    }

    private void sendInputInternal(String userMessage) {
        log(">>> sendInputInternal() called");
        log(">>> Message length: " + userMessage.length());

        if (!running.get()) {
            log("ERROR: Wrapper not running!");
            return;
        }

        String claudeCommand = findClaudeCommand();
        if (claudeCommand == null) {
            log("ERROR: Claude command not found!");
            chatPanel.addMessage("System", "Claude command not found!",
                    VisualCCChatPanel.MessageType.SYSTEM);
            return;
        }

        log(">>> Starting CLI process");

        // Set working state
        chatPanel.setWorkingState(true);

        // Run in a separate thread
        new Thread(() -> {
            try {
                // Build command with stream-json output format
                List<String> cmd = new ArrayList<>();

                if (SystemInfo.isWindows) {
                    cmd.add("cmd");
                    cmd.add("/c");
                    cmd.add(claudeCommand);
                } else {
                    cmd.add(claudeCommand);
                }

                // Add arguments
                cmd.add("-p");
                cmd.add("--system-prompt");
                cmd.add("You are Claude Code, Anthropic's official CLI for programming.");
                cmd.add("--verbose");
                cmd.add("--output-format");
                cmd.add("stream-json");

                // Permission mode: bypassPermissions (auto-approve) or default (ask for approval)
                cmd.add("--permission-mode");
                cmd.add(autoApprovalEnabled ? "bypassPermissions" : "default");

                log(">>> Auto-approval: " + (autoApprovalEnabled ? "ENABLED (bypassPermissions)" : "DISABLED (default)"));

                // Allow multiple turns for interactive conversations
                cmd.add("--max-turns");
                cmd.add("10");

                ProcessBuilder pb = new ProcessBuilder(cmd);

                // Set working directory - use project base path or current directory
                String workingDir = project.getBasePath();
                if (workingDir == null || workingDir.isEmpty()) {
                    // Fallback to current directory if project has no base path
                    workingDir = System.getProperty("user.dir");
                    log(">>> WARNING: Project has no base path, using current directory: " + workingDir);
                }
                pb.directory(new File(workingDir));

                // Set environment
                pb.redirectErrorStream(false);

                log(">>> Executing CLI: " + pb.command());
                log(">>> Working directory: " + workingDir);

                cliProcess = pb.start();
                log(">>> Process started, PID: " + cliProcess.pid());

                // Add user message to conversation history
                JsonObject userMsg = new JsonObject();
                userMsg.addProperty("role", "user");
                userMsg.addProperty("content", userMessage);
                conversationHistory.add(userMsg);
                log(">>> Added user message to history. History size: " + conversationHistory.size());

                // Send entire conversation history to CLI (not just current message)
                String jsonInput = gson.toJson(conversationHistory);
                log(">>> Sending JSON to stdin (" + jsonInput.length() + " chars): " +
                    (jsonInput.length() > 200 ? jsonInput.substring(0, 200) + "..." : jsonInput));

                // Write JSON to stdin and close it (signals EOF)
                // CLI needs EOF to know input is complete and start processing
                try (OutputStream stdin = cliProcess.getOutputStream()) {
                    stdin.write(jsonInput.getBytes(StandardCharsets.UTF_8));
                    stdin.write('\n');
                    stdin.flush();
                }
                log(">>> JSON sent to stdin, stream closed");

                // Start output reading
                startOutputReaders();

                // Wait for process to complete (no timeout)
                log(">>> Waiting for process to complete...");
                int exitCode = cliProcess.waitFor();
                log(">>> Process completed with exit code: " + exitCode);

                // Wait for output threads to finish
                log(">>> Waiting for output threads to finish...");
                if (outputThread != null) {
                    outputThread.join(2000);
                }
                if (errorThread != null) {
                    errorThread.join(2000);
                }
                log(">>> All threads finished");

            } catch (Exception e) {
                log("ERROR: " + e.getMessage());
                e.printStackTrace();
                chatPanel.addMessage("System", "Error: " + e.getMessage(),
                        VisualCCChatPanel.MessageType.SYSTEM);
                chatPanel.setStatus("Error", new Color(200, 100, 100));
            } finally {
                // Always reset working state
                chatPanel.setWorkingState(false);
            }
        }, "VisualCC-session").start();
    }

    private void startOutputReaders() {
        log(">>> Starting output readers");

        outputThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(cliProcess.getInputStream(), StandardCharsets.UTF_8))) {

                String line;
                int lineCount = 0;
                while ((line = reader.readLine()) != null) {
                    lineCount++;
                    log("STDOUT [" + lineCount + "]: " + (line.length() > 200 ? line.substring(0, 200) + "..." : line));
                    processJsonLine(line);
                }
                log("STDOUT: End of stream (total lines: " + lineCount + ")");
            } catch (IOException e) {
                log("ERROR: STDOUT read error: " + e.getMessage());
            }
        }, "VisualCC-stdout");

        errorThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(cliProcess.getErrorStream(), StandardCharsets.UTF_8))) {

                String line;
                while ((line = reader.readLine()) != null) {
                    log("STDERR: " + line);
                    chatPanel.addMessage("System", "[Error] " + line,
                            VisualCCChatPanel.MessageType.SYSTEM);
                }
            } catch (IOException e) {
                log("ERROR: STDERR read error: " + e.getMessage());
            }
        }, "VisualCC-stderr");

        outputThread.start();
        errorThread.start();
    }

    private void processJsonLine(String line) {
        if (line.trim().isEmpty()) return;

        // STOP processing if we're waiting for user answer or approval
        // This prevents processing after we've killed the CLI process
        if (waitingForAnswer) {
            log(">>> NOT processing line - waiting for user to answer questions");
            return;
        }

        // Also stop processing if waiting for approval decision
        if (approvalManager.isWaitingForApproval()) {
            log(">>> NOT processing line - waiting for user approval decision");
            return;
        }

        log(">>> processJsonLine(): " + (line.length() > 150 ? line.substring(0, 150) + "..." : line));

        try {
            JsonElement element = JsonParser.parseString(line);

            if (element.isJsonObject()) {
                JsonObject obj = element.getAsJsonObject();

                String type = obj.has("type") ? obj.get("type").getAsString() : null;
                log(">>> JSON type: " + type);

                if ("text".equals(type)) {
                    // Direct text message
                    String text = obj.get("text").getAsString();
                    log(">>> Text content: " + text);
                    chatPanel.addMessage("Claude", text, VisualCCChatPanel.MessageType.CLAUDE);
                } else if ("user".equals(type)) {
                    // User message (tool_result) - internal, don't display
                    // These are just tool results that Claude uses internally
                    log(">>> Skipping user/tool_result message (internal)");
                } else if ("assistant".equals(type)) {
                    // Assistant message with nested content
                    log(">>> Processing assistant message");
                    if (obj.has("message")) {
                        JsonObject message = obj.getAsJsonObject("message");

                        // Extract token usage info
                        if (message.has("usage")) {
                            JsonObject usage = message.getAsJsonObject("usage");
                            int inputTokens = usage.has("input_tokens") ? usage.get("input_tokens").getAsInt() : 0;
                            int outputTokens = usage.has("output_tokens") ? usage.get("output_tokens").getAsInt() : 0;
                            int cacheTokens = usage.has("cache_read_input_tokens") ? usage.get("cache_read_input_tokens").getAsInt() : 0;

                            log(">>> Token usage - Input: " + inputTokens + ", Output: " + outputTokens + ", Cache: " + cacheTokens);
                            chatPanel.updateTokenInfo(inputTokens, outputTokens, cacheTokens);
                        }

                        if (message.has("content")) {
                            JsonArray content = message.getAsJsonArray("content");
                            StringBuilder fullText = new StringBuilder();

                            for (JsonElement contentEl : content) {
                                if (contentEl.isJsonObject()) {
                                    JsonObject contentObj = contentEl.getAsJsonObject();
                                    if (contentObj.has("type")) {
                                        String contentType = contentObj.get("type").getAsString();

                                        if ("text".equals(contentType)) {
                                            String text = contentObj.get("text").getAsString();
                                            fullText.append(text);
                                            // Reset tool when text appears (tool finished)
                                            chatPanel.setCurrentTool(null);
                                        } else if ("thinking".equals(contentType)) {
                                            String thinking = contentObj.has("thinking") ?
                                                contentObj.get("thinking").getAsString() : "";
                                            if (!thinking.isEmpty()) {
                                                chatPanel.addMessage("Claude", "[Thinking: " + thinking + "]",
                                                    VisualCCChatPanel.MessageType.CLAUDE);
                                            }
                                        } else if ("tool_use".equals(contentType)) {
                                            // Handle tool_use (Read, Write, Bash, AskUserQuestion, etc.)
                                            String toolName = contentObj.has("name") ?
                                                contentObj.get("name").getAsString() : "";
                                            log(">>> Tool use detected: " + toolName);

                                            // Handle AskUserQuestion tool
                                            if ("AskUserQuestion".equals(toolName)) {
                                                log("==========================================");
                                                log("!!! AskUserQuestion DETECTED !!!");
                                                log("==========================================");
                                                handleAskUserQuestion(contentObj);
                                                return;  // Exit processing, wait for user response
                                            }

                                            // Handle approval requests for file operations
                                            // Check if we're already waiting for user to answer questions
                                            if (waitingForAnswer) {
                                                log(">>> NOT processing approval - waiting for user to answer questions");
                                            } else if (approvalManager.shouldRequestApproval(autoApprovalEnabled, toolName)) {
                                                log("==========================================");
                                                log("!!! APPROVAL REQUIRED for: " + toolName + " !!!");
                                                log("==========================================");

                                                // Extract tool_use_id and input
                                                String toolUseId = contentObj.has("id") ?
                                                    contentObj.get("id").getAsString() : "";
                                                JsonObject input = contentObj.has("input") ?
                                                    contentObj.getAsJsonObject("input") : new JsonObject();

                                                log(">>> Processing approval request - ID: " + toolUseId);

                                                // Parse and display approval request
                                                ApprovalRequest request = approvalManager.parseApprovalRequest(
                                                    toolName, toolUseId, input);
                                                approvalManager.requestApproval(request);

                                                // IMPORTANT: Kill the CLI process to wait for user's approval decision
                                                log(">>> Killing CLI process - waiting for approval decision");
                                                if (cliProcess != null && cliProcess.isAlive()) {
                                                    cliProcess.destroyForcibly();
                                                    log(">>> CLI process killed for approval");

                                                    // Interrupt output threads
                                                    if (outputThread != null && outputThread.isAlive()) {
                                                        outputThread.interrupt();
                                                    }
                                                    if (errorThread != null && errorThread.isAlive()) {
                                                        errorThread.interrupt();
                                                    }
                                                }

                                                chatPanel.setWorkingState(false);
                                                return;  // Exit processing, wait for user's approval decision
                                            }

                                            // Update status to show current tool
                                            if (!toolName.isEmpty()) {
                                                chatPanel.setCurrentTool(toolName);
                                            }
                                        }
                                    }
                                }
                            }

                            // Add accumulated text as single message
                            if (fullText.length() > 0) {
                                log(">>> Accumulated text: " + fullText);
                                chatPanel.addMessage("Claude", fullText.toString(), VisualCCChatPanel.MessageType.CLAUDE);

                                // Add assistant response to conversation history
                                JsonObject assistantMsg = new JsonObject();
                                assistantMsg.addProperty("role", "assistant");
                                assistantMsg.addProperty("content", fullText.toString());
                                conversationHistory.add(assistantMsg);
                                log(">>> Added assistant response to history. History size: " + conversationHistory.size());
                            }
                        }
                    }
                } else if ("system".equals(type)) {
                    // System message - log for debugging
                    log(">>> System message: " + line);
                } else if ("error".equals(type)) {
                    // Error message
                    log(">>> Error message: " + line);
                    String errorMsg = obj.has("error") ? obj.get("error").getAsString() : line;
                    chatPanel.addMessage("System", "Error: " + errorMsg, VisualCCChatPanel.MessageType.SYSTEM);
                    chatPanel.setStatus("Error", new Color(200, 80, 80));
                } else if ("result".equals(type)) {
                    // Result/final message with cost and duration info
                    log(">>> Result: " + line);

                    // Check for permission denials
                    if (obj.has("permission_denials")) {
                        handlePermissionDenials(obj.getAsJsonArray("permission_denials"));
                        // Don't show "Done" when there are permission denials
                        return;
                    }

                    // Extract cost and duration info (from Kilo Code types)
                    double totalCost = obj.has("total_cost_usd") ? obj.get("total_cost_usd").getAsDouble() : 0.0;
                    long durationMs = obj.has("duration_ms") ? obj.get("duration_ms").getAsLong() : 0;
                    long durationApiMs = obj.has("duration_api_ms") ? obj.get("duration_api_ms").getAsLong() : 0;
                    int numTurns = obj.has("num_turns") ? obj.get("num_turns").getAsInt() : 0;
                    String resultMsg = obj.has("result") ? obj.get("result").getAsString() : "";

                    log(">>> Cost: $" + String.format("%.4f", totalCost) +
                        ", Duration: " + durationMs + "ms, API: " + durationApiMs + "ms, Turns: " + numTurns);

                    // IMPORTANT: Reset working state when process completes
                    chatPanel.setWorkingState(false);
                    log(">>> Working state reset to false (result received)");

                    // Show completion message with cost info
                    chatPanel.setStatus("Done ($" + String.format("%.4f", totalCost) + ")", new Color(80, 140, 90));
                } else {
                    log(">>> Unknown type: " + type);
                }
            }
        } catch (Exception e) {
            // Silently ignore parsing errors - JSON is parsed correctly above
            log(">>> JSON parse error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void dispose() {
        log("==========================================");
        log("!!! dispose() called !!!");
        log("==========================================");
        running.set(false);

        if (cliProcess != null) {
            cliProcess.destroy();
        }

        if (outputThread != null) {
            outputThread.interrupt();
        }

        if (errorThread != null) {
            errorThread.interrupt();
        }

        // Close log file
        if (logFileWriter != null) {
            try {
                log("Closing log file");
                logFileWriter.close();
            } catch (IOException e) {
                System.err.println("Failed to close log file: " + e.getMessage());
            }
        }
    }

    public boolean isRunning() {
        return cliProcess != null && cliProcess.isAlive();
    }

    /**
     * Check if currently waiting for user answer to a question
     * Following Kline approach - simple boolean flag
     */
    public boolean isWaitingForResponse() {
        return waitingForAnswer;
    }

    /**
     * Set plan mode on/off
     * @param enabled true for plan mode, false for default mode
     */
    public void setPlanMode(boolean enabled) {
        log(">>> Plan mode set to: " + enabled);
        this.planMode = enabled;
    }

    /**
     * Get current plan mode setting
     */
    public boolean isPlanMode() {
        return planMode;
    }

    /**
     * Set auto-approval on/off
     * @param enabled true for bypassPermissions (auto-approve), false for default (ask for approval)
     */
    public void setAutoApproval(boolean enabled) {
        log(">>> Auto-approval set to: " + enabled);
        this.autoApprovalEnabled = enabled;
    }

    /**
     * Get current auto-approval setting
     */
    public boolean isAutoApproval() {
        return autoApprovalEnabled;
    }

    /**
     * Clear conversation history for new conversation
     */
    public void clearHistory() {
        log("==========================================");
        log(">>> Clearing conversation history");
        log(">>> Previous history size: " + conversationHistory.size());
        conversationHistory = new JsonArray();
        log(">>> History cleared. New size: " + conversationHistory.size());
        log("==========================================");
    }

    /**
     * Start waiting for user answer
     * Called when we detect a question in the agent's response
     */
    public void startWaitingForAnswer(String currentMessage) {
        log("==========================================");
        log("!!! startWaitingForAnswer() called !!!");
        log("==========================================");
        log(">>> waitingForAnswer = " + waitingForAnswer);
        log(">>> Message to accumulate: " + currentMessage);
        waitingForAnswer = true;
        accumulatedUserMessage.append(currentMessage);
        log(">>> accumulatedUserMessage is now: " + accumulatedUserMessage.toString());
        chatPanel.setWaitingForAnswer(true);
        log(">>> UI updated to waiting state");
    }

    /**
     * Handle AskUserQuestion tool from Claude
     * Extracts questions and triggers waiting state for user response
     */
    private void handleAskUserQuestion(JsonObject toolUseObj) {
        log("==========================================");
        log("!!! handleAskUserQuestion() START !!!");
        log("==========================================");
        log(">>> toolUseObj: " + toolUseObj);

        try {
            // Extract the input/payload from the tool_use
            JsonObject input = null;
            if (toolUseObj.has("input")) {
                input = toolUseObj.getAsJsonObject("input");
                log(">>> input found: " + input);
            } else {
                log("WARNING: No 'input' field in toolUseObj!");
            }

            if (input != null) {
                // Extract questions array
                JsonArray questions = null;
                if (input.has("questions")) {
                    JsonElement questionsEl = input.get("questions");
                    if (questionsEl.isJsonArray()) {
                        questions = questionsEl.getAsJsonArray();
                        log(">>> questions array found with " + questions.size() + " elements");
                    } else {
                        log("WARNING: 'questions' field is not an array: " + questionsEl);
                    }
                } else {
                    log("WARNING: No 'questions' field in input!");
                }

                // Build questions text and extract options
                StringBuilder questionsText = new StringBuilder();
                java.util.List<String> allOptions = new java.util.ArrayList<>();

                if (questions != null && questions.size() > 0) {
                    for (int i = 0; i < questions.size(); i++) {
                        JsonElement questionEl = questions.get(i);
                        // Each question is an object with a "question" field
                        if (questionEl.isJsonObject()) {
                            JsonObject questionObj = questionEl.getAsJsonObject();
                            String questionText = questionObj.has("question") ?
                                questionObj.get("question").getAsString() : "(No question text)";

                            questionsText.append(i + 1).append(". ");
                            questionsText.append(questionText);
                            questionsText.append("\n");
                            log(">>> Question " + (i+1) + ": " + questionText);

                            // Extract options if available
                            if (questionObj.has("options")) {
                                JsonArray options = questionObj.get("options").getAsJsonArray();
                                log(">>>    Options available: " + options.size());
                                for (JsonElement optionEl : options) {
                                    // Options are objects with "label" and "description"
                                    if (optionEl.isJsonObject()) {
                                        JsonObject optionObj = optionEl.getAsJsonObject();
                                        String label = optionObj.has("label") ?
                                            optionObj.get("label").getAsString() : optionObj.toString();
                                        allOptions.add(label);
                                        log(">>>      Option: " + label);
                                    } else {
                                        // Fallback for string options
                                        String option = optionEl.getAsString();
                                        allOptions.add(option);
                                        log(">>>      Option: " + option);
                                    }
                                }
                            }
                        } else {
                            // Fallback if it's just a string
                            questionsText.append(i + 1).append(". ");
                            questionsText.append(questionEl.getAsString());
                            questionsText.append("\n");
                        }
                    }
                } else {
                    // Fallback: if no questions array, look for a single question text
                    if (input.has("question")) {
                        questionsText.append(input.get("question").getAsString());
                        log(">>> Single question found: " + input.get("question").getAsString());
                    } else {
                        questionsText.append("(Nessuna domanda specificata)");
                        log("WARNING: No questions found in tool_use!");
                    }
                }

                // Display the questions with or without clickable options
                String qText = questionsText.toString();
                log(">>> Questions text: " + qText);
                log(">>> Total options extracted: " + allOptions.size());

                if (!allOptions.isEmpty()) {
                    // Use new clickable component with options
                    log(">>> Creating question component with clickable options");
                    VisualCCQuestionComponent questionComponent = new VisualCCQuestionComponent(
                        qText, allOptions, selectedOption -> {
                            // When user clicks an option, automatically send it as their answer
                            log("==========================================");
                            log(">>> User clicked option: " + selectedOption);
                            log("==========================================");

                            // Add the user's answer to conversation history
                            JsonObject answerMsg = new JsonObject();
                            answerMsg.addProperty("role", "user");
                            answerMsg.addProperty("content", selectedOption);
                            conversationHistory.add(answerMsg);
                            log(">>> Added user's answer to history: " + selectedOption);

                            // Restart CLI with the user's answer
                            log(">>> Restarting CLI with user's answer");
                            sendInputInternal("");
                        });
                    chatPanel.addQuestionComponent(questionComponent);
                } else {
                    // Fallback to simple message display for questions without options
                    log(">>> No options found, using simple message display");
                    chatPanel.addMessage("Claude", qText, VisualCCChatPanel.MessageType.QUESTION);
                    startWaitingForAnswer("[User answered Claude's questions]");
                }

                // Set working state to false since we're waiting
                chatPanel.setWorkingState(false);
                log(">>> Working state set to false");

                // IMPORTANT: Kill the CLI process now because:
                // 1. stdin is already closed
                // 2. We can't send a response while process is running
                // 3. User will answer, then we'll start a new CLI session
                log(">>> Killing CLI process - waiting for user to answer questions");
                if (cliProcess != null && cliProcess.isAlive()) {
                    cliProcess.destroyForcibly();
                    log(">>> CLI process killed");

                    // Also interrupt the output threads so they stop waiting for input
                    if (outputThread != null && outputThread.isAlive()) {
                        outputThread.interrupt();
                        log(">>> Output thread interrupted");
                    }
                    if (errorThread != null && errorThread.isAlive()) {
                        errorThread.interrupt();
                        log(">>> Error thread interrupted");
                    }
                }
            } else {
                log("ERROR: input is null, cannot process AskUserQuestion");
            }

        } catch (Exception e) {
            log("ERROR in handleAskUserQuestion: " + e.getMessage());
            e.printStackTrace();

            // Even if there's an error, we need to kill the process to prevent it from continuing
            log(">>> Killing CLI process due to error in AskUserQuestion handling");
            if (cliProcess != null && cliProcess.isAlive()) {
                cliProcess.destroyForcibly();
                log(">>> CLI process killed (error recovery)");

                if (outputThread != null && outputThread.isAlive()) {
                    outputThread.interrupt();
                }
                if (errorThread != null && errorThread.isAlive()) {
                    errorThread.interrupt();
                }
            }
        }

        log("==========================================");
        log("!!! handleAskUserQuestion() END !!!");
        log("==========================================");
    }

    /**
     * Handle permission denials from CLI result.
     * This happens when --permission-mode is default and the CLI denies a tool use.
     * We need to show our approval UI and let the user decide.
     */
    private void handlePermissionDenials(com.google.gson.JsonArray denials) {
        log("==========================================");
        log("!!! handlePermissionDenials() START !!!");
        log(">>> Permission denials count: " + denials.size());
        log("==========================================");

        try {
            for (com.google.gson.JsonElement denialEl : denials) {
                com.google.gson.JsonObject denial = denialEl.getAsJsonObject();

                String toolName = denial.has("tool_name") ? denial.get("tool_name").getAsString() : "Unknown";
                String toolUseId = denial.has("tool_use_id") ? denial.get("tool_use_id").getAsString() : "";
                com.google.gson.JsonObject toolInput = denial.has("tool_input") ? denial.getAsJsonObject("tool_input") : new com.google.gson.JsonObject();

                log(">>> Permission denied for tool: " + toolName + ", ID: " + toolUseId);

                // Check if this tool is already approved (session or persistent)
                if (approvalPreferences.isToolApproved(toolName)) {
                    log(">>> Tool already approved, skipping UI: " + toolName);
                    // We still need to let the user know to continue
                    continue;
                }

                // Check if we should request approval
                if (!approvalManager.shouldRequestApproval(autoApprovalEnabled, toolName)) {
                    log(">>> Auto-approval enabled or tool doesn't require approval, skipping: " + toolName);
                    continue;
                }

                // Parse the approval request
                ApprovalRequest request = approvalManager.parseApprovalRequest(toolName, toolUseId, toolInput);
                log(">>> Parsed approval request: " + request);

                // Request approval through the manager
                approvalManager.requestApproval(request);

                // Only show the first approval request for now
                // TODO: Handle multiple permission denials
                break;
            }

        } catch (Exception e) {
            log("ERROR in handlePermissionDenials: " + e.getMessage());
            e.printStackTrace();
        }

        log("==========================================");
        log("!!! handlePermissionDenials() END !!!");
        log("==========================================");
    }

    /**
     * Called when user makes an approval decision (Approve or Deny).
     * This is called from VisualCCApprovalManager.
     */
    public void onApprovalDecision(ApprovalRequest request, boolean approved) {
        log("==========================================");
        log("!!! onApprovalDecision() START !!!");
        log(">>> Request: " + request);
        log(">>> Approved: " + approved);
        log("==========================================");

        try {
            approvalManager.clearPending();

            if (approved) {
                // User approved - we need to continue execution
                log(">>> User APPROVED - continuing execution");

                // Check if process is still running
                if (cliProcess != null && cliProcess.isAlive()) {
                    // Process is still running (e.g., we interrupted it for approval)
                    // Add approval confirmation to conversation and restart
                    log(">>> Process is still running, restarting with approval");

                    StringBuilder approvalContent = new StringBuilder();
                    approvalContent.append("Please proceed with the approved operation:\n");
                    approvalContent.append("- Tool: ").append(request.getToolName());
                    if (request.getFilePath() != null) {
                        approvalContent.append("\n- File: ").append(request.getFilePath());
                    }
                    approvalContent.append("\n\nThe operation was approved. Please continue executing.");

                    JsonObject approvalMsg = new JsonObject();
                    approvalMsg.addProperty("role", "user");
                    approvalMsg.addProperty("content", approvalContent.toString());
                    conversationHistory.add(approvalMsg);
                    log(">>> Added approval confirmation to history: " + approvalContent);

                    // Restart CLI with updated history
                    log(">>> Restarting CLI to continue with approved operation");
                    sendInputInternal("");
                } else {
                    // Process has already finished (e.g., permission denial from result)
                    // Tell user to press Enter to continue
                    log(">>> Process has finished, asking user to continue");

                    // Show message to user
                    chatPanel.addMessage("System",
                            "✓ Operazione approvata! **Premi Invio per continuare**\n\n" +
                                    "(Il tool \"" + request.getToolName() + "\" è stato approvato. " +
                                    "Al prossimo messaggio, Claude procederà con l'operazione.)",
                            VisualCCChatPanel.MessageType.SYSTEM);

                    chatPanel.setPlaceholderText("Premi Invio per continuare...");
                    chatPanel.setStatus("Approved. Press Enter to continue...", new java.awt.Color(80, 140, 90));
                }

            } else {
                // User denied - pause and ask for instructions
                log(">>> User DENIED - pausing and asking for instructions");

                // Set waiting for answer state
                waitingForAnswer = true;
                accumulatedUserMessage.setLength(0);
                accumulatedUserMessage.append("[Operation denied: ");
                accumulatedUserMessage.append(request.getToolName());
                if (request.getFilePath() != null) {
                    accumulatedUserMessage.append(" on ").append(request.getFilePath());
                }
                accumulatedUserMessage.append(". User will provide new instructions.]");
                log(">>> Waiting for user instructions");

                chatPanel.setPlaceholderText("Provide instructions on how to proceed...");
                chatPanel.setStatus("Operation denied. Waiting for instructions...", new java.awt.Color(170, 80, 80));
            }

        } catch (Exception e) {
            log("ERROR in onApprovalDecision: " + e.getMessage());
            e.printStackTrace();
        }

        log("==========================================");
        log("!!! onApprovalDecision() END !!!");
        log("==========================================");
    }
}
