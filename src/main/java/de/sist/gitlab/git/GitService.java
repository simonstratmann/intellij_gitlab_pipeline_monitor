package de.sist.gitlab.git;

import com.intellij.dvcs.repo.VcsRepositoryManager;
import com.intellij.dvcs.repo.VcsRepositoryMappingListener;
import com.intellij.openapi.project.Project;
import git4idea.GitUtil;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryChangeListener;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class GitService {

    public GitService(Project project) {
        List<GitRepository> repositories = GitUtil.getRepositoryManager(project).getRepositories();
        if (repositories.isEmpty()) {
            //todo handle
            return;
        }
        project.getMessageBus().syncPublisher(GitInitListener.GIT_INITIALIZED).handle(repositories.get(0));

        project.getMessageBus().connect().subscribe(VcsRepositoryManager.VCS_REPOSITORY_MAPPING_UPDATED, new VcsRepositoryMappingListener() {
            @Override
            public void mappingChanged() {
                System.out.println();
                project.getMessageBus().syncPublisher(GitInitListener.GIT_INITIALIZED).handle(GitUtil.getRepositoryManager(project).getRepositories().get(0));
            }
        });
        project.getMessageBus().connect().subscribe(GitRepository.GIT_REPO_CHANGE, new GitRepositoryChangeListener() {
            @Override
            public void repositoryChanged(@NotNull GitRepository repository) {
                System.out.println(repository);
                project.getMessageBus().syncPublisher(GitInitListener.GIT_INITIALIZED).handle(GitUtil.getRepositoryManager(project).getRepositories().get(0));
            }
        });
    }


}
