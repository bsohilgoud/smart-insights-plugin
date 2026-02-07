package io.jenkins.plugins.failureanalyzer;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class AnalysisResult implements Serializable {
    private static final long serialVersionUID = 1L;

    private String jobName;
    private int buildNumber;
    private String failedStageName;
    private String fullLog;
    private String errorMessage;
    private List<StageInfo> stages = new ArrayList<>();

    public static class StageInfo implements Serializable {
        private static final long serialVersionUID = 1L;

        private String stageName;
        private String stageLog;
        private boolean failed;

        public StageInfo(String stageName, String stageLog, boolean failed) {
            this.stageName = stageName;
            this.stageLog = stageLog;
            this.failed = failed;
        }

        public String getStageName() {
            return stageName;
        }

        public String getStageLog() {
            return stageLog;
        }

        public boolean isFailed() {
            return failed;
        }
    }

    public String getJobName() {
        return jobName;
    }

    public void setJobName(String jobName) {
        this.jobName = jobName;
    }

    public int getBuildNumber() {
        return buildNumber;
    }

    public void setBuildNumber(int buildNumber) {
        this.buildNumber = buildNumber;
    }

    public String getFailedStageName() {
        return failedStageName;
    }

    public void setFailedStageName(String failedStageName) {
        this.failedStageName = failedStageName;
    }

    public String getFullLog() {
        return fullLog;
    }

    public void setFullLog(String fullLog) {
        this.fullLog = fullLog;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public List<StageInfo> getStages() {
        return stages;
    }

    public void setStages(List<StageInfo> stages) {
        this.stages = stages;
    }

    public void addStage(String stageName, String stageLog, boolean failed) {
        this.stages.add(new StageInfo(stageName, stageLog, failed));
    }

    public boolean hasFailedStage() {
        return failedStageName != null;
    }
}