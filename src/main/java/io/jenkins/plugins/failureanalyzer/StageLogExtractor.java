package io.jenkins.plugins.failureanalyzer;

import hudson.model.Run;
import org.jenkinsci.plugins.workflow.actions.ErrorAction;
import org.jenkinsci.plugins.workflow.actions.LogAction;
import org.jenkinsci.plugins.workflow.actions.StageAction;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graph.FlowGraphWalker;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class StageLogExtractor {

    public static AnalysisResult extractFailureData(Run<?, ?> run) {
        AnalysisResult result = new AnalysisResult();
        result.setJobName(run.getParent().getFullName());
        result.setBuildNumber(run.getNumber());
        result.setFullLog(getFullLog(run));

        if (run instanceof WorkflowRun) {
            WorkflowRun workflowRun = (WorkflowRun) run;
            extractPipelineData(workflowRun, result);
        }

        return result;
    }

    private static void extractPipelineData(WorkflowRun run, AnalysisResult result) {
        FlowExecution execution = run.getExecution();
        if (execution == null) {
            System.out.println("[DEBUG] FlowExecution is null");
            return;
        }

        System.out.println("[DEBUG] Starting pipeline data extraction");
        
        // Use FlowGraphWalker to get ALL nodes
        List<FlowNode> allNodes = new ArrayList<>();
        try {
            FlowGraphWalker walker = new FlowGraphWalker(execution);
            for (FlowNode node : walker) {
                allNodes.add(node);
            }
        } catch (Exception e) {
            System.out.println("[DEBUG] Error walking flow graph: " + e.getMessage());
            result.setErrorMessage("Error walking flow graph: " + e.getMessage());
            return;
        }
        
        System.out.println("[DEBUG] Total nodes collected: " + allNodes.size());

        // Find all stage nodes
        List<FlowNode> stageNodes = new ArrayList<>();
        for (FlowNode node : allNodes) {
            StageAction stageAction = node.getAction(StageAction.class);
            if (stageAction != null) {
                stageNodes.add(node);
                System.out.println("[DEBUG] Found stage: " + stageAction.getStageName() + " at node " + node.getId());
            }
        }

        // Find error node
        FlowNode errorNode = null;
        for (FlowNode node : allNodes) {
            ErrorAction errorAction = node.getError();
            if (errorAction != null) {
                errorNode = node;
                result.setErrorMessage(errorAction.getError().getMessage());
                System.out.println("[DEBUG] Found error at node " + node.getId() + ": " + errorAction.getError().getMessage());
                break;
            }
        }

        // Extract logs for each stage
        for (FlowNode stageNode : stageNodes) {
            StageAction stageAction = stageNode.getPersistentAction(StageAction.class);
            String stageName = stageAction.getStageName();
            String stageLog = extractNodeLog(stageNode, run);
            
            boolean isFailed = false;
            
            // Check if this stage contains the error
            if (errorNode != null) {
                FlowNode current = errorNode;
                while (current != null) {
                    if (current.equals(stageNode)) {
                        isFailed = true;
                        result.setFailedStageName(stageName);
                        System.out.println("[DEBUG] Stage '" + stageName + "' is the failed stage");
                        break;
                    }
                    List<FlowNode> parents = current.getParents();
                    current = parents.isEmpty() ? null : parents.get(0);
                }
            }
            
            result.addStage(stageName, stageLog, isFailed);
            System.out.println("[DEBUG] Added stage '" + stageName + "' with log length: " + stageLog.length() + ", failed: " + isFailed);
        }
        
        System.out.println("[DEBUG] Total stages extracted: " + result.getStages().size());
    }

    private static String extractNodeLog(FlowNode node, WorkflowRun run) {
        LogAction logAction = node.getAction(LogAction.class);
        if (logAction != null) {
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                logAction.getLogText().writeRawLogTo(0, baos);
                String log = baos.toString(StandardCharsets.UTF_8);
                System.out.println("[DEBUG] Extracted " + log.length() + " chars from LogAction for node " + node.getId());
                return log;
            } catch (IOException e) {
                System.out.println("[DEBUG] Error extracting stage log: " + e.getMessage());
                return "Error extracting stage log: " + e.getMessage();
            }
        }
        System.out.println("[DEBUG] No LogAction found for node " + node.getId());
        return "No log available for this stage";
    }

    private static String getFullLog(Run<?, ?> run) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(run.getLogInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder log = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                log.append(line).append("\n");
            }
            return log.toString();
        } catch (IOException e) {
            return "Error reading full log: " + e.getMessage();
        }
    }
}
