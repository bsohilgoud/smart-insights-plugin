package io.jenkins.plugins.failureanalyzer;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

import java.util.logging.Level;
import java.util.logging.Logger;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

public class LLMAnalyzer {
    private static final Logger LOGGER = Logger.getLogger(LLMAnalyzer.class.getName());

    private static String getApiKey() {
        String apiKey = System.getProperty("OPENAI_API_KEY");
        apiKey = "API_KEY_Place_here";
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            return apiKey.trim();
        }
        apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            return apiKey.trim();
        }
        try {
            File envFile = new File(".env");
            if (envFile.exists()) {
                List<String> lines = Files.readAllLines(envFile.toPath());
                for (String line : lines) {
                    if (line.trim().startsWith("OPENAI_API_KEY=")) {
                        String key = line.split("=", 2)[1].trim();
                        // Removing any surrounding quotes if present
                        if (key.startsWith("\"") && key.endsWith("\"")) key = key.substring(1, key.length() - 1);
                        if (key.startsWith("'") && key.endsWith("'")) key = key.substring(1, key.length() - 1);
                        return key;
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error reading .env file", e);
        }
        return null;
    }

    public static String analyze(AnalysisResult result) {
        String apiKey = getApiKey();
        if (apiKey == null || apiKey.trim().isEmpty()) {
            LOGGER.warning("OPENAI_API_KEY is not set. Falling back to basic analysis.");
            return BasicAnalyzer.analyze(result);
        }

        try {
            ChatLanguageModel model = OpenAiChatModel.builder()
                    .apiKey(apiKey)
                    .modelName("gpt-4o-mini") // Fast and cost-effective model
                    .maxRetries(1)
                    .build();

            StringBuilder promptBuilder = new StringBuilder();
            promptBuilder.append("You are a strict, highly precise CI/CD failure analysis expert for Jenkins pipelines.\n\n");
            promptBuilder.append("**Job:** ").append(result.getJobName()).append("\n");
            promptBuilder.append("**Build:** #").append(result.getBuildNumber()).append("\n");
            
            if (result.getFailedStageName() != null) {
                promptBuilder.append("**Failed Stage:** ").append(result.getFailedStageName()).append("\n");
            }
            if (result.getErrorMessage() != null) {
                promptBuilder.append("**Error Message:** ").append(result.getErrorMessage()).append("\n");
            }
            
            promptBuilder.append("\n**Pipeline Execution Flow:**\n");
            for (AnalysisResult.StageInfo stage : result.getStages()) {
                promptBuilder.append("- ").append(stage.getStageName()).append(": ")
                        .append(stage.isFailed() ? "FAILED" : "PASSED").append("\n");
                
                // Append checkout info if present on passed stages
                if (stage.getStageLog() != null && !stage.getStageLog().isEmpty() && !stage.isFailed()) {
                    promptBuilder.append("  ").append(stage.getStageLog().replace("\n", "\n  ")).append("\n");
                }
            }

            promptBuilder.append("\n**Failed Stage Logs (Context-Filtered):**\n");
            for (AnalysisResult.StageInfo stage : result.getStages()) {
                if (stage.isFailed() && stage.getStageLog() != null && !stage.getStageLog().isEmpty()) {
                    promptBuilder.append(stage.getStageLog()).append("\n");
                }
            }

            promptBuilder.append("\n**INSTRUCTIONS:**\n");
            
            // --- Contextual Prompt Routing Based on Job Name ---
            String jobName = result.getJobName() != null ? result.getJobName().toLowerCase() : "";
            if (jobName.contains("web") || jobName.contains("responsive") || jobName.contains("fabric")) {
                promptBuilder.append("0. **Context Focus (Web/Frontend):** Pay special attention to missing custom widget ZIPs, third-party library resolution, npm/yarn outputs, and Fabric publish stages.\n");
            } else if (jobName.contains("android") || jobName.contains("ios") || jobName.contains("mobile")) {
                promptBuilder.append("0. **Context Focus (Mobile):** Pay special attention to provisioning profiles, Gradle memory limits, CocoaPods/SDK version mismatches, and build tools.\n");
            } else if (jobName.contains("backend") || jobName.contains("api") || jobName.contains("service")) {
                promptBuilder.append("0. **Context Focus (Backend):** Pay special attention to database connection issues, missing environment variables, failing unit tests, and dependency alignment.\n");
            }

            promptBuilder.append("Analyze the failure and respond in cleanly formatted Markdown.\n");
            promptBuilder.append("1. **Do not hallucinate.** Only state facts present in the logs.\n");
            promptBuilder.append("2. **Be extremely precise and concise.** Do not provide basic, generic suggestions (e.g., 'check your syntax' or 'verify credentials' unless the log explicitly shows an auth failure).\n");
            promptBuilder.append("3. If you cannot identify the exact root cause from the provided log snippets, clearly state: \"I could not identify the actual issue from the provided logs.\"\n");
            promptBuilder.append("4. Use the following sections:\n");
            promptBuilder.append("\n### Summary\n");
            promptBuilder.append("A brief 1-sentence summary of what exactly failed.\n");
            promptBuilder.append("\n### Root Cause\n");
            promptBuilder.append("The exact technical reason for the failure. If none is found, admit it clearly.\n");
            promptBuilder.append("\n### Suggested Fixes\n");
            promptBuilder.append("A bulleted list of specific, precise, and highly actionable fixes. Omit this section entirely if you do not have highly accurate suggestions.\n");

            String finalPrompt = promptBuilder.toString();
            LOGGER.info("------ FINAL AI PROMPT CONTEXT ------\n" + finalPrompt + "\n-------------------------------------");

            return model.generate(finalPrompt);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error calling OpenAI API. Falling back to basic analysis.", e);
            return "AI Analysis failed: " + e.getMessage() + "\n\n" + BasicAnalyzer.analyze(result);
        }
    }
}
