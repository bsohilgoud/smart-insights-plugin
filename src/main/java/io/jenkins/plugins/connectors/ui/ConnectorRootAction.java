package io.jenkins.plugins.connectors.ui;

import hudson.Extension;
import hudson.model.ManagementLink;
import io.jenkins.plugins.connectors.storage.ConnectorPermissions;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.StaplerProxy;

@Extension
public class ConnectorRootAction extends ManagementLink implements StaplerProxy {

    @Override
    public String getIconFileName() {
        return "symbol-extension";
    }

    @Override
    public String getDisplayName() {
        return "Connectors Management";
    }

    @Override
    public String getUrlName() {
        return "connectors";
    }

    @Override
    public String getDescription() {
        return "Manage Global Connectors for SCM, Database, and Artifact configuration.";
    }

    @Override
    public Object getTarget() {
        Jenkins.get().checkPermission(ConnectorPermissions.VIEW);
        return new ConnectorManagementAction();
    }
}
