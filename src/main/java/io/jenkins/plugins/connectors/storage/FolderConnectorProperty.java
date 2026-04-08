package io.jenkins.plugins.connectors.storage;

import com.cloudbees.hudson.plugins.folder.AbstractFolder;
import com.cloudbees.hudson.plugins.folder.AbstractFolderProperty;
import com.cloudbees.hudson.plugins.folder.AbstractFolderPropertyDescriptor;
import hudson.Extension;
import io.jenkins.plugins.connectors.domain.Connector;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class FolderConnectorProperty extends AbstractFolderProperty<AbstractFolder<?>> {

    private List<Connector> connectors = new ArrayList<>();

    @DataBoundConstructor
    public FolderConnectorProperty() {
    }

    public List<Connector> getConnectors() {
        return connectors;
    }

    @DataBoundSetter
    public void setConnectors(List<Connector> connectors) {
        this.connectors = connectors == null ? new ArrayList<>() : new ArrayList<>(connectors);
    }

    public void addConnector(Connector connector) throws IOException {
        this.connectors.add(connector);
        owner.save();
    }

    public void removeConnector(String id) throws IOException {
        this.connectors.removeIf(c -> c.getId().equals(id));
        owner.save();
    }

    public void updateConnector(Connector connector) throws IOException {
        for (int i = 0; i < connectors.size(); i++) {
            if (connectors.get(i).getId().equals(connector.getId())) {
                connectors.set(i, connector);
                owner.save();
                return;
            }
        }
    }

    @Extension
    public static class DescriptorImpl extends AbstractFolderPropertyDescriptor {
        @Override
        public String getDisplayName() {
            return "Folder Connectors Storage";
        }
    }
}
