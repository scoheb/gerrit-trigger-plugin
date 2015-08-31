package com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.workflow;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.TaskListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Builder;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;

import org.kohsuke.stapler.DataBoundConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sonyericsson.hudson.plugins.gerrit.trigger.GerritServer;
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;
import com.sonyericsson.hudson.plugins.gerrit.trigger.config.IGerritHudsonTriggerConfig;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritCause;
import com.sonymobile.tools.gerrit.gerritevents.GerritQueryHandler;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.ChangeBasedEvent;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.GerritTriggeredEvent;

import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;

public class GerritWorkflowWaitForReviewStep extends Builder implements SimpleBuildStep {

    private static final int SLEEPTIMEBETWEENTRIES = 1000;
    private static final Logger logger = LoggerFactory.getLogger(GerritWorkflowWaitForReviewStep.class);
    private String label;
    private String labelValue;
    private String maxWaitTimeInMins;
    
    @DataBoundConstructor
    public GerritWorkflowWaitForReviewStep(String label, String labelValue, String maxWaitTimeInMins) {
        this.label = label;
        this.labelValue = labelValue;
        this.maxWaitTimeInMins = maxWaitTimeInMins;
    }

    public String getMaxWaitTimeInMins() {
        return maxWaitTimeInMins;
    }

    public String getLabel() {
        return label;
    }

    public String getLabelValue() {
        return labelValue;
    }

    @Override
    public boolean prebuild(AbstractBuild<?, ?> build, BuildListener listener) {
        return false;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
            throws InterruptedException, IOException {
        return false;
    }

    @Override
    public Collection<? extends Action> getProjectActions(AbstractProject<?, ?> project) {
        return null;
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    @Override
    public void perform(Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener)
            throws InterruptedException, IOException {
        listener.getLogger().println("Running GerritWorkflowWaitForReviewStep");
        GerritCause cause = GerritCause.getCause(run);
        if (cause == null) {
            listener.getLogger().println("GerritWorkflowWaitForReviewStep: Build not triggered by Gerrit...returning.");
            return;
        }
        listener.getLogger().println("Cause: " + cause);
        GerritCause.cleanUpGerritCauses(cause, run);
        GerritTriggeredEvent event = cause.getEvent();
        listener.getLogger().println("Event: " + event);

        ChangeBasedEvent cbEvent = null;
        String selectedServer = null;
        if (event instanceof ChangeBasedEvent) {
            cbEvent = (ChangeBasedEvent)event;
            selectedServer = cbEvent.getProvider().getName();
            listener.getLogger().println("GerritWorkflowWaitForReviewStep: server: " + selectedServer);
        } else {
            listener.getLogger().println("GerritWorkflowWaitForReviewStep: Gerrit Event is not a Change-Based one...returning.");
            return;
        }

        IGerritHudsonTriggerConfig config = getServerConfig(selectedServer);
        if (config != null) {
            GerritQueryHandler handler = new GerritQueryHandler(config);

            String queryString = "change:" + cbEvent.getChange().getNumber() + " " + label + "=" + labelValue;
            listener.getLogger().println(queryString);
            try {
                List<String> json = null;
                boolean found = false;

                int timer = 0;
                //CS IGNORE MagicNumber FOR NEXT 1 LINE. REASON: Seconds
                int maxWait = new Long(maxWaitTimeInMins).intValue() * 60 * SLEEPTIMEBETWEENTRIES; 
                while (!found || timer < maxWait) {
                    json = handler.queryJson(queryString);
                    for (String line: json) {
                        JSONObject jsonObj = (JSONObject)JSONSerializer.toJSON(line.trim());
                        if (jsonObj.has("type") && "stats".equalsIgnoreCase(jsonObj.getString("type"))) {
                            //Ignore this line
                        } else {
                            found = true;
                        }
                    }
                    if (!found) {
                        listener.getLogger().println("GerritWorkflowWaitForReviewStep: Waiting for " + label + " = " + labelValue);
                        Thread.sleep(SLEEPTIMEBETWEENTRIES);
                        timer = timer + SLEEPTIMEBETWEENTRIES;
                    } else {
                        listener.getLogger().println("GerritWorkflowWaitForReviewStep: FOUND " + label + " = " + labelValue);
                        break;
                    }
                }
                for (String j : json) {
                    listener.getLogger().println(j);
                }
            } catch (Exception ex) {
                logger.warn("Could not query Gerrit for [" + queryString + "]", ex);
            }
        } else {
            listener.getLogger().println("GerritWorkflowWaitForReviewStep: Could not find config for the server " + selectedServer);
            logger.error("Could not find config for the server {}", selectedServer);
            return;
        }
    }

    /**
     * Get the server config.
     *
     * @param serverName the name of the server.
     * @return the config of the server or null if config not found.
     */
    private IGerritHudsonTriggerConfig getServerConfig(String serverName) {
        GerritServer server = PluginImpl.getServer_(serverName);
        if (server != null) {
            IGerritHudsonTriggerConfig config = server.getConfig();
            if (config != null) {
                return config;
            } else {
                logger.error("Could not find the config of server: {}", serverName);
            }
        } else {
            logger.error("Could not find server {}", serverName);
        }
        return null;
    }

    @Override
    public Action getProjectAction(AbstractProject<?, ?> project) {
        return null;
    }

    public static DescriptorImpl descriptor() {
        return Jenkins.getInstance().getDescriptorByType(
                GerritWorkflowWaitForReviewStep.DescriptorImpl.class);
    }

    @Extension
    public static final class DescriptorImpl extends
            BuildStepDescriptor<Builder> {

        @SuppressWarnings("rawtypes")
        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "GerritWorkflowWaitForReviewStep";
        }

    }
    
}
