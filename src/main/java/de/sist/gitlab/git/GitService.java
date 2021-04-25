package de.sist.gitlab.git;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.dvcs.repo.VcsRepositoryManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import de.sist.gitlab.config.ConfigProvider;
import git4idea.GitUtil;
import git4idea.branch.GitBranchUtil;
import git4idea.commands.Git;
import git4idea.commands.GitCommand;
import git4idea.commands.GitCommandResult;
import git4idea.commands.GitLineHandler;
import git4idea.config.GitVcsSettings;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

public class GitService {

    private static final Logger logger = Logger.getInstance(Logger.class);

    private List<GitRepository> allGitRepositories = new ArrayList<>();
    private List<GitRepository> nonIgnoredRepositories = new ArrayList<>();
    private final Project project;

    public GitService(Project project) {
        this.project = project;
        project.getMessageBus().connect().subscribe(VcsRepositoryManager.VCS_REPOSITORY_MAPPING_UPDATED, () -> {
            logger.debug("Retrieved event VCS_REPOSITORY_MAPPING_UPDATED");
            fireGitEventIfReposChanged();
        });
        project.getMessageBus().connect().subscribe(GitRepository.GIT_REPO_CHANGE, repository -> {
            logger.debug("Retrieved event GIT_REPO_CHANGE");
            fireGitEventIfReposChanged();
        });
    }

    private void fireGitEventIfReposChanged() {
        final List<GitRepository> newAllGitRepositories = GitUtil.getRepositoryManager(project).getRepositories();
        final List<GitRepository> newNonIgnoredGitRepositories = filterNonIgnoredRepos(newAllGitRepositories);

        if (!allGitRepositories.equals(newAllGitRepositories) || !nonIgnoredRepositories.equals(newNonIgnoredGitRepositories)) {
            allGitRepositories = newAllGitRepositories;
            nonIgnoredRepositories = newNonIgnoredGitRepositories;
            logger.debug("Firing event GIT_INITIALIZED. Number of git repositories: " + newAllGitRepositories.size() + ". Non-ignored: " + newNonIgnoredGitRepositories.size());
            project.getMessageBus().syncPublisher(GitInitListener.GIT_INITIALIZED).handle(allGitRepositories);
        } else {
            logger.debug("No new git repositories found. Number of git repositories: " + newAllGitRepositories.size() + ". Non-ignored: " + newNonIgnoredGitRepositories.size());
        }
    }

    private List<GitRepository> filterNonIgnoredRepos(List<GitRepository> gitRepositories) {
        return gitRepositories.stream().filter(x ->
                x.getRemotes().stream().noneMatch(gitRemote ->
                        gitRemote.getUrls().stream().anyMatch(url -> ConfigProvider.getInstance().getIgnoredRemotes().contains(url)))

        ).collect(Collectors.toList());
    }

    public List<GitRepository> getNonIgnoredRepositories() {
        return nonIgnoredRepositories;
    }

    public void reloadGitRepositories() {
        logger.debug("Reloading git repositories");
        fireGitEventIfReposChanged();
    }

    public List<GitRepository> getAllGitRepositories() {
        allGitRepositories = GitUtil.getRepositoryManager(project).getRepositories();
        return allGitRepositories;
    }

    public GitRepository guessCurrentRepository() {
        final GitVcsSettings settings = GitVcsSettings.getInstance(project);
        return DvcsUtil.guessCurrentRepositoryQuick(project, GitUtil.getRepositoryManager(project), settings.getRecentRootPath());
    }

    public void copyHashToClipboard() {
        ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
            @Override
            public void run() {

                final GitLineHandler gitLineHandler = new GitLineHandler(project, guessCurrentRepository().getRoot(), GitCommand.LOG);
                gitLineHandler.addParameters("--pretty=format:%h -n 1");

                final GitCommandResult gitCommandResult = Git.getInstance().runCommand(gitLineHandler);
                String hash = gitCommandResult.getOutput().get(0).trim().replace("-n 1", "").trim();
                logger.debug("Found local hash: " + hash);
                Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                clipboard.setContents(new StringSelection(hash), null);
            }
        });
    }

    public @NotNull List<String> getTags() {
        try {
            return ApplicationManager.getApplication().executeOnPooledThread((Callable<List<String>>) () -> {
                try {
                    return GitBranchUtil.getAllTags(project, guessCurrentRepository().getRoot());
                } catch (VcsException e) {
                    logger.error("Unable to get all tags", e);
                    return Collections.emptyList();
                }
            }).get();
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Unable to get all tags", e);
            return Collections.emptyList();
        }


    }

    public static GitService getInstance(Project project) {
        return ServiceManager.getService(project, GitService.class);
    }

}
