package de.sist.gitlab.pipelinemonitor;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import de.sist.gitlab.pipelinemonitor.config.ConfigProvider;
import de.sist.gitlab.pipelinemonitor.config.Mapping;
import de.sist.gitlab.pipelinemonitor.config.PipelineViewerConfigApp;
import de.sist.gitlab.pipelinemonitor.git.GitService;
import de.sist.gitlab.pipelinemonitor.gitlab.GitlabService;
import de.sist.gitlab.pipelinemonitor.gitlab.mapping.MergeRequest;
import git4idea.repo.GitRepository;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
        gitService = project.getService(GitService.class);
    }

    public List<PipelineJobStatus> filterPipelines(Mapping mapping, List<PipelineJobStatus> toFilter, boolean forNotification) {
        Set<String> tags = new HashSet<>();

        GitRepository gitRepository = gitService.getRepositoryByRemoteUrl(mapping.getRemote());
        final Set<String> trackedBranches = gitService.getTrackedBranches(mapping);
        if (PipelineViewerConfigApp.getInstance().isShowForTags()) {
            final boolean outdated = lastTagUpdate.computeIfAbsent(gitRepository, x -> Instant.MIN.plusSeconds(TAG_INTERVAL)).isBefore(Instant.now().minusSeconds(TAG_INTERVAL));
            if (outdated || !repoToTags.containsKey(gitRepository)) {
                logger.debug("Tags outdated or not yet initialized");
                final List<String> allTags = gitService.getTags(gitRepository);
                repoToTags.put(gitRepository, allTags);
                lastTagUpdate.put(gitRepository, Instant.now());
            }
            final List<String> cachedTags = repoToTags.get(gitRepository);
            if (PipelineViewerConfigApp.getInstance().getMaxLatestTags() != null) {
                tags.addAll(cachedTags.subList(0, Math.min(PipelineViewerConfigApp.getInstance().getMaxLatestTags(), cachedTags.size())));
                logger.debug("Using the latest ", PipelineViewerConfigApp.getInstance().getMaxLatestTags(), " tags: ", tags);
            } else {
                tags.addAll(cachedTags);
                logger.debug("Using all ", tags.size(), " tags");
            }
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Will retain branches that are contained in these checked out branches: ", Joiner.on(", ").join(trackedBranches));
        }
        final List<MergeRequest> mergeRequests = project.getService(GitlabService.class).getMergeRequests();
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
            final Optional<MergeRequest> matchingMergeRequest = mergeRequests.stream()
                    .filter(mr -> mr.getHeadPipeline() != null && Objects.equals(mr.getHeadPipeline().getRef(), x.branchName))
                    .findFirst();
            if (matchingMergeRequest.isPresent()) {
                logger.debug("Branch with ref ", x.branchName, " matches MR ", matchingMergeRequest.get());
                final String prefix = Strings.nullToEmpty(PipelineViewerConfigApp.getInstance().getMrPipelinePrefix());
                if (PipelineViewerConfigApp.getInstance().getMrPipelineDisplayType() == PipelineViewerConfigApp.MrPipelineDisplayType.SOURCE_BRANCH) {
                    x.setBranchNameDisplay(prefix + matchingMergeRequest.get().getSourceBranch());
                } else {
                    x.setBranchNameDisplay(prefix + matchingMergeRequest.get().getTitle());
                }
                x.mergeRequestLink = matchingMergeRequest.get().getWebUrl();
                return true;
            }
                    logger.debug("Pipeline for branch ", x.branchName, " will be filtered out");
                    return false;
                }
        ).distinct().collect(Collectors.toList());
        if (!statuses.isEmpty() && !forNotification) {
            latestShown = statuses.get(0);
        }
        if (logger.isDebugEnabled()) {
            final String pipelineBranchNames = statuses.stream().map(PipelineJobStatus::getBranchName).distinct().collect(Collectors.joining(", "));
            logger.debug(String.format("Filtered %d out of %d pipelines %s", statuses.size(), toFilter.size(), forNotification ? "for notifications:" : "for display:"), pipelineBranchNames);
        }

        return statuses;
    }

    public PipelineJobStatus getLatestShown() {
        return latestShown;
    }
}
