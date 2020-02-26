package de.sist.gitlab.config;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@State(name = "PipelineViewerConfig", storages = {@Storage("PipelineViewerConfig.xml")})
public class PipelineViewerConfig implements PersistentStateComponent<PipelineViewerConfig> {

    private String gitlabUrl;
    private String gitlabAuthToken;
    private Integer gitlabProjectId;
    private List<String> branchesToIgnore = new ArrayList<>();
    private List<String> branchesToWatch = new ArrayList<>();
    private List<String> statusesToWatch = new ArrayList<>();
    private String showLightsForBranch;

    public String getGitlabUrl() {
        return gitlabUrl;
    }

    public void setGitlabUrl(String gitlabUrl) {
        this.gitlabUrl = gitlabUrl;
    }

    public String getGitlabAuthToken() {
        return gitlabAuthToken;
    }

    public void setGitlabAuthToken(String gitlabAuthToken) {
        this.gitlabAuthToken = gitlabAuthToken;
    }

    public Integer getGitlabProjectId() {
        return gitlabProjectId;
    }

    public void setGitlabProjectId(Integer gitlabProjectId) {
        this.gitlabProjectId = gitlabProjectId;
    }

    public List<String> getBranchesToIgnore() {
        return branchesToIgnore;
    }

    public void setBranchesToIgnore(List<String> branchesToIgnore) {
        this.branchesToIgnore = branchesToIgnore;
    }

    @NotNull
    public List<String> getBranchesToWatch() {
        return branchesToWatch;
    }

    public void setBranchesToWatch(List<String> branchesToWatch) {
        this.branchesToWatch = branchesToWatch;
    }

    public List<String> getStatusesToWatch() {
        return statusesToWatch;
    }

    public void setStatusesToWatch(List<String> statusesToWatch) {
        this.statusesToWatch = statusesToWatch;
    }

    public String getShowLightsForBranch() {
        return showLightsForBranch;
    }

    public void setShowLightsForBranch(String showLightsForBranch) {
        this.showLightsForBranch = showLightsForBranch;
    }

    public void initIfNeeded() {
        if (gitlabProjectId == null) {
            setStatusesToWatch(new ArrayList<>());
            setBranchesToWatch(new ArrayList<>());
            setBranchesToIgnore(new ArrayList<>());

            statusesToWatch.add("success");
            statusesToWatch.add("failed");
            statusesToWatch.add("skipped");
            statusesToWatch.add("canceled");
        }
    }

    @Nullable
    @Override
    public PipelineViewerConfig getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull PipelineViewerConfig state) {
        XmlSerializerUtil.copyBean(state, this);
    }

    public static PipelineViewerConfig getInstance(Project project) {
        return ServiceManager.getService(project, PipelineViewerConfig.class);
    }
}
