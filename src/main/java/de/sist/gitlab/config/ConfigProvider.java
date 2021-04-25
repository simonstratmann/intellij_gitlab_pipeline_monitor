
package de.sist.gitlab.config;

import com.google.common.base.Strings;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
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
        return ServiceManager.getService(ConfigProvider.class);
    }


}
