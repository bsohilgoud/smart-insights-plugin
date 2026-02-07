package io.jenkins.plugins.failureanalyzer;

import hudson.model.Run;
import jenkins.model.RunAction2;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.verb.POST;
import javax.servlet.ServletException;
import java.io.IOException;

public class AnalyzeFailureAction implements RunAction2 {
    
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

    @POST
    public void doAnalyze(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        run.checkPermission(Run.UPDATE);
        
        AnalysisResult result = StageLogExtractor.extractFailureData(run);
        System.out.println("result = " + result);
        System.out.println("result.getJobName() = " + result.getJobName());
        
        req.setAttribute("result", result);
        req.getView(this, "result.jelly").forward(req, rsp);
    }


    public void doIndex(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        run.checkPermission(Run.UPDATE);

        AnalysisResult result = StageLogExtractor.extractFailureData(run);

        req.setAttribute("result", result);
        req.getView(this, "index.jelly").forward(req, rsp);
    }
}
