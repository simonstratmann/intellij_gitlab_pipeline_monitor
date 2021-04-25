package de.sist.gitlab;

import com.intellij.openapi.project.Project;
import de.sist.gitlab.config.ConfigProvider;
import de.sist.gitlab.config.Mapping;
import de.sist.gitlab.config.PipelineViewerConfigApp;
import de.sist.gitlab.git.GitService;
import git4idea.GitLocalBranch;
import git4idea.repo.GitRepository;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@SuppressWarnings("RedundantIfStatement")
public class PipelineFilter {

    private final ConfigProvider config;
    private final Project project;
    final GitService gitService;
    private PipelineJobStatus latestShown;
    private Map<GitRepository, Instant> lastTagUpdate = new HashMap<>();
    private Map<GitRepository, List<String>> repoToTags = new HashMap<>();

    public PipelineFilter(Project project) {
        config = ConfigProvider.getInstance();

        this.project = project;
        gitService = GitService.getInstance(project);
    }

    public List<PipelineJobStatus> filterPipelines(Mapping mapping, List<PipelineJobStatus> toFilter, boolean forNotification) {
        final Set<String> trackedBranches = new HashSet<>();
        final Set<String> tags = new HashSet<>();

        GitRepository gitRepository = gitService.getRepositoryByRemoteUrl(mapping.getRemote());
        for (GitLocalBranch localBranch : gitRepository.getBranches().getLocalBranches()) {
            if (localBranch.findTrackedBranch(gitRepository) != null) {
                trackedBranches.add(localBranch.getName());
            }
        }
        if (PipelineViewerConfigApp.getInstance().isShowForTags()) {
            final boolean outdated = lastTagUpdate.computeIfAbsent(gitRepository, x -> Instant.MIN.plusSeconds(30)).isBefore(Instant.now().minusSeconds(30));
            if (outdated) {
                repoToTags.put(gitRepository, gitService.getTags(gitRepository));
                lastTagUpdate.put(gitRepository, Instant.now());
            }
            tags.addAll(repoToTags.get(gitRepository));
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
