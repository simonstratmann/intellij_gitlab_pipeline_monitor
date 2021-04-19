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

    private String gitlabUrl;
    private String gitlabAuthToken;
    private Integer gitlabProjectId;
    private String mergeRequestTargetBranch = "master";
    private List<String> statusesToWatch = new ArrayList<>();
    private boolean showNotificationForWatchedBranches = true;
    private boolean showConnectionErrors = true;

    public String getGitlabUrl() {
        return Strings.emptyToNull(gitlabUrl);
    }

    public void setGitlabUrl(String gitlabUrl) {
        this.gitlabUrl = gitlabUrl;
    }

    public String getGitlabAuthToken() {
        return Strings.emptyToNull(gitlabAuthToken);
    }

    public void setGitlabAuthToken(String gitlabAuthToken) {
        this.gitlabAuthToken = gitlabAuthToken;
    }

    public Integer getGitlabProjectId() {
        return (gitlabProjectId == null || gitlabProjectId == 0) ? null : gitlabProjectId;
    }

    public void setGitlabProjectId(Integer gitlabProjectId) {
        this.gitlabProjectId = gitlabProjectId;
    }

    public void parseAndSetGitlabProjectId(String gitlabProjectId) {
        if (Strings.isNullOrEmpty(gitlabProjectId)) {
            this.gitlabProjectId = null;
        } else {
            final int asInt;
            try {
                asInt = Integer.parseInt(gitlabProjectId);
                this.gitlabProjectId = asInt;
            } catch (NumberFormatException ignored) {
                this.gitlabProjectId = null;
            }
        }
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
