package de.sist.gitlab.pipelinemonitor;

import com.google.common.base.Stopwatch;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import de.sist.gitlab.pipelinemonitor.config.ConfigChangedListener;
import de.sist.gitlab.pipelinemonitor.config.ConfigProvider;
import de.sist.gitlab.pipelinemonitor.config.PipelineViewerConfigApp;
import de.sist.gitlab.pipelinemonitor.config.PipelineViewerConfigProject;
import de.sist.gitlab.pipelinemonitor.git.GitInitListener;
import de.sist.gitlab.pipelinemonitor.git.GitService;
import de.sist.gitlab.pipelinemonitor.gitlab.GitlabService;
import de.sist.gitlab.pipelinemonitor.notifier.NotifierService;
import dev.failsafe.FailsafeException;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class BackgroundUpdateService {

    private static final Logger logger = Logger.getInstance(BackgroundUpdateService.class);

    private static final int INITIAL_DELAY = 0;
    private static final int UPDATE_DELAY = 30;
    final GitService gitService;
    final NotifierService notifierService;

    private boolean isActive = false;
    private boolean isRunning = false;
    private final Runnable backgroundTask;
    private ScheduledFuture<?> scheduledFuture;
    private final Project project;
    private final GitlabService gitlabService;
    private final MessageBus messageBus;
    private boolean connectionFailureReported;

    public BackgroundUpdateService(Project project) {
        this.project = project;

        gitlabService = project.getService(GitlabService.class);
        messageBus = project.getMessageBus();

        backgroundTask = () -> {
            if (project.isDisposed()) {
                return;
            }
            if (!PipelineViewerConfigProject.getInstance(project).isEnabled()) {
                stopBackgroundTask();
                return;
            }
            update(project, false);
        };

        final MessageBusConnection messageBusConnection = project.getMessageBus().connect();
        messageBusConnection.subscribe(GitInitListener.GIT_INITIALIZED, (GitInitListener) gitRepository -> {
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
        messageBusConnection.subscribe(ConfigChangedListener.CONFIG_CHANGED, (ConfigChangedListener) () -> {
            if (!PipelineViewerConfigProject.getInstance(project).isEnabled()) {
                logger.debug("Retrieved CONFIG_CHANGED event. Project is disabled. Stopping background task if needed");
                stopBackgroundTask();
            } else {
                logger.debug("Retrieved CONFIG_CHANGED event. Project is enabled. Starting background task if needed");
            }

        });
        gitService = project.getService(GitService.class);
        notifierService = project.getService(NotifierService.class);
    }

    public synchronized void update(Project project, boolean triggeredByUser) {
        if (isRunning) {
            return;
        }
        Runnable updateRunnable = () -> {
            Stopwatch stopwatch = Stopwatch.createStarted();
            getUpdateRunnable(triggeredByUser).run();
            //For some stupid reason the progress bar is not removed if the process returns too quickly
            if (stopwatch.elapsed(TimeUnit.MILLISECONDS) < 1000) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ignored) {
                }
            }
        };
        if (PipelineViewerConfigApp.getInstance().isShowProgressBar()) {

            Task.Backgroundable updateTask = new Task.Backgroundable(project, "Loading gitLab pipelines", false) {
                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    try {
                        updateRunnable.run();
                    } finally {
                        indicator.stop();
                    }
                }
            };

            final BackgroundableProcessIndicator updateProgressIndicator = new BackgroundableProcessIndicator(updateTask);
            ProgressManager.getInstance().runProcessWithProgressAsynchronously(updateTask, updateProgressIndicator);
        } else {
            ApplicationManager.getApplication().executeOnPooledThread(updateRunnable);
        }


    }

    private Runnable getUpdateRunnable(boolean triggeredByUser) {
        return () -> {
            if (isRunning || project.isDisposed()) {
                return;
            }
            isRunning = true;
            try {
                logger.debug("Starting IntelliJ background task", (triggeredByUser ? " triggered by user" : ""));
                gitlabService.checkForUnmappedRemotes(triggeredByUser);
                gitlabService.updatePipelineInfos(triggeredByUser);
                gitlabService.updateFromGraphQl();
                if (!messageBus.isDisposed()) {
                    messageBus.syncPublisher(ReloadListener.RELOAD).reload(gitlabService.getPipelineInfos());
                }
                connectionFailureReported = false;
                logger.debug("Finished IntelliJ background task");
            } catch (FailsafeException | IOException e) {
                logger.info("Connection error: " + e.getMessage(), e);
                if (ConfigProvider.getInstance().isShowConnectionErrorNotifications()) {
                    if (!connectionFailureReported || triggeredByUser) {
                        logger.debug("Showing notification for first connection error after a successful connection");
                        notifierService.showError("Unable to connect to gitlab: " + e);
                        connectionFailureReported = true;
                    } else {
                        logger.debug("Not notification for connection error because one was already shown");
                    }
                }
            } finally {
                isRunning = false;
            }
        };
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
        logger.debug("Background task cancelled: ", cancelled);
    }

    public synchronized boolean isActive() {
        return isActive;
    }

    public synchronized void restartBackgroundTask() {
        logger.debug("Restarting background task");
        if (isActive) {
            boolean cancelled = scheduledFuture.cancel(false);
            isActive = !cancelled;
            logger.debug("Background task cancelled: ", cancelled);
        }
        scheduledFuture = AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay(backgroundTask, INITIAL_DELAY, UPDATE_DELAY, TimeUnit.SECONDS);
        isActive = true;
    }

}
