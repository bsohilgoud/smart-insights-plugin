package io.jenkins.plugins.connectors.ui;

import com.cloudbees.hudson.plugins.folder.AbstractFolder;
import hudson.ExtensionList;
import hudson.model.Action;
import hudson.model.Descriptor;
import io.jenkins.plugins.connectors.domain.Connector;
import io.jenkins.plugins.connectors.storage.ConnectorPermissions;
import io.jenkins.plugins.connectors.storage.FolderConnectorProperty;
import io.jenkins.plugins.connectors.storage.GlobalConnectorStorage;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ConnectorManagementAction implements Action {

    private final Object context;

    public ConnectorManagementAction() {
        this.context = Jenkins.get();
    }

    public ConnectorManagementAction(AbstractFolder<?> context) {
        this.context = context;
    }

    @Override
    public String getIconFileName() {
        if (hasPermission(ConnectorPermissions.VIEW)) {
            return "symbol-extension";
        }
        return null;
    }

    @Override
    public String getDisplayName() {
        return "Connectors";
    }

    @Override
    public String getUrlName() {
        return "connectors";
    }

    public Object getContext() {
        return context;
    }

    private boolean hasPermission(hudson.security.Permission permission) {
        if (context instanceof AbstractFolder) {
            return ((AbstractFolder<?>) context).hasPermission(permission);
        }
        return Jenkins.get().hasPermission(permission);
    }

    private void checkPermission(hudson.security.Permission permission) {
        if (context instanceof AbstractFolder) {
            ((AbstractFolder<?>) context).checkPermission(permission);
        } else {
            Jenkins.get().checkPermission(permission);
        }
    }

    public List<Connector> getConnectors() {
        if (context instanceof AbstractFolder) {
            FolderConnectorProperty prop = ((AbstractFolder<?>) context).getProperties().get(FolderConnectorProperty.class);
            return prop != null ? prop.getConnectors() : new ArrayList<>();
        }
        return GlobalConnectorStorage.get().getConnectors();
    }

    public Connector getConnector(String id) {
        return getConnectors().stream().filter(c -> c.getId().equals(id)).findFirst().orElse(null);
    }

    public ExtensionList<Connector.ConnectorDescriptor> getConnectorDescriptors() {
        return ExtensionList.lookup(Connector.ConnectorDescriptor.class);
    }

    private Connector parseConnectorFromRequest(StaplerRequest req, net.sf.json.JSONObject form) throws Descriptor.FormException {
        net.sf.json.JSONObject connectorPayload = form.has("connector") ? form.getJSONObject("connector") : form;
        String type = null;

        if (connectorPayload.has("stapler-class")) {
            type = connectorPayload.getString("stapler-class");
        } else if (connectorPayload.has("$class")) {
            type = connectorPayload.getString("$class");
        }

        if ((type == null || type.length() < 3) && form.has("")) {
            String rootType = form.getString("");
            if (rootType.contains(".")) {
                type = rootType;
            }
        }

        if (type == null || type.isEmpty()) {
            if (form.has("connectorType")) {
                type = form.getString("connectorType");
            } else if (req.getParameter("connectorType") != null) {
                type = req.getParameter("connectorType");
            }
        }

        if (type == null || type.isEmpty()) {
            throw new IllegalArgumentException("Connector type must be provided. JSON Dump: " + form);
        }

        Descriptor<Connector> descriptor = Jenkins.get().getDescriptor(type);
        if (descriptor == null) {
            throw new IllegalArgumentException("Invalid connector type: " + type);
        }

        String connectorId = form.has("id") ? form.getString("id") : null;
        if (connectorId == null || connectorId.trim().isEmpty()) {
            throw new IllegalArgumentException("Connector ID is mandatory and cannot be empty.");
        }

        if (form.has("id")) connectorPayload.put("id", form.getString("id"));
        if (form.has("name")) connectorPayload.put("name", form.getString("name"));
        if (form.has("description")) connectorPayload.put("description", form.getString("description"));

        // Fix for Jenkins Table-to-Div Migration bug: flatten JSONArrays back to single String
        for (Object keyObj : new ArrayList<Object>(connectorPayload.keySet())) {
            String k = String.valueOf(keyObj);
            Object v = connectorPayload.get(k);
            if (v instanceof net.sf.json.JSONArray) {
                net.sf.json.JSONArray array = (net.sf.json.JSONArray) v;
                if (!array.isEmpty()) {
                    connectorPayload.put(k, array.getString(array.size() - 1));
                }
            }
        }

        return descriptor.newInstance(req, connectorPayload);
    }

    @org.kohsuke.stapler.verb.POST
    public void doCreate(StaplerRequest req, StaplerResponse rsp) throws ServletException, IOException, Descriptor.FormException {
        checkPermission(ConnectorPermissions.CREATE);

        net.sf.json.JSONObject form = req.getSubmittedForm();
        System.out.println("DEBUG CONNECTORS FORM PAYLOAD: \n" + form.toString(2));

        Connector newConnector = parseConnectorFromRequest(req, form);

        if (context instanceof AbstractFolder) {
            AbstractFolder<?> folder = (AbstractFolder<?>) context;
            FolderConnectorProperty prop = folder.getProperties().get(FolderConnectorProperty.class);
            if (prop == null) {
                prop = new FolderConnectorProperty();
                folder.addProperty(prop);
            }
            prop.addConnector(newConnector);
        } else {
            GlobalConnectorStorage.get().addConnector(newConnector);
        }

        boolean isInline = form.has("isInline") && form.getBoolean("isInline");
        if (isInline) {
            rsp.setContentType("text/html");
            rsp.getWriter().println("<html><body><script>window.parent.closeConnectorDialogAndRefresh();</script></body></html>");
        } else {
            rsp.sendRedirect2("?created=true");
        }
    }

    // Stapler maps form action="update" → doUpdate
    @org.kohsuke.stapler.verb.POST
    public void doUpdate(StaplerRequest req, StaplerResponse rsp) throws ServletException, IOException, Descriptor.FormException {
        checkPermission(ConnectorPermissions.UPDATE);

        net.sf.json.JSONObject form = req.getSubmittedForm();
        Connector updatedConnector = parseConnectorFromRequest(req, form);

        if (context instanceof AbstractFolder) {
            AbstractFolder<?> folder = (AbstractFolder<?>) context;
            FolderConnectorProperty prop = folder.getProperties().get(FolderConnectorProperty.class);
            if (prop != null) {
                prop.removeConnector(updatedConnector.getId());
                prop.addConnector(updatedConnector);
            }
        } else {
            GlobalConnectorStorage.get().removeConnector(updatedConnector.getId());
            GlobalConnectorStorage.get().addConnector(updatedConnector);
        }

        rsp.sendRedirect2("?updated=true");
    }

    // Stapler maps form action="delete" → doDelete
    @org.kohsuke.stapler.verb.POST
    public void doDelete(@QueryParameter("id") String id, StaplerRequest req, StaplerResponse rsp) throws IOException {
        checkPermission(ConnectorPermissions.DELETE);

        if (context instanceof AbstractFolder) {
            FolderConnectorProperty prop = ((AbstractFolder<?>) context).getProperties().get(FolderConnectorProperty.class);
            if (prop != null) {
                prop.removeConnector(id);
            }
        } else {
            GlobalConnectorStorage.get().removeConnector(id);
        }

        rsp.sendRedirect2("?deleted=true");
    }
}
