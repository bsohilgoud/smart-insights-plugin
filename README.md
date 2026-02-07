# Jenkins Failure Analyzer Plugin - Integration Guide

## Project Structure

```
failure-analyzer/
├── pom.xml
└── src/main/
    ├── java/io/jenkins/plugins/failureanalyzer/
    │   ├── AnalyzeFailureAction.java
    │   ├── AnalyzeFailureActionFactory.java
    │   ├── StageLogExtractor.java
    │   └── AnalysisResult.java
    └── resources/io/jenkins/plugins/failureanalyzer/
        └── AnalyzeFailureAction/
            ├── index.jelly
            └── result.jelly
```

## Key Implementation Details

### 1. Action Visibility Logic
- `getIconFileName()` returns `null` for non-failed builds → button hidden
- Uses `symbol-search` icon (no image files needed)
- Automatically attached to ALL builds via `TransientActionFactory`

### 2. Pipeline Log Extraction
- Traverses FlowNode graph to find failed stage
- Uses `StageAction` to identify stage boundaries
- Uses `ErrorAction` to find the failure point
- Uses `LogAction` to extract stage-specific logs

### 3. Security
- `@POST` annotation on doAnalyze method
- Permission check: `run.checkPermission(Run.UPDATE)`

## Build & Install

```bash
mvn clean package
# Install: target/failure-analyzer.hpi
```

## Extending for AI Integration

The `AnalysisResult` object contains:
- `jobName`, `buildNumber`
- `failedStageName` (Pipeline only)
- `stageLog` (failed stage only)
- `fullLog` (entire build)
- `errorMessage`

### Example: Send to AI Backend

```java
@POST
public void doAnalyze(StaplerRequest req, StaplerResponse rsp) throws IOException {
    AnalysisResult result = StageLogExtractor.extractFailureData(run);
    
    // Send to AI service
    String aiAnalysis = YourAIService.analyze(
        result.getStageLog(),
        result.getErrorMessage()
    );
    
    req.setAttribute("result", result);
    req.setAttribute("aiAnalysis", aiAnalysis);
    req.getView(this, "result.jelly").forward(req, rsp);
}
```

## Testing

1. Create a Pipeline job with failing stage:
```groovy
pipeline {
    agent any
    stages {
        stage('Build') {
            steps { echo 'Building...' }
        }
        stage('Test') {
            steps { 
                error 'Tests failed!'
            }
        }
    }
}
```

2. Run the build (it will fail)
3. Open build page → see "Analyze Failure" in sidebar
4. Click → triggers analysis → shows results

## Notes

- Works with both Pipeline and non-Pipeline jobs
- For non-Pipeline jobs, only full log is extracted
- Jelly files must be in `resources/io/jenkins/plugins/failureanalyzer/AnalyzeFailureAction/`
- The action uses Stapler's convention-based routing