package de.sist.gitlab.git;

import com.intellij.dvcs.repo.VcsRepositoryManager;
import com.intellij.dvcs.repo.VcsRepositoryMappingListener;
import com.intellij.openapi.project.Project;
import de.sist.gitlab.config.ConfigProvider;
import git4idea.GitUtil;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryChangeListener;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class GitService {

    private List<GitRepository> allGitRepositories = new ArrayList<>();
    private List<GitRepository> nonIgnoredRepositories = new ArrayList<>();
    private final Project project;

    public GitService(Project project) {
        this.project = project;
        project.getMessageBus().connect().subscribe(VcsRepositoryManager.VCS_REPOSITORY_MAPPING_UPDATED, new VcsRepositoryMappingListener() {
            @Override
            public void mappingChanged() {
                fireGitEventIfReposChanged();
            }
        });
        project.getMessageBus().connect().subscribe(GitRepository.GIT_REPO_CHANGE, new GitRepositoryChangeListener() {
            @Override
            public void repositoryChanged(@NotNull GitRepository repository) {
                fireGitEventIfReposChanged();
            }
        });
    }

    private void fireGitEventIfReposChanged() {
        final List<GitRepository> newAllGitRepositories = GitUtil.getRepositoryManager(project).getRepositories();
        final List<GitRepository> newNonIgnoredGitRepositories = filterNonIgnoredRepos(newAllGitRepositories);

        if (!allGitRepositories.equals(newAllGitRepositories) || !nonIgnoredRepositories.equals(newNonIgnoredGitRepositories)) {
            allGitRepositories = newAllGitRepositories;
            nonIgnoredRepositories = newNonIgnoredGitRepositories;
            project.getMessageBus().syncPublisher(GitInitListener.GIT_INITIALIZED).handle(allGitRepositories);
        }
    }

    private List<GitRepository> filterNonIgnoredRepos(List<GitRepository> gitRepositories) {
        return gitRepositories.stream().filter(x -> x.getRemotes().stream().noneMatch(gitRemote -> gitRemote.getUrls().stream().anyMatch(url -> ConfigProvider.getInstance().getIgnoredRemotes().contains(url)))).collect(Collectors.toList());
    }

    public List<GitRepository> getNonIgnoredRepositories() {
        return nonIgnoredRepositories;
    }

    public void reloadGitRepositories() {
        fireGitEventIfReposChanged();
    }

    public List<GitRepository> getAllGitRepositories() {
        allGitRepositories = GitUtil.getRepositoryManager(project).getRepositories();
        return allGitRepositories;
    }

}
