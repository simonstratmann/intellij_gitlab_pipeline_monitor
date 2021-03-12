package de.sist.gitlab;

import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.IconLoader;
import com.intellij.util.concurrency.AppExecutorUtil;
import de.sist.gitlab.config.PipelineViewerConfig;
import de.sist.gitlab.git.GitInitListener;
import de.sist.gitlab.notifier.NotifierService;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class BackgroundUpdateService {

    private static final int INITIAL_DELAY = 0;
    private static final int UPDATE_DELAY = 30;
    private static final String DISPLAY_ID = "GitLab Pipeline Viewer - Error";
    Logger logger = Logger.getInstance(BackgroundUpdateService.class);

    private boolean isRunning = false;
    private final Runnable backgroundTask;
    private ScheduledFuture<?> scheduledFuture;

    public BackgroundUpdateService(Project project) {
        GitlabService gitlabService = ServiceManager.getService(project, GitlabService.class);
        backgroundTask = () -> {
            try {
                List<PipelineJobStatus> statuses = gitlabService.getStatuses();
                project.getMessageBus().syncPublisher(ReloadListener.RELOAD).reload(statuses);
            } catch (IOException e) {
                if (PipelineViewerConfig.getInstance(project).isShowConnectionErrorNotifications()) {
                    ServiceManager.getService(project, NotifierService.class).showError("Unable to connect got gitlab: " + e);
                }
            }
        };
        PipelineViewerConfig config = PipelineViewerConfig.getInstance(project);
        if (config.getGitlabProjectId() == null || config.getGitlabProjectId() == 0) {
            NotificationGroup notificationGroup = NotificationGroup.findRegisteredGroup(DISPLAY_ID);
            if (notificationGroup == null) {
                notificationGroup = new NotificationGroup(DISPLAY_ID, NotificationDisplayType.BALLOON, true,
                        "GitLab pipeline viewer", IconLoader.getIcon("/toolWindow/gitlab-icon.png", BackgroundUpdateService.class));
            }
            notificationGroup.createNotification("No gitlab project ID set", MessageType.ERROR);
            return;
        }

        project.getMessageBus().connect().subscribe(GitInitListener.GIT_INITIALIZED, gitRepository -> startBackgroundTask());
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
        if (scheduledFuture == null) {
            //Should not happen but can happen... (don't know when)
            return;
        }
        logger.debug("Stopping background task");
        boolean cancelled = scheduledFuture.cancel(false);
        isRunning = !cancelled;
        logger.debug("Background task cancelled: " + cancelled);
    }

    public synchronized void restartBackgroundTask() {
        logger.debug("Restarting background task");
        if (isRunning) {
            boolean cancelled = scheduledFuture.cancel(false);
            isRunning = !cancelled;
            logger.debug("Background task cancelled: " + cancelled);
        }
        scheduledFuture = AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay(backgroundTask, 0, UPDATE_DELAY, TimeUnit.SECONDS);
        isRunning = true;
    }

}
