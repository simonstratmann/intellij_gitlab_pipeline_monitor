package de.sist.gitlab.pipelinemonitor.config;

import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@State(name = "PipelineViewerConfigApp", storages = {@Storage("PipelineViewerConfigApp.xml")})
public class PipelineViewerConfigApp implements PersistentStateComponent<PipelineViewerConfigApp> {

    public enum DisplayType {
        ICON,
        LINK,
        ID
    }

    public enum MrPipelineDisplayType {
        SOURCE_BRANCH,
        TITLE
    }

    private List<Mapping> mappings = new ArrayList<>();
    private String mergeRequestTargetBranch;
    private List<String> statusesToWatch = new ArrayList<>();
    private boolean showNotificationForWatchedBranches = true;
    private boolean showConnectionErrors = true;
    private List<String> ignoredRemotes = new ArrayList<>();
    private boolean showForTags = true;
    private Integer maxLatestTags;
    private String urlOpenerCommand;
    @com.intellij.util.xmlb.annotations.Transient
    private final Set<String> remotesAskAgainNextTime = new HashSet<>();
    private DisplayType displayType = DisplayType.ICON;
    private int connectTimeout = 10;
    private MrPipelineDisplayType mrPipelineDisplayType = MrPipelineDisplayType.SOURCE_BRANCH;
    private String mrPipelinePrefix = "MR: ";
    private final Map<String, GitlabInfo> gitlabInstanceInfos = new HashMap<>();
    private Integer maxAgeDays;
    private boolean onlyForRemoteBranchesExist;

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

    public Integer getMaxLatestTags() {
        return maxLatestTags;
    }

    public void setMaxLatestTags(Integer maxLatestTags) {
        this.maxLatestTags = maxLatestTags;
    }

    public String getUrlOpenerCommand() {
        return urlOpenerCommand;
    }

    public void setUrlOpenerCommand(String urlOpenerCommand) {
        this.urlOpenerCommand = urlOpenerCommand;
    }

    public Set<String> getRemotesAskAgainNextTime() {
        return remotesAskAgainNextTime;
    }

    public DisplayType getDisplayType() {
        return displayType == null ? DisplayType.ICON : displayType;
    }

    public void setDisplayType(DisplayType displayType) {
        this.displayType = displayType;
    }

    public int getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public MrPipelineDisplayType getMrPipelineDisplayType() {
        return mrPipelineDisplayType == null ? MrPipelineDisplayType.SOURCE_BRANCH : mrPipelineDisplayType;
    }

    public void setMrPipelineDisplayType(MrPipelineDisplayType mrPipelineDisplayType) {
        this.mrPipelineDisplayType = mrPipelineDisplayType;
    }

    public String getMrPipelinePrefix() {
        return mrPipelinePrefix;
    }

    public void setMrPipelinePrefix(String mrPipelinePrefix) {
        this.mrPipelinePrefix = mrPipelinePrefix;
    }

    public Integer getMaxAgeDays() {
        return maxAgeDays;
    }

    public void setMaxAgeDays(Integer maxAgeDays) {
        this.maxAgeDays = maxAgeDays;
    }

    public boolean isOnlyForRemoteBranchesExist() {
        return onlyForRemoteBranchesExist;
    }

    public void setOnlyForRemoteBranchesExist(boolean onlyForRemoteBranchesExist) {
        this.onlyForRemoteBranchesExist = onlyForRemoteBranchesExist;
    }

    public Map<String, GitlabInfo> getGitlabInstanceInfos() {
        return gitlabInstanceInfos;
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

    public static class GitlabInfo {
        private Instant lastCheck = Instant.now();
        private boolean supportsRef = false;

        public GitlabInfo() {
        }

        public GitlabInfo(Instant lastCheck, boolean supportsRef) {
            this.lastCheck = lastCheck;
            this.supportsRef = supportsRef;
        }

        public void setLastCheck(Instant lastCheck) {
            this.lastCheck = lastCheck;
        }


        public Instant getLastCheck() {
            return lastCheck;
        }

        public boolean isSupportsRef() {
            return supportsRef;
        }

        public void setSupportsRef(boolean supportsRef) {
            this.supportsRef = supportsRef;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("lastCheck", lastCheck)
                    .add("supportsRef", supportsRef)
                    .toString();
        }
    }

}
