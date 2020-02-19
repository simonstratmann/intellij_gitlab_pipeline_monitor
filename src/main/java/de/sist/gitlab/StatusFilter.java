package de.sist.gitlab;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import de.sist.gitlab.config.PipelineViewerConfig;
import git4idea.GitReference;
import git4idea.branch.GitBranchUtil;
import git4idea.branch.GitBranchesCollection;
import git4idea.repo.GitRepository;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@SuppressWarnings("RedundantIfStatement")
public class StatusFilter {

    private final PipelineViewerConfig config;
    private final GitlabService gitlabService;
    private final Project project;
    private GitRepository gitRepository;

    public StatusFilter(Project project) {
        config = PipelineViewerConfig.getInstance(project);
        gitlabService = project.getService(GitlabService.class);
        this.project = project;
    }

    public List<PipelineJobStatus> filterPipelines(List<PipelineJobStatus> toFilter) {
        if (gitRepository == null) {
            gitRepository = ApplicationManager.getApplication().runReadAction((Computable<GitRepository>) () -> GitBranchUtil.getCurrentRepository(project));
        }

        GitBranchesCollection branches = gitRepository.getBranches();
        Set<String> trackedBranches = branches.getLocalBranches().stream().filter(x -> branches.getRemoteBranches().stream().anyMatch(remote -> remote.getNameForRemoteOperations().equals(x.getName()))).map(GitReference::getName).collect(Collectors.toSet());

        return toFilter.stream().filter(x -> {
            if (!config.getStatusesToWatch().contains(x.result)) {
                return false;
            }
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
