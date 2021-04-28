package de.sist.gitlab.config;

import com.google.common.base.Strings;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@State(name = "PipelineViewerConfigApp", storages = {@Storage("PipelineViewerConfigApp.xml")})
public class PipelineViewerConfigApp implements PersistentStateComponent<PipelineViewerConfigApp> {

    private List<Mapping> mappings = new ArrayList<>();
    private String mergeRequestTargetBranch;
    private List<String> statusesToWatch = new ArrayList<>();
    private boolean showNotificationForWatchedBranches = true;
    private boolean showConnectionErrors = true;
    private List<String> ignoredRemotes = new ArrayList<>();
    private boolean showForTags = true;
    private String urlOpenerCommand;

    public List<Mapping> getMappings() {
        return mappings;
    }

    public void setMappings(List<Mapping> mappings) {
        this.mappings = mappings;
    }

    public boolean isShowConnectionErrors() {
        return showConnectionErrors;
    }

    public String getMergeRequestTargetBranch() {
        return Strings.emptyToNull(mergeRequestTargetBranch);
    }

    public void setMergeRequestTargetBranch(String mergeRequestTargetBranch) {
        this.mergeRequestTargetBranch = mergeRequestTargetBranch;
    }

    public boolean isShowNotificationForWatchedBranches() {
        return showNotificationForWatchedBranches;
    }

    public void setShowNotificationForWatchedBranches(boolean showNotificationForWatchedBranches) {
        this.showNotificationForWatchedBranches = showNotificationForWatchedBranches;
    }

    public boolean isShowConnectionErrorNotifications() {
        return showConnectionErrors;
    }

    public void setShowConnectionErrors(boolean showConnectionErrors) {
        this.showConnectionErrors = showConnectionErrors;
    }

    public List<String> getStatusesToWatch() {
        return statusesToWatch;
    }

    public void setStatusesToWatch(List<String> statusesToWatch) {
        this.statusesToWatch = statusesToWatch;
    }

    public synchronized List<String> getIgnoredRemotes() {
        return ignoredRemotes;
    }

    public synchronized void setIgnoredRemotes(List<String> ignoredRemotes) {
        this.ignoredRemotes = ignoredRemotes;
    }

    public boolean isShowForTags() {
        return showForTags;
    }

    public void setShowForTags(boolean showForTags) {
        this.showForTags = showForTags;
    }

    public String getUrlOpenerCommand() {
        return urlOpenerCommand;
    }

    public void setUrlOpenerCommand(String urlOpenerCommand) {
        this.urlOpenerCommand = urlOpenerCommand;
    }

    @Override
    public @Nullable PipelineViewerConfigApp getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull PipelineViewerConfigApp state) {
        XmlSerializerUtil.copyBean(state, this);
    }

    public static PipelineViewerConfigApp getInstance() {
        return ServiceManager.getService(PipelineViewerConfigApp.class);
    }
}
