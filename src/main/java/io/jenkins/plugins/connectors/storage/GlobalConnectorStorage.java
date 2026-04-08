package io.jenkins.plugins.connectors.storage;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.XmlFile;
import hudson.model.Saveable;
import hudson.model.listeners.SaveableListener;
import io.jenkins.plugins.connectors.domain.Connector;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundSetter;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

@Extension
public class GlobalConnectorStorage implements Saveable {
    private static final Logger LOGGER = Logger.getLogger(GlobalConnectorStorage.class.getName());

    private List<Connector> connectors = new ArrayList<>();

    public GlobalConnectorStorage() {
        load();
    }

    public static GlobalConnectorStorage get() {
        return ExtensionList.lookupSingleton(GlobalConnectorStorage.class);
    }

    public List<Connector> getConnectors() {
        return connectors;
    }

    @DataBoundSetter
    public void setConnectors(List<Connector> connectors) {
        this.connectors = connectors == null ? new ArrayList<>() : new ArrayList<>(connectors);
    }

    protected XmlFile getConfigFile() {
        return new XmlFile(new File(Jenkins.get().getRootDir(), "connectors.xml"));
    }

    public void load() {
        XmlFile file = getConfigFile();
        if (file.exists()) {
            try {
                file.unmarshal(this);
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to load global connectors", e);
            }
        }
    }

    @Override
    public void save() throws IOException {
        getConfigFile().write(this);
        SaveableListener.fireOnChange(this, getConfigFile());
    }

    public void addConnector(Connector connector) throws IOException {
        this.connectors.add(connector);
        save();
    }

    public void removeConnector(String id) throws IOException {
        this.connectors.removeIf(c -> c.getId().equals(id));
        save();
    }

    public void updateConnector(Connector connector) throws IOException {
        for (int i = 0; i < connectors.size(); i++) {
            if (connectors.get(i).getId().equals(connector.getId())) {
                connectors.set(i, connector);
                save();
                return;
            }
        }
    }
}
