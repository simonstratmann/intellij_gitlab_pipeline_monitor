package de.sist.gitlab;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.AppExecutorUtil;
import de.sist.gitlab.config.ConfigChangedListener;
import de.sist.gitlab.config.ConfigProvider;
import de.sist.gitlab.config.Mapping;
import de.sist.gitlab.config.PipelineViewerConfigProject;
import de.sist.gitlab.git.GitInitListener;
import de.sist.gitlab.notifier.NotifierService;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class BackgroundUpdateService {

    private static final Logger logger = Logger.getInstance(Logger.class);

    private static final int INITIAL_DELAY = 0;
    private static final int UPDATE_DELAY = 30;

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
            update(project);
        };

        project.getMessageBus().connect().subscribe(GitInitListener.GIT_INITIALIZED, gitRepository -> {
            logger.debug("Retrieved GIT_INITIALIZED event. Starting background task if needed");
            startBackgroundTask();
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

    public void update(Project project) {
        Task.Backgroundable task = new Task.Backgroundable(project, "Loading gitLab pipelines") {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    final Map<Mapping, List<PipelineJobStatus>> pipelineInfos = ServiceManager.getService(project, GitlabService.class).getPipelineInfos();
                    project.getMessageBus().syncPublisher(ReloadListener.RELOAD).reload(pipelineInfos);
                } catch (IOException e) {
                    if (ConfigProvider.getInstance().isShowConnectionErrorNotifications()) {
                        ServiceManager.getService(project, NotifierService.class).showError("Unable to connect to gitlab: " + e);
                    }
                }
            }
        };
        ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, new BackgroundableProcessIndicator(task));
    }

    public synchronized boolean startBackgroundTask() {
        if (isRunning) {
            logger.debug("Background task already running");
            return false;
        }
        if (!PipelineViewerConfigProject.getInstance(project).isEnabled()) {
            return false;
        }
        logger.debug("Starting background task");
        scheduledFuture = AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay(backgroundTask, INITIAL_DELAY, UPDATE_DELAY, TimeUnit.SECONDS);
        isRunning = true;
        return true;
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
