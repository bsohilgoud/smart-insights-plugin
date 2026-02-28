package io.jenkins.plugins.failureanalyzer;

import com.google.gson.Gson;
import hudson.model.Run;
import jenkins.model.RunAction2;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.bind.JavaScriptMethod;
import javax.servlet.ServletException;
import java.io.IOException;
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
            LOGGER.info("Starting background extraction and analysis...");
            AnalysisResult result = StageLogExtractor.extractFailureData(run);
            // Optionally clear full log if not used in UI to save bandwidth, or keep if needed
            result.setFullLog("Full log omitted in AJAX to save bandwidth.");

            LOGGER.info("Performing expected AI analysis...");
            String aiResult = LLMAnalyzer.analyze(result);
            result.setAiAnalysis(aiResult);
            LOGGER.info("Analysis completed.");

            return new Gson().toJson(result);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error in AJAX analysis", e);
            return "{\"errorMessage\": \"Error in AJAX analysis: " + e.getMessage().replace("\"", "\\\"") + "\"}";
        }
    }
}
