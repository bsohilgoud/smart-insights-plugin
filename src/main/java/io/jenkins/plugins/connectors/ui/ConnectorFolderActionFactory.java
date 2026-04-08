package io.jenkins.plugins.connectors.ui;

import com.cloudbees.hudson.plugins.folder.AbstractFolder;
import hudson.Extension;
import hudson.model.Action;
import jenkins.model.TransientActionFactory;

import java.util.Collection;
import java.util.Collections;

@Extension
public class ConnectorFolderActionFactory extends TransientActionFactory<AbstractFolder> {

    @Override
    public Class<AbstractFolder> type() {
        return AbstractFolder.class;
    }

    @Override
    public Collection<? extends Action> createFor(AbstractFolder target) {
        return Collections.singletonList(new ConnectorManagementAction(target));
    }
}
