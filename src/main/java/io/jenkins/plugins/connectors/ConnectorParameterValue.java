package io.jenkins.plugins.connectors;

import hudson.model.StringParameterValue;
import org.kohsuke.stapler.DataBoundConstructor;

public class ConnectorParameterValue extends StringParameterValue {

    @DataBoundConstructor
    public ConnectorParameterValue(String name, String value, String description) {
        super(name, value, description);
    }

    public ConnectorParameterValue(String name, String value) {
        super(name, value);
    }
}
