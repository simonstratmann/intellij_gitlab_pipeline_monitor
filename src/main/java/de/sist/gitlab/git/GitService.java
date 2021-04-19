package de.sist.gitlab.git;

import com.intellij.dvcs.repo.VcsRepositoryManager;
import com.intellij.dvcs.repo.VcsRepositoryMappingListener;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import de.sist.gitlab.config.PipelineViewerConfigApp;
import git4idea.GitUtil;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryChangeListener;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class GitService {

    private static final Logger logger = Logger.getInstance(GitService.class);

    private List<GitRepository> gitRepositories = new ArrayList<>();
    private final Project project;

    public GitService(Project project) {
        this.project = project;
        project.getMessageBus().connect().subscribe(VcsRepositoryManager.VCS_REPOSITORY_MAPPING_UPDATED, new VcsRepositoryMappingListener() {
            @Override
            public void mappingChanged() {
                fireGitEventIfReposChanged(project);
            }
        });
        project.getMessageBus().connect().subscribe(GitRepository.GIT_REPO_CHANGE, new GitRepositoryChangeListener() {
            @Override
            public void repositoryChanged(@NotNull GitRepository repository) {
                fireGitEventIfReposChanged(project);
            }
        });
    }

    private void fireGitEventIfReposChanged(Project project) {
        final List<GitRepository> newRepoList = getNonIgnoredRepos(GitUtil.getRepositoryManager(project).getRepositories());
        if (!gitRepositories.equals(newRepoList)) {
            gitRepositories = newRepoList;
            project.getMessageBus().syncPublisher(GitInitListener.GIT_INITIALIZED).handle(gitRepositories);
        }
    }

    private List<GitRepository> getNonIgnoredRepos(List<GitRepository> gitRepositories) {
        return gitRepositories.stream().filter(x -> x.getRemotes().stream().noneMatch(gitRemote -> gitRemote.getUrls().stream().anyMatch(url -> PipelineViewerConfigApp.getInstance().getIgnoredRemotes().contains(url)))).collect(Collectors.toList());
    }


    public List<GitRepository> getGitRepositories() {
        if (gitRepositories.isEmpty()) {
            gitRepositories = GitUtil.getRepositoryManager(project).getRepositories();
            gitRepositories = getNonIgnoredRepos(gitRepositories);
        }
        return gitRepositories;
    }

}
