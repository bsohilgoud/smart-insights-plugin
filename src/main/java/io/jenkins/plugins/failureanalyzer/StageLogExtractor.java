package io.jenkins.plugins.failureanalyzer;

import hudson.model.Run;
import org.jenkinsci.plugins.workflow.actions.ErrorAction;
import org.jenkinsci.plugins.workflow.actions.LogAction;
import org.jenkinsci.plugins.workflow.actions.StageAction;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
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
            return;
        }

        List<FlowNode> nodes = new ArrayList<>();
        execution.getCurrentHeads().forEach(node -> collectNodes(node, nodes));

        for (FlowNode node : nodes) {
            StageAction stageAction = node.getPersistentAction(StageAction.class);
            ErrorAction errorAction = node.getError();

            if (stageAction != null && errorAction != null) {
                result.setFailedStageName(stageAction.getStageName());
                result.setStageLog(extractNodeLog(node, run));
                result.setErrorMessage(errorAction.getError().getMessage());
                break;
            }
        }
    }

    private static void collectNodes(FlowNode node, List<FlowNode> collector) {
        if (node == null) {
            return;
        }
        collector.add(node);
        for (FlowNode parent : node.getParents()) {
            collectNodes(parent, collector);
        }
    }

    private static String extractNodeLog(FlowNode node, WorkflowRun run) {
        LogAction logAction = node.getAction(LogAction.class);
        if (logAction != null) {
            try {
                StringWriter writer = new StringWriter();
                logAction.getLogText().writeLogTo(0, writer);
                return writer.toString();
            } catch (IOException e) {
                return "Error extracting stage log: " + e.getMessage();
            }
        }
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
