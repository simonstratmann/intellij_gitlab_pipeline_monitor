// (C) 2021 PPI AG
package de.sist.gitlab.config;

import com.google.common.base.Strings;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

/**
 * @author PPI AG
 */
public class ConfigProvider {

    public String getGitlabUrl(Project project) {
        final String gitlabUrl = PipelineViewerConfigProject.getInstance(project).getGitlabUrl();
        return !Strings.isNullOrEmpty(gitlabUrl) ? gitlabUrl : PipelineViewerConfigApp.getInstance().getGitlabUrl();
    }


    public String getGitlabAuthToken(Project project) {
        final String gitlabUrl = PipelineViewerConfigProject.getInstance(project).getGitlabAuthToken();
        return !Strings.isNullOrEmpty(gitlabUrl) ? gitlabUrl : PipelineViewerConfigApp.getInstance().getGitlabAuthToken();
    }


    public List<Mapping> getMappings(Project project) {
        return PipelineViewerConfigApp.getInstance().getMappings();
    }

    public Optional<Mapping> getMappingByRemote(Project project, String remote) {
        return getMappings(project).stream().filter(x -> x.getRemote().equals(remote)).findFirst();
    }

    public Optional<Mapping> getMappingByProjectId(Project project, String projectId) {
        return getMappings(project).stream().filter(x -> x.getGitlabProjectId().equals(projectId)).findFirst();
    }


    public List<String> getBranchesToIgnore(Project project) {
        return PipelineViewerConfigProject.getInstance(project).getBranchesToIgnore();
    }


    @NotNull
    public List<String> getBranchesToWatch(Project project) {
        return PipelineViewerConfigProject.getInstance(project).getBranchesToWatch();
    }


    public String getShowLightsForBranch(Project project) {
        return PipelineViewerConfigProject.getInstance(project).getShowLightsForBranch();
    }


    public String getMergeRequestTargetBranch(Project project) {
        final String value = PipelineViewerConfigProject.getInstance(project).getMergeRequestTargetBranch();
        return !Strings.isNullOrEmpty(value) ? value : PipelineViewerConfigApp.getInstance().getMergeRequestTargetBranch();
    }


    public boolean isShowNotificationForWatchedBranches() {
        return PipelineViewerConfigApp.getInstance().isShowNotificationForWatchedBranches();
    }


    public boolean isShowConnectionErrorNotifications() {
        return PipelineViewerConfigApp.getInstance().isShowConnectionErrorNotifications();
    }

    public static ConfigProvider getInstance() {
        return ServiceManager.getService(ConfigProvider.class);
    }


}
