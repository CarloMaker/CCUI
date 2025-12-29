package com.cc.visualcc;

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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Wrapper for Claude Code CLI process.
 * Uses JSON stdin input with stream-json output format (like Kilo Code).
 */
public class VisualCCCliWrapper {

    private final Project project;
    private final VisualCCChatPanel chatPanel;
    private Process cliProcess;
    private Thread outputThread;
    private Thread errorThread;
    private final AtomicBoolean running = new AtomicBoolean(false);

    // User question response handling - following Kline approach
    private boolean waitingForAnswer = false;
    private StringBuilder accumulatedUserMessage = new StringBuilder();  // Accumulate questions + answers

    // JSON parsing
    private final Gson gson = new Gson();

    public VisualCCCliWrapper(Project project, VisualCCChatPanel chatPanel) {
        this.project = project;
        this.chatPanel = chatPanel;
    }

    public void start() {
        System.out.println("[VisualCC CLI Wrapper] start() called");
        String claudeCommand = findClaudeCommand();
        System.out.println("[VisualCC CLI Wrapper] Claude command: " + claudeCommand);
        if (claudeCommand == null) {
            System.out.println("[VisualCC CLI Wrapper] Claude command not found!");
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
        System.out.println("[VisualCC CLI Wrapper] sendInput() called with: " + userMessage);

        // Kline approach: if waiting for answer, accumulate the response
        if (waitingForAnswer) {
            System.out.println("[VisualCC CLI Wrapper] Accumulating answer to question");
            accumulatedUserMessage.append("\n\n[My answer: ").append(userMessage).append("]");
            waitingForAnswer = false;
            chatPanel.setWaitingForAnswer(false);
            // Now send the accumulated message (original + answer)
            sendInputInternal(accumulatedUserMessage.toString());
            accumulatedUserMessage.setLength(0); // Clear for next time
            return;
        }

        // Normal message - start fresh
        sendInputInternal(userMessage);
    }

    private void sendInputInternal(String userMessage) {
        System.out.println("[VisualCC CLI Wrapper] sendInputInternal() called with: " + userMessage);

        if (!running.get()) {
            System.out.println("[VisualCC CLI Wrapper] Wrapper not running!");
            return;
        }

        String claudeCommand = findClaudeCommand();
        if (claudeCommand == null) {
            chatPanel.addMessage("System", "Claude command not found!",
                    VisualCCChatPanel.MessageType.SYSTEM);
            return;
        }

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

                // Allow multiple turns for interactive conversations
                cmd.add("--max-turns");
                cmd.add("10");

                ProcessBuilder pb = new ProcessBuilder(cmd);
                pb.directory(new File(project.getBasePath()));

                // Set environment
                pb.redirectErrorStream(false);

                System.out.println("[VisualCC CLI Wrapper] Executing: " + pb.command());

                cliProcess = pb.start();

                // Prepare JSON input for stdin
                JsonObject message = new JsonObject();
                message.addProperty("role", "user");
                message.addProperty("content", userMessage);

                JsonArray messages = new JsonArray();
                messages.add(message);

                String jsonInput = gson.toJson(messages);
                System.out.println("[VisualCC CLI Wrapper] Sending JSON to stdin: " + jsonInput);

                // Write JSON to stdin and close it (signals EOF)
                try (OutputStream stdin = cliProcess.getOutputStream()) {
                    stdin.write(jsonInput.getBytes(StandardCharsets.UTF_8));
                    stdin.write('\n');
                    stdin.flush();
                }

                // Start output reading
                startOutputReaders();

                // Wait for process to complete (with timeout)
                boolean finished = cliProcess.waitFor(120, TimeUnit.SECONDS);

                if (!finished) {
                    System.out.println("[VisualCC CLI Wrapper] Process timeout, killing");
                    cliProcess.destroyForcibly();
                    chatPanel.addMessage("System", "Process timeout after 120s",
                            VisualCCChatPanel.MessageType.SYSTEM);
                    chatPanel.setStatus("Timeout", new Color(200, 100, 100));
                } else {
                    int exitCode = cliProcess.exitValue();
                    System.out.println("[VisualCC CLI Wrapper] Process exit code: " + exitCode);
                }

                // Wait for output threads to finish
                if (outputThread != null) {
                    outputThread.join(2000);
                }
                if (errorThread != null) {
                    errorThread.join(2000);
                }

            } catch (Exception e) {
                System.out.println("[VisualCC CLI Wrapper] Error: " + e.getMessage());
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
        System.out.println("[VisualCC CLI Wrapper] Starting output readers");

        outputThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(cliProcess.getInputStream(), StandardCharsets.UTF_8))) {

                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("[VisualCC CLI Wrapper] STDOUT: " + line);
                    processJsonLine(line);
                }
            } catch (IOException e) {
                System.out.println("[VisualCC CLI Wrapper] STDOUT read error: " + e.getMessage());
            }
        }, "VisualCC-stdout");

        errorThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(cliProcess.getErrorStream(), StandardCharsets.UTF_8))) {

                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("[VisualCC CLI Wrapper] STDERR: " + line);
                    chatPanel.addMessage("System", "[Error] " + line,
                            VisualCCChatPanel.MessageType.SYSTEM);
                }
            } catch (IOException e) {
                System.out.println("[VisualCC CLI Wrapper] STDERR read error: " + e.getMessage());
            }
        }, "VisualCC-stderr");

        outputThread.start();
        errorThread.start();
    }

    private void processJsonLine(String line) {
        if (line.trim().isEmpty()) return;

        System.out.println("[VisualCC CLI Wrapper] Processing JSON line: " + line);

        try {
            JsonElement element = JsonParser.parseString(line);

            if (element.isJsonObject()) {
                JsonObject obj = element.getAsJsonObject();

                String type = obj.has("type") ? obj.get("type").getAsString() : null;
                System.out.println("[VisualCC CLI Wrapper] JSON type: " + type);

                if ("text".equals(type)) {
                    // Direct text message
                    String text = obj.get("text").getAsString();
                    System.out.println("[VisualCC CLI Wrapper] Text content: " + text);
                    chatPanel.addMessage("Claude", text, VisualCCChatPanel.MessageType.CLAUDE);
                } else if ("user".equals(type)) {
                    // User message (tool_result) - internal, don't display
                    // These are just tool results that Claude uses internally
                    System.out.println("[VisualCC CLI Wrapper] Skipping user/tool_result message (internal)");
                } else if ("assistant".equals(type)) {
                    // Assistant message with nested content
                    System.out.println("[VisualCC CLI Wrapper] Processing assistant message");
                    if (obj.has("message")) {
                        JsonObject message = obj.getAsJsonObject("message");

                        // Extract token usage info
                        if (message.has("usage")) {
                            JsonObject usage = message.getAsJsonObject("usage");
                            int inputTokens = usage.has("input_tokens") ? usage.get("input_tokens").getAsInt() : 0;
                            int outputTokens = usage.has("output_tokens") ? usage.get("output_tokens").getAsInt() : 0;
                            int cacheTokens = usage.has("cache_read_input_tokens") ? usage.get("cache_read_input_tokens").getAsInt() : 0;

                            System.out.println("[VisualCC CLI Wrapper] Token usage - Input: " + inputTokens +
                                ", Output: " + outputTokens + ", Cache: " + cacheTokens);
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
                                            System.out.println("[VisualCC CLI Wrapper] Tool use: " + toolName);

                                            // Handle AskUserQuestion tool
                                            if ("AskUserQuestion".equals(toolName)) {
                                                System.out.println("[VisualCC CLI Wrapper] AskUserQuestion detected!");
                                                handleAskUserQuestion(contentObj);
                                                return;  // Exit processing, wait for user response
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
                                System.out.println("[VisualCC CLI Wrapper] Accumulated text: " + fullText);
                                chatPanel.addMessage("Claude", fullText.toString(), VisualCCChatPanel.MessageType.CLAUDE);
                            }
                        }
                    }
                } else if ("system".equals(type)) {
                    // System message - log for debugging
                    System.out.println("[VisualCC CLI Wrapper] System message: " + line);
                } else if ("error".equals(type)) {
                    // Error message
                    System.out.println("[VisualCC CLI Wrapper] Error message: " + line);
                    String errorMsg = obj.has("error") ? obj.get("error").getAsString() : line;
                    chatPanel.addMessage("System", "Error: " + errorMsg, VisualCCChatPanel.MessageType.SYSTEM);
                    chatPanel.setStatus("Error", new Color(200, 80, 80));
                } else if ("result".equals(type)) {
                    // Result/final message with cost and duration info
                    System.out.println("[VisualCC CLI Wrapper] Result: " + line);

                    // Extract cost and duration info (from Kilo Code types)
                    double totalCost = obj.has("total_cost_usd") ? obj.get("total_cost_usd").getAsDouble() : 0.0;
                    long durationMs = obj.has("duration_ms") ? obj.get("duration_ms").getAsLong() : 0;
                    long durationApiMs = obj.has("duration_api_ms") ? obj.get("duration_api_ms").getAsLong() : 0;
                    int numTurns = obj.has("num_turns") ? obj.get("num_turns").getAsInt() : 0;
                    String resultMsg = obj.has("result") ? obj.get("result").getAsString() : "";

                    System.out.println("[VisualCC CLI Wrapper] Cost: $" + String.format("%.4f", totalCost) +
                        ", Duration: " + durationMs + "ms, API: " + durationApiMs + "ms, Turns: " + numTurns);

                    // Show completion message with cost info
                    chatPanel.setStatus("Done ($" + String.format("%.4f", totalCost) + ")", new Color(80, 140, 90));
                } else {
                    System.out.println("[VisualCC CLI Wrapper] Unknown type: " + type);
                }
            }
        } catch (Exception e) {
            // Silently ignore parsing errors - JSON is parsed correctly above
            System.out.println("[VisualCC CLI Wrapper] JSON parse error: " + e.getMessage());
        }
    }

    public void dispose() {
        System.out.println("[VisualCC CLI Wrapper] dispose() called");
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
     * Start waiting for user answer
     * Called when we detect a question in the agent's response
     */
    public void startWaitingForAnswer(String currentMessage) {
        System.out.println("[VisualCC CLI Wrapper] Starting to wait for user answer");
        waitingForAnswer = true;
        accumulatedUserMessage.append(currentMessage);
        chatPanel.setWaitingForAnswer(true);
    }

    /**
     * Handle AskUserQuestion tool from Claude
     * Extracts questions and triggers waiting state for user response
     */
    private void handleAskUserQuestion(JsonObject toolUseObj) {
        try {
            // Extract the input/payload from the tool_use
            JsonObject input = null;
            if (toolUseObj.has("input")) {
                input = toolUseObj.getAsJsonObject("input");
            }

            if (input != null) {
                // Extract questions array
                JsonArray questions = null;
                if (input.has("questions")) {
                    JsonElement questionsEl = input.get("questions");
                    if (questionsEl.isJsonArray()) {
                        questions = questionsEl.getAsJsonArray();
                    }
                }

                StringBuilder questionsText = new StringBuilder();
                questionsText.append("[Claude ha delle domande]\n\n");

                if (questions != null && questions.size() > 0) {
                    for (int i = 0; i < questions.size(); i++) {
                        questionsText.append(i + 1).append(". ");
                        questionsText.append(questions.get(i).getAsString());
                        questionsText.append("\n");
                    }
                } else {
                    // Fallback: if no questions array, look for a single question text
                    if (input.has("question")) {
                        questionsText.append(input.get("question").getAsString());
                    } else {
                        questionsText.append("(Nessuna domanda specificata)");
                    }
                }

                // Display the questions
                String qText = questionsText.toString();
                System.out.println("[VisualCC CLI Wrapper] Questions: " + qText);
                chatPanel.addMessage("Claude", qText, VisualCCChatPanel.MessageType.QUESTION);

                // Store the current context and start waiting for answer
                // We'll accumulate the user's response when it comes
                startWaitingForAnswer("[User answered Claude's questions]");

                // Set working state to false since we're waiting
                chatPanel.setWorkingState(false);
            }

        } catch (Exception e) {
            System.out.println("[VisualCC CLI Wrapper] Error handling AskUserQuestion: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
