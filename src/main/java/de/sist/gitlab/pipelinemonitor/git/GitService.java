package de.sist.gitlab.pipelinemonitor.git;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.dvcs.repo.VcsRepositoryManager;
import com.intellij.dvcs.repo.VcsRepositoryMappingListener;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.MessageBus;
import de.sist.gitlab.pipelinemonitor.ReloadListener;
import de.sist.gitlab.pipelinemonitor.config.ConfigProvider;
import de.sist.gitlab.pipelinemonitor.config.Mapping;
import git4idea.GitLocalBranch;
import git4idea.GitRemoteBranch;
import git4idea.GitUtil;
import git4idea.commands.Git;
import git4idea.commands.GitCommand;
import git4idea.commands.GitCommandResult;
import git4idea.commands.GitLineHandler;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryChangeListener;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.util.List;
import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class GitService {

    private static final Logger logger = Logger.getInstance(Logger.class);

    private List<GitRepository> allGitRepositories = new ArrayList<>();
    private List<GitRepository> nonIgnoredRepositories = new ArrayList<>();
    private final Project project;
    private final MessageBus messageBus;

    public GitService(Project project) {
        this.project = project;
        messageBus = project.getMessageBus();
        project.getMessageBus().connect().subscribe(VcsRepositoryManager.VCS_REPOSITORY_MAPPING_UPDATED, (VcsRepositoryMappingListener) () -> {
            logger.debug("Retrieved event VCS_REPOSITORY_MAPPING_UPDATED");
            fireGitEventIfReposChanged();
        });
        project.getMessageBus().connect().subscribe(GitRepository.GIT_REPO_CHANGE, (GitRepositoryChangeListener) repository -> {
            logger.debug("Retrieved event GIT_REPO_CHANGE");
            fireGitEventIfReposChanged();
        });
        project.getMessageBus().connect().subscribe(ReloadListener.RELOAD, (ReloadListener) pipelineInfos -> fireGitEventIfReposChanged());
    }

    private void fireGitEventIfReposChanged() {
        final List<GitRepository> newAllGitRepositories = GitUtil.getRepositoryManager(project).getRepositories();
        final List<GitRepository> newNonIgnoredGitRepositories = filterNonIgnoredRepos(newAllGitRepositories);

        if (!allGitRepositories.equals(newAllGitRepositories) || !nonIgnoredRepositories.equals(newNonIgnoredGitRepositories)) {
            allGitRepositories = newAllGitRepositories;
            nonIgnoredRepositories = newNonIgnoredGitRepositories;
            logger.debug("Firing event GIT_INITIALIZED. Number of git repositories: ", newAllGitRepositories.size(), ". Non-ignored: ", newNonIgnoredGitRepositories.size());

            if (!messageBus.isDisposed()) {
                messageBus.syncPublisher(GitInitListener.GIT_INITIALIZED).handle(allGitRepositories);
            }
        } else {
            logger.debug("No new git repositories found. Number of git repositories: ", newAllGitRepositories.size(), ". Non-ignored: ", newNonIgnoredGitRepositories.size());
        }
    }

    private List<GitRepository> filterNonIgnoredRepos(List<GitRepository> gitRepositories) {
        return gitRepositories.stream().filter(x ->
                x.getRemotes().stream().anyMatch(gitRemote ->
                        gitRemote.getUrls().stream().noneMatch(url -> ConfigProvider.getInstance().getIgnoredRemotes().contains(url)))

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
        if (project.isDisposed()) {
            return Collections.emptyList();
        }
        allGitRepositories = GitUtil.getRepositoryManager(project).getRepositories();
        return allGitRepositories;
    }

    public GitRepository guessCurrentRepository() {
        return DvcsUtil.guessWidgetRepository(project, GitUtil.getRepositoryManager(project), null, (VirtualFile) null);
    }

    public void copyHashToClipboard() {
        ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
            @Override
            public void run() {

                final GitLineHandler gitLineHandler = new GitLineHandler(project, guessCurrentRepository().getRoot(), GitCommand.LOG);
                gitLineHandler.addParameters("--pretty=format:%h -n 1");

                final GitCommandResult gitCommandResult = Git.getInstance().runCommand(gitLineHandler);
                String hash = gitCommandResult.getOutput().get(0).trim().replace("-n 1", "").trim();
                logger.debug("Found local hash: ", hash);
                Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                clipboard.setContents(new StringSelection(hash), null);
            }
        });
    }

    public GitRepository getRepositoryByRemoteUrl(String url) {
        return getAllGitRepositories().stream().filter(repo -> repo.getRemotes().stream().anyMatch(remote -> remote.getUrls().stream().anyMatch(x -> x.equals(url)))).findFirst().orElse(null);
    }

    public @NotNull List<String> getTags(GitRepository gitRepository) {
        logger.debug("Loading tags for ", gitRepository);
        if (gitRepository == null) {
            logger.warn("GitRepository is null");
            return Collections.emptyList();
        }
        final VirtualFile root = gitRepository.getRoot();
        final Future<List<String>> future = ApplicationManager.getApplication().executeOnPooledThread(() -> getAllTags(project, root));
        try {
            return future.get(3, TimeUnit.SECONDS);
        } catch (Exception e) {
            logger.info("Error loading tags", e);
            return Collections.emptyList();
        }
    }

    private static List<String> getAllTags(@NotNull Project project, @NotNull VirtualFile root) throws VcsException {
        GitLineHandler h = new GitLineHandler(project, root, GitCommand.TAG);
        h.addParameters("-l");
        h.addParameters("--sort=-creatordate");
        h.setSilent(true);

        List<String> tags = new ArrayList<>();
        h.addLineListener((line, outputType) -> {
            if (outputType != ProcessOutputTypes.STDOUT) {
                return;
            }
            if (line.length() != 0) {
                tags.add(line);
            }
        });

        GitCommandResult result = Git.getInstance().runCommandWithoutCollectingOutput(h);
        result.throwOnError();

        return tags;
    }

    public Set<String> getTrackedBranches(Mapping mapping) {
        final Set<String> trackedBranches = new HashSet<>();
        GitRepository gitRepository = getRepositoryByRemoteUrl(mapping.getRemote());
        if (gitRepository == null) {
            //Can happen during shutdown or in other edge cases. Not much we can do
            return Collections.emptySet();
        }
        for (GitLocalBranch localBranch : gitRepository.getBranches().getLocalBranches()) {
            if (localBranch.findTrackedBranch(gitRepository) != null) {
                trackedBranches.add(localBranch.getName());
            }
        }
        return trackedBranches;
    }

    public Set<String> getRemoteBranches(Mapping mapping) {
        GitRepository gitRepository = getRepositoryByRemoteUrl(mapping.getRemote());
        if (gitRepository == null) {
            //Can happen during shutdown or in other edge cases. Not much we can do
            return Collections.emptySet();
        }
        return gitRepository.getBranches().getRemoteBranches().stream()
                .map(GitRemoteBranch::getNameForRemoteOperations)
                .collect(Collectors.toSet());
    }

}
