package de.sist.gitlab.pipelinemonitor;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.AppExecutorUtil;
import de.sist.gitlab.pipelinemonitor.config.ConfigChangedListener;
import de.sist.gitlab.pipelinemonitor.config.ConfigProvider;
import de.sist.gitlab.pipelinemonitor.config.PipelineViewerConfigProject;
import de.sist.gitlab.pipelinemonitor.git.GitInitListener;
import de.sist.gitlab.pipelinemonitor.git.GitService;
import de.sist.gitlab.pipelinemonitor.gitlab.GitlabService;
import de.sist.gitlab.pipelinemonitor.notifier.NotifierService;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class BackgroundUpdateService {

    private static final Logger logger = Logger.getInstance(BackgroundUpdateService.class);

    private static final int INITIAL_DELAY = 0;
    private static final int UPDATE_DELAY = 30;

    private boolean isActive = false;
    private boolean isRunning = false;
    private final Runnable backgroundTask;
    private ScheduledFuture<?> scheduledFuture;
    private final Project project;

    public BackgroundUpdateService(Project project) {
        this.project = project;

        backgroundTask = () -> {
            if (!PipelineViewerConfigProject.getInstance(project).isEnabled()) {
                stopBackgroundTask();
                return;
            }
            update(project, false);
        };

        project.getMessageBus().connect().subscribe(GitInitListener.GIT_INITIALIZED, gitRepository -> {
            logger.debug("Retrieved GIT_INITIALIZED event. Starting background task if needed");
            //If the background task is started at once for some reason the progress indicator is never closed
            if (isActive) {
                logger.debug("Background task already running");
                return;
            }
            if (!PipelineViewerConfigProject.getInstance(this.project).isEnabled()) {
                return;
            }
            logger.debug("Starting background task");
            scheduledFuture = AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay(backgroundTask, 5, UPDATE_DELAY, TimeUnit.SECONDS);
            isActive = true;
        });
        project.getMessageBus().connect().subscribe(ConfigChangedListener.CONFIG_CHANGED, () -> {
            if (!PipelineViewerConfigProject.getInstance(project).isEnabled()) {
                logger.debug("Retrieved CONFIG_CHANGED event. Project is disabled. Stopping background task if needed");
                stopBackgroundTask();
            } else {
                logger.debug("Retrieved CONFIG_CHANGED event. Project is enabled. Starting background task if needed");
            }
        });
    }

    public synchronized void update(Project project, boolean triggeredByUser) {
        Task.Backgroundable updateTask = new Task.Backgroundable(project, "Loading gitLab pipelines", false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                if (isRunning) {
                    return;
                }
                isRunning = true;
                try {
                    logger.debug("Starting IntelliJ background task");
                    final GitlabService gitlabService = ServiceManager.getService(project, GitlabService.class);
                    gitlabService.updatePipelineInfos();
                    gitlabService.updateMergeRequests();
                    project.getMessageBus().syncPublisher(ReloadListener.RELOAD).reload(gitlabService.getPipelineInfos());
                    logger.debug("Finished IntelliJ background task");
                } catch (IOException e) {
                    logger.info("Connection error: " + e.getMessage());
                    if (ConfigProvider.getInstance().isShowConnectionErrorNotifications()) {
                        ServiceManager.getService(project, NotifierService.class).showError("Unable to connect to gitlab: " + e);
                    }
                } finally {
                    isRunning = false;
                    indicator.checkCanceled();
                }
            }
        };

        ApplicationManager.getApplication().invokeLater(() -> {
            ServiceManager.getService(project, GitlabService.class).checkForUnmappedRemotes(ServiceManager.getService(project, GitService.class).getAllGitRepositories(), triggeredByUser);
            final BackgroundableProcessIndicator updateProgressIndicator = new BackgroundableProcessIndicator(updateTask);
            ProgressManager.getInstance().runProcessWithProgressAsynchronously(updateTask, updateProgressIndicator);
        });

    }

    public synchronized boolean startBackgroundTask() {
        if (isActive) {
            logger.debug("Background task already running");
            return false;
        }
        if (!PipelineViewerConfigProject.getInstance(project).isEnabled()) {
            return false;
        }
        logger.debug("Starting background task");
        scheduledFuture = AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay(backgroundTask, INITIAL_DELAY, UPDATE_DELAY, TimeUnit.SECONDS);
        isActive = true;
        return true;
    }

    public synchronized void stopBackgroundTask() {
        if (!isActive) {
            logger.debug("Background task already stopped");
        }
        if (scheduledFuture == null) {
            //Should not happen but can happen... (don't know when)
            return;
        }
        logger.debug("Stopping background task");
        boolean cancelled = scheduledFuture.cancel(false);
        isActive = !cancelled;
        logger.debug("Background task cancelled: " + cancelled);
    }

    public synchronized boolean isActive() {
        return isActive;
    }

    public synchronized void restartBackgroundTask() {
        logger.debug("Restarting background task");
        if (isActive) {
            boolean cancelled = scheduledFuture.cancel(false);
            isActive = !cancelled;
            logger.debug("Background task cancelled: " + cancelled);
        }
        scheduledFuture = AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay(backgroundTask, INITIAL_DELAY, UPDATE_DELAY, TimeUnit.SECONDS);
        isActive = true;
    }

}
