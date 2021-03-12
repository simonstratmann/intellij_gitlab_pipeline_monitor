package de.sist.gitlab.git;

import com.intellij.dvcs.repo.VcsRepositoryManager;
import com.intellij.dvcs.repo.VcsRepositoryMappingListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import git4idea.GitUtil;
import git4idea.commands.Git;
import git4idea.commands.GitCommand;
import git4idea.commands.GitCommandResult;
import git4idea.commands.GitLineHandler;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryChangeListener;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class GitService {

    private static final Logger logger = Logger.getInstance(GitService.class);

    private GitRepository gitRepository;
    private final Project project;

    public GitService(Project project) {
        this.project = project;
        project.getMessageBus().syncPublisher(GitInitListener.GIT_INITIALIZED).handle(gitRepository);

        project.getMessageBus().connect().subscribe(VcsRepositoryManager.VCS_REPOSITORY_MAPPING_UPDATED, new VcsRepositoryMappingListener() {
            @Override
            public void mappingChanged() {
                gitRepository = GitUtil.getRepositoryManager(project).getRepositories().get(0);
                project.getMessageBus().syncPublisher(GitInitListener.GIT_INITIALIZED).handle(gitRepository);
            }
        });
        project.getMessageBus().connect().subscribe(GitRepository.GIT_REPO_CHANGE, new GitRepositoryChangeListener() {
            @Override
            public void repositoryChanged(@NotNull GitRepository repository) {
                gitRepository = GitUtil.getRepositoryManager(project).getRepositories().get(0);
                project.getMessageBus().syncPublisher(GitInitListener.GIT_INITIALIZED).handle(gitRepository);
            }
        });
        project.getMessageBus().connect().subscribe(GitInitListener.GIT_INITIALIZED, gitRepository -> {
            this.gitRepository = gitRepository;
        });
    }

    public String getCurrentHash() {
        final GitLineHandler gitLineHandler = new GitLineHandler(project, gitRepository.getRoot(), GitCommand.LOG);
        gitLineHandler.addParameters("--pretty=format:%h -n 1");

        return ApplicationManager.getApplication().runReadAction((Computable<String>) () -> {
            final GitCommandResult gitCommandResult = Git.getInstance().runCommand(gitLineHandler);
            String hash = gitCommandResult.getOutput().get(0).trim().replace("-n 1", "").trim();
            logger.debug("Found local hash: " + hash);
            return hash;
        });
    }

    public GitRepository getGitRepository() {
        List<GitRepository> repositories = GitUtil.getRepositoryManager(project).getRepositories();
        if (repositories.isEmpty()) {
            logger.error("No GitRepositories");
            return null;
        }
        gitRepository = repositories.get(0);
        return gitRepository;
    }

}
