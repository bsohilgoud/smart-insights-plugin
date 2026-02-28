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
        String apiKey = System.getenv("OPENAI_API_KEY");
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
            promptBuilder.append("You are a CI/CD failure analysis expert for Jenkins pipelines.\n\n");
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
            }

            promptBuilder.append("\n**Stage Details (Logs):**\n");
            for (AnalysisResult.StageInfo stage : result.getStages()) {
                if (stage.isFailed() && stage.getStageLog() != null && !stage.getStageLog().isEmpty()) {
                    promptBuilder.append(stage.getStageLog()).append("\n");
                }
            }

            promptBuilder.append("\nAnalyze the failure and respond in valid JSON matching exactly this schema without any markdown formatting wrappers:\n");
            promptBuilder.append("{\n");
            promptBuilder.append("  \"summary\": \"Brief 1-sentence summary of what failed\",\n");
            promptBuilder.append("  \"root_cause\": \"Detailed root cause analysis considering the execution flow\",\n");
            promptBuilder.append("  \"suggested_fixes\": [\"Actionable fix 1\", \"Actionable fix 2\"],\n");
            promptBuilder.append("  \"flaky\": true\n");
            promptBuilder.append("}\n\n");
            
            promptBuilder.append("Focus on:\n");
            promptBuilder.append("1. Whether the failure is isolated to the failed stage or cascaded from earlier stages\n");
            promptBuilder.append("2. Patterns in the execution flow that contributed to the failure\n");
            promptBuilder.append("3. Specific actionable fixes (not generic advice)\n");

            LOGGER.info("Sending request to LLM using LangChain4j...");
            return model.generate(promptBuilder.toString());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error calling OpenAI API. Falling back to basic analysis.", e);
            return "AI Analysis failed: " + e.getMessage() + "\n\n" + BasicAnalyzer.analyze(result);
        }
    }
}
