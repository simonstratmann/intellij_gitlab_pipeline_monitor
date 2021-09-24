package de.sist.gitlab.pipelinemonitor;

import com.google.common.base.Joiner;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import de.sist.gitlab.pipelinemonitor.config.ConfigProvider;
import de.sist.gitlab.pipelinemonitor.config.Mapping;
import de.sist.gitlab.pipelinemonitor.config.PipelineViewerConfigApp;
import de.sist.gitlab.pipelinemonitor.git.GitService;
import git4idea.GitLocalBranch;
import git4idea.repo.GitRepository;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class PipelineFilter {

    private static final Logger logger = Logger.getInstance(PipelineFilter.class);

    private static final int TAG_INTERVAL = 180;
    private final ConfigProvider config;
    private final Project project;
    private final GitService gitService;
    private PipelineJobStatus latestShown;
    private final Map<GitRepository, Instant> lastTagUpdate = new HashMap<>();
    private final Map<GitRepository, List<String>> repoToTags = new HashMap<>();

    public PipelineFilter(Project project) {
        config = ConfigProvider.getInstance();

        this.project = project;
        gitService = ServiceManager.getService(project, GitService.class);
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
            final boolean outdated = lastTagUpdate.computeIfAbsent(gitRepository, x -> Instant.MIN.plusSeconds(TAG_INTERVAL)).isBefore(Instant.now().minusSeconds(TAG_INTERVAL));
            if (outdated) {
                repoToTags.put(gitRepository, gitService.getTags(gitRepository));
                lastTagUpdate.put(gitRepository, Instant.now());
            }
            tags.addAll(repoToTags.get(gitRepository));
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Will retain branches that are contained in these checked out branches: ", Joiner.on(", ").join(trackedBranches));
        }
        final List<PipelineJobStatus> statuses = toFilter.stream().filter(x -> {
                    if (config.getBranchesToIgnore(project).contains(x.branchName)) {
                        logger.debug("Pipeline for branch ", x.branchName, " is ignored and will be filtered out");
                        return false;
                    }
                    if (trackedBranches.contains(x.branchName)) {
                        logger.debug("Pipeline for branch ", x.branchName, " is tracked locally and will be retained");
                        return true;
                    }
                    if (config.getBranchesToWatch(project).contains(x.branchName) && (!forNotification || config.isShowNotificationForWatchedBranches())) {
                        logger.debug("Pipeline for branch ", x.branchName, " is in list of branches to watch and will be retained");
                        return true;
                    }
                    if (tags.contains(x.branchName)) {
                        logger.debug("Pipeline for ref ", x.branchName, " is in the list of tags and will be retained");
                        return true;
                    }
                    logger.debug("Pipeline for branch ", x.branchName, " will be filtered out");
                    return false;
                }
        ).collect(Collectors.toList());
        if (!statuses.isEmpty() && !forNotification) {
            latestShown = statuses.get(0);
        }
        if (logger.isDebugEnabled()) {
            final String pipelineBranchNames = statuses.stream().map(PipelineJobStatus::getBranchName).collect(Collectors.joining(", "));
            logger.debug(String.format("Filtered %d out of %d pipelines %s", statuses.size(), toFilter.size(), forNotification ? "for notifications:" : "for display:"), pipelineBranchNames);
        }

        return statuses;
    }

    public PipelineJobStatus getLatestShown() {
        return latestShown;
    }
}
