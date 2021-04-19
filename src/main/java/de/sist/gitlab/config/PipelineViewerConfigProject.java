package de.sist.gitlab.config;

import com.google.common.base.Strings;
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

@State(name = "PipelineViewerConfigProject", storages = {@Storage("PipelineViewerConfig.xml")})
public class PipelineViewerConfigProject implements PersistentStateComponent<PipelineViewerConfigProject> {

    private String gitlabUrl;
    private String gitlabAuthToken;
    private Integer gitlabProjectId;
    private List<String> branchesToIgnore = new ArrayList<>();
    private List<String> branchesToWatch = new ArrayList<>();
    private String showLightsForBranch;
    private String mergeRequestTargetBranch = "master";


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

    public void setGitlabProjectId(String gitlabProjectId) {
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


    public String getShowLightsForBranch() {
        return Strings.emptyToNull(showLightsForBranch);
    }


    public void setShowLightsForBranch(String showLightsForBranch) {
        this.showLightsForBranch = showLightsForBranch;
    }


    public String getMergeRequestTargetBranch() {
        return Strings.emptyToNull(mergeRequestTargetBranch);
    }


    public void setMergeRequestTargetBranch(String mergeRequestTargetBranch) {
        this.mergeRequestTargetBranch = mergeRequestTargetBranch;
    }


    public @Nullable PipelineViewerConfigProject getState() {
        return this;
    }

    public void initIfNeeded() {
        if (gitlabProjectId == null) {
            setBranchesToWatch(new ArrayList<>());
            setBranchesToIgnore(new ArrayList<>());
        }
    }


    public void loadState(@NotNull PipelineViewerConfigProject state) {
        XmlSerializerUtil.copyBean(state, this);
    }

    public static PipelineViewerConfigProject getInstance(Project project) {
        return ServiceManager.getService(project, PipelineViewerConfigProject.class);
    }
}
