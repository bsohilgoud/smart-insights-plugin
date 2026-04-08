package io.jenkins.plugins.connectors;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.Item;
import hudson.model.Run;
import hudson.util.Secret;
import io.jenkins.plugins.connectors.domain.Connector;
import io.jenkins.plugins.connectors.domain.DbConnector;
import io.jenkins.plugins.connectors.domain.ScmConnector;
import io.jenkins.plugins.connectors.storage.ConnectorResolver;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.EnvironmentExpander;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class WithConnectorStep extends Step {

    private final String connectorId;

    @DataBoundConstructor
    public WithConnectorStep(String connectorId) {
        this.connectorId = connectorId;
    }

    public String getConnectorId() {
        return connectorId;
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new Execution(connectorId, context);
    }

    @Extension
    public static class DescriptorImpl extends StepDescriptor {
        @Override
        public String getFunctionName() {
            return "withConnector";
        }

        @Override
        public String getDisplayName() {
            return "Bind Connector properties to environment";
        }

        @Override
        public boolean takesImplicitBlockArgument() {
            return true;
        }

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return Collections.singleton(Run.class);
        }
    }

    private static class Execution extends StepExecution {
        private static final long serialVersionUID = 1L;
        private final String connectorId;

        Execution(String connectorId, StepContext context) {
            super(context);
            this.connectorId = connectorId;
        }

        @Override
        public boolean start() throws Exception {
            Run<?, ?> run = getContext().get(Run.class);
            
            Connector connector = ConnectorResolver.getConnector(connectorId, run.getParent());

            if (connector == null) {
                throw new IllegalArgumentException("No Connector found with id: " + connectorId);
            }

            Map<String, String> envOverrides = new HashMap<>();

            if (connector instanceof ScmConnector) {
                ScmConnector scm = (ScmConnector) connector;
                envOverrides.put("CONNECTOR_URL", scm.getRepoUrl());
                envOverrides.put("CONNECTOR_CREDENTIALS_ID", scm.getCredentialsId());
            } else if (connector instanceof DbConnector) {
                DbConnector db = (DbConnector) connector;
                envOverrides.put("CONNECTOR_URL", db.getDbUrl());
                envOverrides.put("CONNECTOR_PORT", db.getPort());
                envOverrides.put("CONNECTOR_USER", db.getUsername());
                envOverrides.put("CONNECTOR_PASSWORD", Secret.toString(db.getPassword()));
            }

            getContext().newBodyInvoker()
                    .withContext(EnvironmentExpander.merge(getContext().get(EnvironmentExpander.class), new ConnectorExpander(envOverrides)))
                    .withCallback(BodyExecutionCallback.wrap(getContext()))
                    .start();

            return false;
        }
    }

    private static class ConnectorExpander extends EnvironmentExpander implements Serializable {
        private static final long serialVersionUID = 1L;
        private final Map<String, String> overrides;

        ConnectorExpander(Map<String, String> overrides) {
            this.overrides = overrides;
        }

        @Override
        public void expand(EnvVars env) {
            env.putAll(overrides);
        }
    }
}
