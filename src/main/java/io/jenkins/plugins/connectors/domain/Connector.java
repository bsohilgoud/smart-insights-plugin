package io.jenkins.plugins.connectors.domain;

import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;

public abstract class Connector extends AbstractDescribableImpl<Connector> {

    private final String id;
    private final String name;
    private final String description;

    protected Connector(String id, String name, String description) {
        this.id = id;
        this.name = name;
        this.description = description;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public static abstract class ConnectorDescriptor extends Descriptor<Connector> {
        protected ConnectorDescriptor() {
        }
        
        public abstract String getIconClassName();
    }
}
