package de.sist.gitlab;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import de.sist.gitlab.config.ConfigProvider;
import de.sist.gitlab.config.Mapping;
import de.sist.gitlab.config.PipelineViewerConfigApp;
import de.sist.gitlab.git.GitService;
import git4idea.GitLocalBranch;
import git4idea.repo.GitRepository;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@SuppressWarnings("RedundantIfStatement")
public class PipelineFilter {

    private final ConfigProvider config;
    private final Project project;
    private PipelineJobStatus latestShown;

    public PipelineFilter(Project project) {
        config = ConfigProvider.getInstance();

        this.project = project;
    }

    public List<PipelineJobStatus> filterPipelines(String projectId, List<PipelineJobStatus> toFilter, boolean forNotification) {
        final List<GitRepository> gitRepositories = ServiceManager.getService(project, GitService.class).getNonIgnoredRepositories();
        final Set<String> trackedBranches = new HashSet<>();
        final Mapping mapping = config.getMappings().stream().filter(x -> x.getGitlabProjectId().equals(projectId)).findFirst().orElseThrow(() -> new RuntimeException("Unable to find mapping for project ID " + projectId));

        final List<GitRepository> matchingRepositories = gitRepositories.stream().filter(x -> x.getRemotes().stream().anyMatch(remote -> remote.getUrls().stream().anyMatch(url -> url.equals(mapping.getRemote())))).collect(Collectors.toList());

        for (GitRepository gitRepository : matchingRepositories) {
            for (GitLocalBranch localBranch : gitRepository.getBranches().getLocalBranches()) {
                if (localBranch.findTrackedBranch(gitRepository) != null) {
                    trackedBranches.add(localBranch.getName());
                }
            }
        }

        final Set<String> tags = new HashSet<>();
        if (PipelineViewerConfigApp.getInstance().isShowForTags()) {
            tags.addAll(GitService.getInstance(project).getTags());
        }

        final List<PipelineJobStatus> statuses = toFilter.stream().filter(x -> {
                    if (config.getBranchesToIgnore(project).contains(x.branchName)) {
                        return false;
                    }
                    if (trackedBranches.contains(x.branchName)) {
                        return true;
                    }
                    if (config.getBranchesToWatch(project).contains(x.branchName) && (!forNotification || config.isShowNotificationForWatchedBranches())) {
                        return true;
                    }
                    if (tags.contains(x.branchName)) {
                        return true;
                    }

                    return false;
                }
        ).collect(Collectors.toList());
        if (!statuses.isEmpty() && !forNotification) {
            latestShown = statuses.get(0);
        }
        return statuses;
    }

    public PipelineJobStatus getLatestShown() {
        return latestShown;
    }
}
