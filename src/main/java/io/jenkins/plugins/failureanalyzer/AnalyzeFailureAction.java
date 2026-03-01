package io.jenkins.plugins.failureanalyzer;

import com.google.gson.Gson;
import hudson.model.Run;
import jenkins.model.RunAction2;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.bind.JavaScriptMethod;
import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AnalyzeFailureAction implements RunAction2 {

    private static final Logger LOGGER = Logger.getLogger(AnalyzeFailureAction.class.getName());
    
    private transient Run<?, ?> run;

    public AnalyzeFailureAction(Run run) {
        this.run = run;
    }
    
    @Override
    public void onAttached(Run<?, ?> run) {
        this.run = run;
    }

    @Override
    public void onLoad(Run<?, ?> run) {
        this.run = run;
    }

    @Override
    public String getIconFileName() {
        return "symbol-search";
    }

    @Override
    public String getDisplayName() {
        return "Analyze Failure";
    }

    @Override
    public String getUrlName() {
        return "analyze-failure";
    }

    public Run<?, ?> getRun() {
        return run;
    }

    public void setRun(Run<?, ?> run) {
        this.run = run;
    }




    public void doIndex(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        run.checkPermission(Run.UPDATE);
        // Render the page immediately. The fetching and AI analysis will happen via AJAX.
        req.getView(this, "index.jelly").forward(req, rsp);
    }

    @JavaScriptMethod
    public String doAnalyzeAjax() {
        run.checkPermission(Run.UPDATE);

        try {
            File cacheFile = new File(run.getRootDir(), "smart-insights-analysis.json");
            if (cacheFile.exists()) {
                LOGGER.info("Zero-cost cache hit! Returning saved analysis from: " + cacheFile.getAbsolutePath());
                return new String(Files.readAllBytes(cacheFile.toPath()), StandardCharsets.UTF_8);
            }

            LOGGER.info("Starting background extraction and analysis...");
            AnalysisResult result = StageLogExtractor.extractFailureData(run);
            // Optionally clear full log if not used in UI to save bandwidth, or keep if needed
            result.setFullLog("Full log omitted in AJAX to save bandwidth.");

            LOGGER.info("Performing expected AI analysis...");
            String aiResult = LLMAnalyzer.analyze(result);
            result.setAiAnalysis(aiResult);
            LOGGER.info("Analysis completed.");

            String jsonResponse = new Gson().toJson(result);
            
            // Save to cache
            Files.write(cacheFile.toPath(), jsonResponse.getBytes(StandardCharsets.UTF_8));
            LOGGER.info("Saved analysis cache to: " + cacheFile.getAbsolutePath());

            return jsonResponse;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error in AJAX analysis", e);
            return "{\"errorMessage\": \"Error in AJAX analysis: " + e.getMessage().replace("\"", "\\\"") + "\"}";
        }
    }
}
