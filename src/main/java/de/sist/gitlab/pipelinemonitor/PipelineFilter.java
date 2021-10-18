package de.sist.gitlab.pipelinemonitor;

import com.google.common.base.Joiner;
import com.intellij.openapi.components.ServiceManager;
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
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class PipelineFilter {

    private static final Logger logger = Logger.getInstance(PipelineFilter.class);

    private static final int TAG_INTERVAL = 180;
    private static final Pattern MR_REF_PATTERN = Pattern.compile(".*\\/(\\d*)\\/.*");
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
        final Set<String> tags = new HashSet<>();

        GitRepository gitRepository = gitService.getRepositoryByRemoteUrl(mapping.getRemote());
        final Set<String> trackedBranches = gitService.getTrackedBranches(mapping);
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
                    if (x.source.equals("merge_request_event")) {
                        logger.debug("Event source for pipeline with ", x.branchName, " is a merge request");
                        final Matcher matcher = MR_REF_PATTERN.matcher(x.branchName);
                        if (!matcher.matches()) {
                            logger.debug("Unable to find MR ID in " + x.source);
                            return false;
                        }
                        final String mergeRequestId = matcher.group(1);
                        final Optional<MergeRequest> matchingMergeRequest = mergeRequests.stream().filter(mr -> mr.getReference().equals(mergeRequestId)).findFirst();
                        if (matchingMergeRequest.isEmpty()) {
                            logger.debug("No merge request found with ID " + mergeRequestId);
                            return false;
                        }
                        final boolean mrBranchTracked = trackedBranches.contains(matchingMergeRequest.get().getSourceBranch());
                        if (!mrBranchTracked) {
                            logger.debug("Merge request ", matchingMergeRequest.get().getReference(), " with source branch ", matchingMergeRequest.get().getSourceBranch(), " does not match any locally tracked branches");
                            return false;
                        }
                        x.setBranchNameDisplay("MR: " + matchingMergeRequest.get().getSourceBranch());
                        x.mergeRequestLink = matchingMergeRequest.get().getWebUrl();
                        logger.debug("Merge request ", matchingMergeRequest.get().getReference(), " source branch ", matchingMergeRequest.get().getSourceBranch(), " matches a locally tracked branch");
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
