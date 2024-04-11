
package de.sist.gitlab.pipelinemonitor.config;

import com.google.common.base.Strings;
import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import de.sist.gitlab.pipelinemonitor.gitlab.GitlabService;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 *
 */
public class ConfigProvider {

    private static final com.intellij.openapi.diagnostic.Logger logger = Logger.getInstance(ConfigProvider.class);

    private boolean isConfigOpen;
    private final Lock lock = new ReentrantLock();
    private static final Lock saveLock = new ReentrantLock();
    private final Condition configOpenCondition = lock.newCondition();

    /**
     * It may happen that with the config open the dialog for untracked remotes is opened. The user chooses to monitor something but the open log dialog is not updated.
     * The user applies the config and the list of tracked remotes is reset, resulting in a loop. Therefore we use this class to track if the config is open, not allowing
     * any dialogs to be opened for that time.
     */
    public void aquireLock() {
        lock.lock();
        try {
            if (isConfigOpen) {
                logger.debug("Config open, waiting");
                try {
                    configOpenCondition.await();
                    logger.debug("Config closed now, continuing");
                } catch (InterruptedException e) {
                    logger.error("Error while aquiring config lock", e);
                }
            } else {
                logger.debug("Config not open, not waiting");
            }
        } finally {
            lock.unlock();
        }
    }

    public boolean isConfigOpen() {
        return isConfigOpen;
    }

    public void setConfigOpen() {
        logger.debug("Setting config open");
        lock.lock();
        isConfigOpen = true;
        lock.unlock();
    }

    public synchronized void setConfigClosed() {
        logger.debug("Setting config closed, signalling waiting threads");
        lock.lock();
        isConfigOpen = false;
        configOpenCondition.signalAll();
        lock.unlock();
    }

    public List<Mapping> getMappings() {
        return PipelineViewerConfigApp.getInstance().getMappings();
    }

    public Mapping getMappingByRemoteUrl(String remote) {
        return getMappings().stream().filter(x -> x.getRemote().equals(remote)).findFirst().orElse(null);
    }

    public Mapping getMappingByProjectId(String projectId) {
        return getMappings().stream().filter(x -> x.getGitlabProjectId().equals(projectId)).findFirst().orElse(null);
    }

    public List<String> getBranchesToIgnore(Project project) {
        return PipelineViewerConfigProject.getInstance(project).getBranchesToIgnore();
    }

    public List<String> getIgnoredRemotes() {
        return PipelineViewerConfigApp.getInstance().getIgnoredRemotes();
    }

    @NotNull
    public List<String> getBranchesToWatch(Project project) {
        return PipelineViewerConfigProject.getInstance(project).getBranchesToWatch();
    }

    public String getShowLightsForBranch(Project project) {
        return PipelineViewerConfigProject.getInstance(project).getShowLightsForBranch();
    }

    public int getConnectTimeoutSeconds() {
        return PipelineViewerConfigApp.getInstance().getConnectTimeout();
    }

    public String getMergeRequestTargetBranch(Project project) {
        final String value = PipelineViewerConfigProject.getInstance(project).getMergeRequestTargetBranch();
        return !Strings.isNullOrEmpty(value) ? value : PipelineViewerConfigApp.getInstance().getMergeRequestTargetBranch();
    }

    public boolean isShowNotificationForWatchedBranches() {
        return PipelineViewerConfigApp.getInstance().isShowNotificationForWatchedBranches();
    }

    public boolean isShowConnectionErrorNotifications() {
        return PipelineViewerConfigApp.getInstance().isShowConnectionErrorNotifications();
    }

    public static ConfigProvider getInstance() {
        return ApplicationManager.getApplication().getService(ConfigProvider.class);
    }

    public static void saveToken(Mapping mapping, String token, TokenType tokenType, Project project) {
        new Task.Backgroundable(project, "Saving token") {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                saveLock.lock();
                final String passwordKey = tokenType == TokenType.PERSONAL ? mapping.getHost() : mapping.getRemote();
                logger.debug("Saving token with length ", (token == null ? 0 : token.length()), (tokenType == TokenType.PERSONAL ? " for host " : " for remote "), passwordKey);
                final CredentialAttributes credentialAttributes = new CredentialAttributes(GitlabService.ACCESS_TOKEN_CREDENTIALS_ATTRIBUTE + passwordKey, passwordKey);
                PasswordSafe.getInstance().setPassword(credentialAttributes, token == null ? null : Strings.emptyToNull(token));
                if (tokenType == TokenType.PERSONAL) {
                    //Delete token saved for this remote as it's now superseded by the personal access token
                    PasswordSafe.getInstance().setPassword(new CredentialAttributes(GitlabService.ACCESS_TOKEN_CREDENTIALS_ATTRIBUTE + mapping.getRemote(), mapping.getRemote()), null);
                }
                saveLock.unlock();
            }
        }.queue();
    }

    public static String getToken(Mapping mapping) {
        return getToken(mapping.getRemote(), mapping.getHost());
    }

    public static String getToken(String remote, String host) {
        return getTokenAndType(remote, host).getLeft();
    }

    public static Pair<String, TokenType> getTokenAndType(String remote, String host) {
        saveLock.lock();
        TokenType tokenType;
        final CredentialAttributes tokenCA = new CredentialAttributes(GitlabService.ACCESS_TOKEN_CREDENTIALS_ATTRIBUTE + remote, remote);
        String token = PasswordSafe.getInstance().getPassword(tokenCA);

        if (!Strings.isNullOrEmpty(token)) {
            tokenType = TokenType.PROJECT;
        } else {
            //Didn't find a remote on token level, try host
            final CredentialAttributes hostCA = new CredentialAttributes(GitlabService.ACCESS_TOKEN_CREDENTIALS_ATTRIBUTE + host, host);
            token = PasswordSafe.getInstance().getPassword(hostCA);
            tokenType = TokenType.PERSONAL;
        }
        if (Strings.isNullOrEmpty(token)) {
            logger.debug("Found no token for remote ", remote, (host == null ? ":" : " and host " + host));
            tokenType = null;
        } else {
            logger.debug("Found token with length ", token.length(), " for remote ", remote, (host == null ? ":" : " and host " + host));
        }
        saveLock.unlock();
        return Pair.of(token, tokenType);
    }

    public static boolean isNotEqualIgnoringEmptyOrNull(String a, String b) {
        return !Strings.nullToEmpty(a).equals(Strings.nullToEmpty(b));
    }


}
