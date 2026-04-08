package io.jenkins.plugins.connectors;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import hudson.Extension;
import hudson.model.Item;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.SimpleParameterDefinition;
import hudson.model.StringParameterValue;
import hudson.security.ACL;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import java.util.Collections;

public class ConnectorParameterDefinition extends SimpleParameterDefinition {

    private final String connectorType;

    @DataBoundConstructor
    public ConnectorParameterDefinition(String name, String description, String connectorType) {
        super(name, description);
        this.connectorType = connectorType;
    }

    public String getConnectorType() {
        return connectorType;
    }

    @Override
    public ParameterValue createValue(String value) {
        return new ConnectorParameterValue(getName(), value, getDescription());
    }

    @Override
    public ParameterValue createValue(StaplerRequest req, JSONObject jo) {
        ConnectorParameterValue value = req.bindJSON(ConnectorParameterValue.class, jo);
        value.setDescription(getDescription());
        return value;
    }

    @Extension
    public static class DescriptorImpl extends ParameterDescriptor {
        @Override
        public String getDisplayName() {
            return "Connector Parameter";
        }

        public ListBoxModel doFillValueItems(@AncestorInPath Item item) {
            ListBoxModel result = new ListBoxModel();
            
            // Standard Permission Check
            if (item == null) {
                if (!Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
                    return result;
                }
            } else {
                if (!item.hasPermission(Item.EXTENDED_READ)) {
                    return result;
                }
            }
            
            result.add("-- Select Connector --", "");
            
            for (io.jenkins.plugins.connectors.domain.Connector c : io.jenkins.plugins.connectors.storage.ConnectorResolver.getAllConnectors(item)) {
                result.add(String.format("%s (%s)", c.getName(), c.getId()), c.getId());
            }
            
            return result;
        }
    }
}
