package io.jenkins.plugins.connectors.storage;

import hudson.model.Item;
import hudson.security.Permission;
import hudson.security.PermissionScope;
import jenkins.model.Jenkins;
import org.jvnet.localizer.Localizable;

public class ConnectorPermissions {

    // Helper to bypass NPE natively
    private static Localizable text(final String label) {
        return new Localizable(null, label, new Object[0]) {
            @Override
            public String toString(java.util.Locale locale) {
                return label;
            }
            @Override
            public String toString() {
                return label;
            }
        };
    }

    public static final Permission VIEW = new Permission(
            Item.PERMISSIONS, "ConnectorView", text("View custom Connectors"),
            Jenkins.READ, PermissionScope.ITEM_GROUP
    );

    public static final Permission CREATE = new Permission(
            Item.PERMISSIONS, "ConnectorCreate", text("Create custom Connectors"),
            Jenkins.ADMINISTER, PermissionScope.ITEM_GROUP
    );

    public static final Permission UPDATE = new Permission(
            Item.PERMISSIONS, "ConnectorUpdate", text("Update custom Connectors"),
            Jenkins.ADMINISTER, PermissionScope.ITEM_GROUP
    );

    public static final Permission DELETE = new Permission(
            Item.PERMISSIONS, "ConnectorDelete", text("Delete custom Connectors"),
            Jenkins.ADMINISTER, PermissionScope.ITEM_GROUP
    );
}
