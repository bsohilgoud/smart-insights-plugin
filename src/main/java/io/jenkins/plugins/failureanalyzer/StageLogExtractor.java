package io.jenkins.plugins.failureanalyzer;

import hudson.model.Result;
import hudson.model.Run;
import org.jenkinsci.plugins.workflow.actions.LabelAction;
import org.jenkinsci.plugins.workflow.actions.LogAction;
import org.jenkinsci.plugins.workflow.graph.BlockEndNode;
import org.jenkinsci.plugins.workflow.graph.BlockStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowGraphWalker;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;
import java.util.logging.Level;

public class StageLogExtractor {

    private static final Logger LOGGER = Logger.getLogger(StageLogExtractor.class.getName());

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
        LOGGER.info("Starting pipeline data extraction using BlockStartNode strategy");

        try {
            // Collect ALL nodes (walker returns newest-first)
            FlowGraphWalker walker = new FlowGraphWalker(run.getExecution());
            List<FlowNode> allNodes = new ArrayList<>();
            for (FlowNode n : walker) {
                allNodes.add(n);
            }
            LOGGER.info("Total nodes: " + allNodes.size());

            // Find ALL error nodes
            List<FlowNode> errorNodes = new ArrayList<>();
            for (FlowNode node : allNodes) {
                if (node.getError() != null) {
                    errorNodes.add(node);
                    String errMsg = node.getError().getError() != null ?
                            node.getError().getError().getMessage() :
                            "Unknown error";
                    LOGGER.info("Found error at node " + node.getId() + ": " + errMsg);
                }
            }

            boolean wasAborted = (run.getResult() == Result.ABORTED);
            LOGGER.info("Found " + errorNodes.size() + " error nodes, wasAborted: " + wasAborted);

            // Find all BlockStartNode candidates
            List<BlockStartNode> blockStartNodes = new ArrayList<>();
            for (FlowNode node : allNodes) {
                if (node instanceof BlockStartNode) {
                    blockStartNodes.add((BlockStartNode) node);
                }
            }
            LOGGER.info("Found " + blockStartNodes.size() + " BlockStartNodes");

            // Process each potential stage
            List<StageData> stages = new ArrayList<>();
            for (BlockStartNode startNode : blockStartNodes) {
                String stageName = getStageName(startNode);

                // Filter out non-user stages
                if (stageName != null && !isFilteredStage(stageName)) {
                    LOGGER.fine("Processing stage: " + stageName);

                    // Find corresponding end node
                    BlockEndNode endNode = findEndNode(startNode, allNodes);

                    // Determine status
                    String status = "PASSED";
                    String errorMsg = null;

                    if (!errorNodes.isEmpty()) {
                        for (FlowNode errNode : errorNodes) {
                            if (isErrorDirectlyInStage(errNode, startNode, endNode, allNodes)) {
                                status = wasAborted ? "ABORTED" : "FAILED";
                                errorMsg = errNode.getError().getError() != null ?
                                        errNode.getError().getError().getMessage() :
                                        "Unknown error";
                                LOGGER.info("Stage '" + stageName + "' marked as " + status);
                                break;
                            }
                        }
                    }

                    // Extract logs
                    String log = getStageLog(startNode, endNode, allNodes);
                    LOGGER.fine("Extracted " + log.length() + " chars for stage: " + stageName);

                    stages.add(new StageData(stageName, status, log, errorMsg, allNodes.indexOf(startNode)));
                }
            }

            // Sort chronologically (earliest first)
            Collections.reverse(stages);
            Collections.sort(stages, (a, b) -> Integer.compare(b.nodeIndex, a.nodeIndex));

            LOGGER.info("Total stages extracted: " + stages.size());

            // Add to result
            for (StageData stage : stages) {
                boolean isFailed = "FAILED".equals(stage.status) || "ABORTED".equals(stage.status);
                result.addStage(stage.name, stage.log, isFailed);

                if (isFailed && result.getFailedStageName() == null) {
                    result.setFailedStageName(stage.name);
                    result.setErrorMessage(stage.errorMsg);
                }
            }

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error extracting pipeline data", e);
            result.setErrorMessage("Error extracting pipeline data: " + e.getMessage());
        }
    }

    private static String getStageName(BlockStartNode startNode) {
        // Prefer LabelAction when present
        LabelAction label = startNode.getAction(LabelAction.class);
        if (label != null && label.getDisplayName() != null) {
            return label.getDisplayName().trim();
        }

        // Fallback to displayName
        String displayName = startNode.getDisplayName();
        return (displayName != null) ? displayName.trim() : null;
    }

    private static boolean isFilteredStage(String stageName) {
        return stageName.contains("Stage : Start") ||
                stageName.contains("Stage : End") ||
                stageName.contains(": Body :") ||
                stageName.contains("Change current directory") ||
                stageName.contains("Allocate node") ||
                stageName.contains("Timestamps") ||
                stageName.contains("Color ANSI") ||
                stageName.contains("Bind credentials") ||
                stageName.contains("Set environment") ||
                stageName.contains("Retry the body") ||
                stageName.contains("set AWS settings") ||
                stageName.contains("Setting storage environment") ||
                stageName.equals("Start of Pipeline");
    }

    private static BlockEndNode findEndNode(BlockStartNode startNode, List<FlowNode> allNodes) {
        for (FlowNode node : allNodes) {
            if (node instanceof BlockEndNode) {
                BlockEndNode endNode = (BlockEndNode) node;
                if (endNode.getStartNode().equals(startNode)) {
                    return endNode;
                }
            }
        }
        return null;
    }

    private static boolean isErrorDirectlyInStage(FlowNode errorNode, BlockStartNode startNode,
                                                  BlockEndNode endNode, List<FlowNode> allNodes) {
        int errorIdx = allNodes.indexOf(errorNode);
        int startIdx = allNodes.indexOf(startNode);
        int endIdx = (endNode != null) ? allNodes.indexOf(endNode) : 0;

        if (errorIdx < 0 || startIdx < 0) {
            return false;
        }

        // allNodes is newest-first, so check if error is in range [endIdx, startIdx]
        boolean inRange = (errorIdx >= endIdx && errorIdx <= startIdx);

        if (!inRange) {
            return false;
        }

        // Check if there's a more specific nested stage containing the error
        for (FlowNode node : allNodes) {
            if (node instanceof BlockStartNode && !node.equals(startNode)) {
                BlockStartNode other = (BlockStartNode) node;
                int otherStartIdx = allNodes.indexOf(other);
                if (otherStartIdx < 0) continue;

                BlockEndNode otherEnd = findEndNode(other, allNodes);
                int otherEndIdx = otherEnd != null ? allNodes.indexOf(otherEnd) : 0;

                // Check if 'other' stage is nested inside current stage
                boolean otherIsNested = (otherStartIdx > endIdx && otherStartIdx < startIdx);

                // Check if error is in the nested stage
                boolean errorInOther = (errorIdx >= otherEndIdx && errorIdx <= otherStartIdx);

                if (otherIsNested && errorInOther) {
                    // Don't match filtered-out stages as "more specific"
                    String otherName = getStageName(other);
                    boolean isFilteredStage = otherName != null && isFilteredStage(otherName);

                    if (!isFilteredStage) {
                        return false;  // Error belongs to a more specific nested stage
                    }
                }
            }
        }

        return true;
    }

    private static String getStageLog(BlockStartNode start, BlockEndNode end, List<FlowNode> allNodes) {
        StringBuilder sb = new StringBuilder();

        int startIdx = allNodes.indexOf(start);
        int endIdx = (end != null) ? allNodes.indexOf(end) : 0;
        if (startIdx < 0) {
            return "";
        }

        // allNodes is newest-first, so range is [endIdx .. startIdx]
        for (int i = Math.max(endIdx, 0); i <= startIdx; i++) {
            FlowNode n = allNodes.get(i);
            LogAction la = n.getAction(LogAction.class);
            if (la != null) {
                try {
                    StringWriter w = new StringWriter();
                    la.getLogText().writeLogTo(0, w);
                    String part = w.toString();
                    if (part != null && !part.trim().isEmpty()) {
                        sb.append(part);
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Error reading log for node " + n.getId(), e);
                }
            }
        }
        return sb.toString();
    }

    private static String getFullLog(Run<?, ?> run) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(run.getLogInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder log = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                log.append(line).append("\n");
            }
            LOGGER.info("Full log extracted: " + log.length() + " chars");
            return log.toString();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error reading full log", e);
            return "Error reading full log: " + e.getMessage();
        }
    }

    private static class StageData {
        String name;
        String status;
        String log;
        String errorMsg;
        int nodeIndex;

        StageData(String name, String status, String log, String errorMsg, int nodeIndex) {
            this.name = name;
            this.status = status;
            this.log = log;
            this.errorMsg = errorMsg;
            this.nodeIndex = nodeIndex;
        }
    }
}