package io.jenkins.plugins.connectors.storage;

import com.cloudbees.hudson.plugins.folder.AbstractFolder;
import hudson.model.Item;
import hudson.model.ItemGroup;
import io.jenkins.plugins.connectors.domain.Connector;

import java.util.ArrayList;
import java.util.List;

public class ConnectorResolver {

    public static Connector getConnector(String id, Item context) {
        if (id == null) return null;

        ItemGroup<?> current = context != null ? context.getParent() : null;

        while (current != null) {
            if (current instanceof AbstractFolder) {
                AbstractFolder<?> folder = (AbstractFolder<?>) current;
                FolderConnectorProperty prop = folder.getProperties().get(FolderConnectorProperty.class);
                if (prop != null) {
                    for (Connector c : prop.getConnectors()) {
                        if (id.equals(c.getId())) {
                            return c;
                        }
                    }
                }
            }
            if (current instanceof Item) {
                current = ((Item) current).getParent();
            } else {
                break;
            }
        }

        GlobalConnectorStorage globalStorage = GlobalConnectorStorage.get();
        if (globalStorage != null) {
            for (Connector c : globalStorage.getConnectors()) {
                if (id.equals(c.getId())) {
                    return c;
                }
            }
        }

        return null;
    }

    public static List<Connector> getAllConnectors(Item context) {
        List<Connector> all = new ArrayList<>();

        GlobalConnectorStorage globalStorage = GlobalConnectorStorage.get();
        if (globalStorage != null) {
            all.addAll(globalStorage.getConnectors());
        }

        ItemGroup<?> current = context != null ? context.getParent() : null;
        while (current != null) {
            if (current instanceof AbstractFolder) {
                AbstractFolder<?> folder = (AbstractFolder<?>) current;
                FolderConnectorProperty prop = folder.getProperties().get(FolderConnectorProperty.class);
                if (prop != null) {
                    all.addAll(prop.getConnectors());
                }
            }
            if (current instanceof Item) {
                current = ((Item) current).getParent();
            } else {
                break;
            }
        }

        return all;
    }
}
