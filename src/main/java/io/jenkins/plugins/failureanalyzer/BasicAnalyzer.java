package io.jenkins.plugins.failureanalyzer;

public class BasicAnalyzer {
    public static String analyze(AnalysisResult result) {
        StringBuilder analysis = new StringBuilder();
        analysis.append("### Basic Failure Analysis\n\n");
        analysis.append("**Job Name**: ").append(result.getJobName()).append("\n");
        analysis.append("**Build Number**: ").append(result.getBuildNumber()).append("\n");
        analysis.append("**Failed Stage**: ").append(result.getFailedStageName()).append("\n");
        if (result.getErrorMessage() != null) {
            analysis.append("**Error Message**: `").append(result.getErrorMessage()).append("`\n\n");
        }
        
        analysis.append("This is a basic heuristic analysis because the AI analyzer was unavailable. ");
        analysis.append("The build failed at stage **").append(result.getFailedStageName()).append("**. ");
        analysis.append("Please review the logs for that step to identify the root cause.\n");
        
        return analysis.toString();
    }
}
