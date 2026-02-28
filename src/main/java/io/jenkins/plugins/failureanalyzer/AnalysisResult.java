package io.jenkins.plugins.failureanalyzer;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@ToString
public class AnalysisResult implements Serializable {
    private static final long serialVersionUID = 1L;

    private String jobName;
    private int buildNumber;
    private String failedStageName;
    private String fullLog;
    private String errorMessage;
    private String aiAnalysis;
    private List<StageInfo> stages = new ArrayList<>();
    private long timestamp = System.currentTimeMillis();

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class StageInfo implements Serializable {
        private static final long serialVersionUID = 1L;

        private String stageName;
        private String stageLog;
        private boolean failed;
    }

    public void addStage(String stageName, String stageLog, boolean failed) {
        this.stages.add(new StageInfo(stageName, stageLog, failed));
    }

    public boolean hasFailedStage() {
        return failedStageName != null;
    }
}