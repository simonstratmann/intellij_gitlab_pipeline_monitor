// (C) 2021 PPI AG
package de.sist.gitlab.config;

import com.google.common.base.Strings;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author PPI AG
 */
public class ConfigProvider {

    public List<Mapping> getMappings() {
        return PipelineViewerConfigApp.getInstance().getMappings();
    }

    public Mapping getMappingByRemote(String remote) {
        return getMappings().stream().filter(x -> x.getRemote().equals(remote)).findFirst().orElse(null);
    }

    public Mapping getMappingByProjectId(String projectId) {
        return getMappings().stream().filter(x -> x.getGitlabProjectId().equals(projectId)).findFirst().orElse(null);
    }

    public List<String> getBranchesToIgnore(Project project) {
        return PipelineViewerConfigProject.getInstance(project).getBranchesToIgnore();
    }

    public List<String> getIgnoredRemotes() {
        return PipelineViewerConfigApp.getInstance().getIgnoredRemotes();
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
