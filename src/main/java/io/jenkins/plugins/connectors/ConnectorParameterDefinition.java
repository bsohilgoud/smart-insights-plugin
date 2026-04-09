package io.jenkins.plugins.connectors;

import com.cloudbees.hudson.plugins.folder.AbstractFolder;
import hudson.Extension;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.ParameterValue;
import hudson.model.SimpleParameterDefinition;
import hudson.util.ListBoxModel;
import io.jenkins.plugins.connectors.domain.Connector;
import io.jenkins.plugins.connectors.storage.ConnectorResolver;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import java.util.List;

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

    /**
     * Computes the nearest parent AbstractFolder's /connectors/dialog URL.
     * Called from index.jelly to pre-resolve the scope URL server-side,
     * avoiding unreliable client-side URL regex parsing.
     *
     * @param req the current Stapler request (to walk ancestor chain)
     * @return absolute URL to the folder-level dialog, or null if no folder ancestor
     */
    public String getFolderDialogUrl(StaplerRequest req) {
        // Walk ancestor objects in the request chain to find the owning Item
        Item item = req.findAncestorObject(Item.class);
        if (item != null) {
            ItemGroup<?> parent = item.getParent();
            while (parent != null) {
                if (parent instanceof AbstractFolder) {
                    String rootUrl = Jenkins.get().getRootUrl();
                    if (rootUrl == null) rootUrl = "";
                    // Strip trailing slash before appending folder URL
                    if (rootUrl.endsWith("/")) rootUrl = rootUrl.substring(0, rootUrl.length() - 1);
                    String folderUrl = ((AbstractFolder<?>) parent).getUrl();
                    // Ensure no double slashes
                    return rootUrl + "/" + folderUrl + "connectors/dialog";
                }
                if (parent instanceof Item) {
                    parent = ((Item) parent).getParent();
                } else {
                    break;
                }
            }
        }
        return null; // no folder ancestor — caller should fall back to global
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

            // Permission check — but always at least try to show global connectors
            boolean canRead = false;
            if (item == null) {
                // In global context, require at minimum Jenkins.READ (not full ADMINISTER)
                canRead = Jenkins.get().hasPermission(Jenkins.READ);
            } else {
                canRead = item.hasPermission(Item.READ);
            }

            if (!canRead) {
                result.add("(no permission)", "");
                return result;
            }

            result.add("-- Select Connector --", "");

            List<Connector> connectors = ConnectorResolver.getAllConnectors(item);
            for (Connector c : connectors) {
                String typeShort = c.getClass().getSimpleName().replace("Connector", "");
                String label = String.format("[%s] %s (%s)", typeShort,
                        c.getName() != null && !c.getName().isEmpty() ? c.getName() : c.getId(),
                        c.getId());
                result.add(label, c.getId());
            }

            return result;
        }
    }
}
