package de.sist.gitlab;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import de.sist.gitlab.config.ConfigProvider;
import de.sist.gitlab.git.GitInitListener;
import de.sist.gitlab.git.GitService;
import git4idea.GitReference;
import git4idea.branch.GitBranchesCollection;
import git4idea.repo.GitRepository;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@SuppressWarnings("RedundantIfStatement")
public class PipelineFilter {

    private final ConfigProvider config;
    private final Project project;
    private GitRepository gitRepository;

    public PipelineFilter(Project project) {
        config = ConfigProvider.getInstance();
        project.getMessageBus().connect().subscribe(GitInitListener.GIT_INITIALIZED, gitRepository -> {
            this.gitRepository = gitRepository;
        });
        this.project = project;
    }

    public List<PipelineJobStatus> filterPipelines(List<PipelineJobStatus> toFilter, boolean forNotification) {
        if (gitRepository == null) {
            gitRepository = ServiceManager.getService(project, GitService.class).getGitRepository();
            if (gitRepository == null) {
                return Collections.emptyList();
            }
        }
        GitBranchesCollection branches = gitRepository.getBranches();
        Set<String> trackedBranches = branches.getLocalBranches().stream()
                .filter(x -> branches.getRemoteBranches().stream().anyMatch(remote -> remote.getNameForRemoteOperations().equals(x.getName())))
                .map(GitReference::getName)
                .collect(Collectors.toSet());

        return toFilter.stream().filter(x -> {
            if (config.getBranchesToIgnore(project).contains(x.branchName)) {
                return false;
            }
            if (trackedBranches.contains(x.branchName)) {
                return true;
            }
            if (config.getBranchesToWatch(project).contains(x.branchName) && (!forNotification || config.isShowNotificationForWatchedBranches())) {
                return true;
            }

                    return false;
                }
        ).collect(Collectors.toList());
    }


}
