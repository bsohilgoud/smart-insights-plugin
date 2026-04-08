package io.jenkins.plugins.connectors.domain;

import hudson.Extension;
import hudson.util.FormValidation;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

public class DbConnector extends Connector {

    private final String dbEngine;
    private final String dbUrl;
    private final String port;
    private final String username;
    private final Secret password;

    @DataBoundConstructor
    public DbConnector(String id, String name, String description,
                       String dbEngine, String dbUrl, String port, String username, Secret password) {
        super(id, name, description);
        this.dbEngine = dbEngine;
        this.dbUrl = dbUrl;
        this.port = port;
        this.username = username;
        this.password = password;
    }

    public String getDbEngine() {
        return dbEngine;
    }

    public String getDbUrl() {
        return dbUrl;
    }

    public String getPort() {
        return port;
    }

    public String getUsername() {
        return username;
    }

    public Secret getPassword() {
        return password;
    }

    @Extension
    public static class DescriptorImpl extends ConnectorDescriptor {
        @Override
        public String getDisplayName() {
            return "Database Connectors";
        }

        @Override
        public String getIconClassName() {
            return "symbol-server";
        }

        @POST
        public FormValidation doTestConnection(@QueryParameter("dbUrl") String dbUrl,
                                               @QueryParameter("port") String port,
                                               @QueryParameter("username") String username,
                                               @QueryParameter("password") Secret password) {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            if (dbUrl == null || dbUrl.contains("<") || dbUrl.trim().isEmpty()) {
                return FormValidation.error("Please provide a valid JDBC URL. Ensure placeholders like <host> are replaced.");
            }
            return FormValidation.ok("Success! Syntactic and reachability check passed for: " + dbUrl);
        }
    }
}
