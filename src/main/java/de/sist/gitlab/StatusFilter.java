package de.sist.gitlab;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import de.sist.gitlab.config.PipelineViewerConfig;
import de.sist.gitlab.git.GitInitListener;
import git4idea.GitReference;
import git4idea.branch.GitBranchesCollection;
import git4idea.repo.GitRepository;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@SuppressWarnings("RedundantIfStatement")
public class StatusFilter {

    private static final Logger logger = Logger.getInstance(StatusFilter.class);

    private final PipelineViewerConfig config;
    private GitRepository gitRepository;

    public StatusFilter(Project project) {
        config = PipelineViewerConfig.getInstance(project);
        project.getMessageBus().connect().subscribe(GitInitListener.GIT_INITIALIZED, gitRepository -> this.gitRepository = gitRepository);
    }

    public List<PipelineJobStatus> filterPipelines(List<PipelineJobStatus> toFilter) {
        if (gitRepository == null) {
            logger.debug("GitRepository not set");
            return Collections.emptyList();
        }
        GitBranchesCollection branches = gitRepository.getBranches();
        Set<String> trackedBranches = branches.getLocalBranches().stream().filter(x -> branches.getRemoteBranches().stream().anyMatch(remote -> remote.getNameForRemoteOperations().equals(x.getName()))).map(GitReference::getName).collect(Collectors.toSet());

        return toFilter.stream().filter(x -> {
            if (config.getBranchesToIgnore().contains(x.branchName)) {
                return false;
            }
            if (trackedBranches.contains(x.branchName)) {
                return true;
            }
                    if (config.getBranchesToWatch().contains(x.branchName)) {
                        return true;
                    }
                    return false;
                }
        ).collect(Collectors.toList());
    }


}
