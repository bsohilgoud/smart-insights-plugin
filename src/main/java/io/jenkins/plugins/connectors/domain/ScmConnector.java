package io.jenkins.plugins.connectors.domain;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import hudson.Extension;
import hudson.model.Item;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

import java.util.Collections;

public class ScmConnector extends Connector {

    private final String scmEngine;
    private final String repoUrl;
    private final String credentialsId;

    @DataBoundConstructor
    public ScmConnector(String id, String name, String description,
                        String scmEngine, String repoUrl, String credentialsId) {
        super(id, name, description);
        this.scmEngine = scmEngine;
        this.repoUrl = repoUrl;
        this.credentialsId = credentialsId;
    }

    public String getScmEngine() {
        return scmEngine;
    }

    public String getRepoUrl() {
        return repoUrl;
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    @Extension
    public static class DescriptorImpl extends ConnectorDescriptor {
        @Override
        public String getDisplayName() {
            return "Git or SCM Connector";
        }

        @Override
        public String getIconClassName() {
            return "symbol-git";
        }

        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Item item, @QueryParameter String credentialsId) {
            StandardListBoxModel result = new StandardListBoxModel();
            if (item == null) {
                if (!Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
                    return result.includeCurrentValue(credentialsId);
                }
            } else {
                if (!item.hasPermission(Item.EXTENDED_READ) && !item.hasPermission(CredentialsProvider.USE_ITEM)) {
                    return result.includeCurrentValue(credentialsId);
                }
            }
            return new StandardListBoxModel()
                    .includeEmptyValue()
                    .includeMatchingAs(ACL.SYSTEM, item, StandardCredentials.class, Collections.emptyList(), CredentialsMatchers.always())
                    .includeCurrentValue(credentialsId);
        }

        @POST
        public FormValidation doTestConnection(@QueryParameter("repoUrl") String repoUrl,
                                               @QueryParameter("credentialsId") String credentialsId) {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            if (repoUrl == null || repoUrl.contains("<") || repoUrl.trim().isEmpty()) {
                return FormValidation.error("Please provide a valid Repository URL. Ensure placeholders are replaced.");
            }
            try {
                // If the user supplied credentials, in a real scenario we'd inject them via GIT_ASKPASS or JGit.
                // For demonstration purposes, we run a native git command to check reachability.
                ProcessBuilder pb = new ProcessBuilder("git", "ls-remote", "-h", repoUrl);
                Process p = pb.start();
                int exitCode = p.waitFor();
                if (exitCode == 0) {
                    return FormValidation.ok("Successfully reached remote repository!");
                } else {
                    return FormValidation.error("Connection failed (Code " + exitCode + "). The repository may be private, require credentials, or does not exist.");
                }
            } catch (Exception e) {
                return FormValidation.error("Failed to execute git ls-remote: " + e.getMessage());
            }
        }
    }
}
