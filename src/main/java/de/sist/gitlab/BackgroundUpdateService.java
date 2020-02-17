package de.sist.gitlab;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.AppExecutorUtil;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class BackgroundUpdateService {

    private static final int INITIAL_DELAY = 0;
    private static final int UPDATE_DELAY = 30;
    Logger logger = Logger.getInstance(BackgroundUpdateService.class);

    private boolean isRunning = false;
    private final Runnable backgroundTask;
    private ScheduledFuture<?> scheduledFuture;

    public BackgroundUpdateService(Project project) {
        GitlabService gitlabService = project.getService(GitlabService.class);
        backgroundTask = () -> {
            try {
                List<PipelineJobStatus> statuses = gitlabService.getStatuses();
                project.getMessageBus().syncPublisher(ReloadListener.RELOAD).reload(statuses);
            } catch (IOException e) {
                stopBackgroundTask();
            }

        };
    }

    public synchronized void startBackgroundTask() {
        if (isRunning) {
            logger.debug("Background task already running");
            return;
        }
        logger.debug("Starting background task");
        scheduledFuture = AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay(backgroundTask, INITIAL_DELAY, UPDATE_DELAY, TimeUnit.SECONDS);
        isRunning = true;
    }

    public synchronized void stopBackgroundTask() {
        if (!isRunning) {
            logger.debug("Background task already stopped");
        }
        logger.debug("Stopping background task");
        boolean cancelled = scheduledFuture.cancel(false);
        isRunning = !cancelled;
        logger.debug("Background task cancelled: " + cancelled);
    }

}
