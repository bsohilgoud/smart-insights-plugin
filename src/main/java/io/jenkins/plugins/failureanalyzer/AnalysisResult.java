package io.jenkins.plugins.failureanalyzer;

import java.io.Serializable;

public class AnalysisResult implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String jobName;
    private int buildNumber;
    private String failedStageName;
    private String stageLog;
    private String fullLog;
    private String errorMessage;

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

    public String getStageLog() {
        return stageLog;
    }

    public void setStageLog(String stageLog) {
        this.stageLog = stageLog;
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

    public boolean hasFailedStage() {
        return failedStageName != null;
    }

    @Override
    public String toString() {
        return "AnalysisResult{" +
                "jobName='" + jobName + '\'' +
                ", buildNumber=" + buildNumber +
                ", failedStageName='" + failedStageName + '\'' +
                ", stageLog='" + stageLog + '\'' +
                ", fullLog='" + fullLog + '\'' +
                ", errorMessage='" + errorMessage + '\'' +
                '}';
    }
}
