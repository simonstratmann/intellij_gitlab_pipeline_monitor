package de.sist.gitlab.pipelinemonitor

import com.google.common.base.Stopwatch
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.AppExecutorUtil
import de.sist.gitlab.pipelinemonitor.config.ConfigChangedListener
import de.sist.gitlab.pipelinemonitor.config.ConfigProvider
import de.sist.gitlab.pipelinemonitor.config.PipelineViewerConfigApp
import de.sist.gitlab.pipelinemonitor.config.PipelineViewerConfigProject
import de.sist.gitlab.pipelinemonitor.git.GitInitListener
import de.sist.gitlab.pipelinemonitor.git.GitService
import de.sist.gitlab.pipelinemonitor.gitlab.GitlabService
import de.sist.gitlab.pipelinemonitor.notifier.NotifierService
import dev.failsafe.FailsafeException
import java.io.IOException
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

@Service(Service.Level.PROJECT)
class BackgroundUpdateService(private val project: Project) {
    private val gitService: GitService
    private val notifierService: NotifierService


    @get:Synchronized
    var isActive: Boolean = false
        private set
    private var isRunning = false
    private val backgroundTask: Runnable
    private var scheduledFuture: ScheduledFuture<*>? = null
    private val gitlabService: GitlabService = project.getService(GitlabService::class.java)
    private val messageBus = project.messageBus
    private var connectionFailureReported = false

    init {

        backgroundTask = Runnable {
            if (project.isDisposed) {
                return@Runnable
            }
            if (!PipelineViewerConfigProject.getInstance(project).isEnabled) {
                stopBackgroundTask()
                return@Runnable
            }
            update(project, false)
        }

        val messageBusConnection = project.messageBus.connect()
        messageBusConnection.subscribe(GitInitListener.GIT_INITIALIZED, GitInitListener {
            logger.debug("Retrieved GIT_INITIALIZED event. Starting background task if needed")
            //If the background task is started at once for some reason the progress indicator is never closed
            if (isActive) {
                logger.debug("Background task already running")
                return@GitInitListener
            }
            if (!PipelineViewerConfigProject.getInstance(this.project).isEnabled) {
                return@GitInitListener
            }
            logger.debug("Starting background task")
            scheduledFuture =
                AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay(
                    backgroundTask,
                    5,
                    PipelineViewerConfigApp.instance.refreshDelay.toLong(),
                    TimeUnit.SECONDS
                )
            isActive = true
        })
        messageBusConnection.subscribe(ConfigChangedListener.CONFIG_CHANGED, ConfigChangedListener {
            if (!PipelineViewerConfigProject.getInstance(
                    project
                ).isEnabled
            ) {
                logger.debug("Retrieved CONFIG_CHANGED event. Project is disabled. Stopping background task if needed")
                stopBackgroundTask()
            } else {
                logger.debug("Retrieved CONFIG_CHANGED event. Project is enabled. Starting background task if needed")
            }
        })
        gitService = project.getService(GitService::class.java)
        notifierService = project.getService(NotifierService::class.java)
    }

    @Synchronized
    fun update(project: Project?, triggeredByUser: Boolean) {
        if (isRunning) {
            return
        }
        val updateRunnable = Runnable {
            val stopwatch = Stopwatch.createStarted()
            getUpdateRunnable(triggeredByUser).run()
            //For some stupid reason the progress bar is not removed if the process returns too quickly
            if (stopwatch.elapsed(TimeUnit.MILLISECONDS) < 1000) {
                try {
                    Thread.sleep(500)
                } catch (ignored: InterruptedException) {
                }
            }
        }
        if (PipelineViewerConfigApp.instance.isShowProgressBar) {
            val updateTask: Task.Backgroundable = object : Task.Backgroundable(project, "Loading gitLab pipelines", false) {
                override fun run(indicator: ProgressIndicator) {
                    try {
                        updateRunnable.run()
                    } finally {
                        indicator.stop()
                    }
                }
            }

            val updateProgressIndicator = BackgroundableProcessIndicator(updateTask)
            ProgressManager.getInstance().runProcessWithProgressAsynchronously(updateTask, updateProgressIndicator)
        } else {
            ApplicationManager.getApplication().executeOnPooledThread(updateRunnable)
        }
    }

    private fun getUpdateRunnable(triggeredByUser: Boolean): Runnable {
        return Runnable {
            if (isRunning || project.isDisposed) {
                return@Runnable
            }
            isRunning = true
            try {
                logger.debug("Starting IntelliJ background task", (if (triggeredByUser) " triggered by user" else ""))
                gitlabService.checkForUnmappedRemotes(triggeredByUser)
                gitlabService.updatePipelineInfos(triggeredByUser)
                gitlabService.updateFromGraphQl()
                if (!messageBus.isDisposed) {
                    messageBus.syncPublisher(ReloadListener.RELOAD).reload(gitlabService.getPipelineInfos())
                }
                connectionFailureReported = false
                logger.debug("Finished IntelliJ background task")
            } catch (e: Exception) {
                if (e is FailsafeException || e is IOException) {
                    logger.info("Connection error: " + e.message, e)
                    if (ConfigProvider.instance.isShowConnectionErrorNotifications) {
                        if (!connectionFailureReported || triggeredByUser) {
                            logger.debug("Showing notification for first connection error after a successful connection")
                            notifierService.showError("Unable to connect to gitlab: $e")
                            connectionFailureReported = true
                        } else {
                            logger.debug("Not notification for connection error because one was already shown")
                        }
                    }
                } else {
                    throw e
                }

            } finally {
                isRunning = false
            }
        }
    }

    @Synchronized
    fun startBackgroundTask(): Boolean {
        if (isActive) {
            logger.debug("Background task already running")
            return false
        }
        if (!PipelineViewerConfigProject.getInstance(project).isEnabled) {
            return false
        }
        logger.debug("Starting background task")
        scheduledFuture = AppExecutorUtil.getAppScheduledExecutorService()
            .scheduleWithFixedDelay(
                backgroundTask,
                INITIAL_DELAY.toLong(),
                PipelineViewerConfigApp.instance.refreshDelay.toLong(),
                TimeUnit.SECONDS
            )
        isActive = true
        return true
    }

    @Synchronized
    fun stopBackgroundTask() {
        if (!isActive) {
            logger.debug("Background task already stopped")
        }
        if (scheduledFuture == null) {
            //Should not happen but can happen... (don't know when)
            return
        }
        logger.debug("Stopping background task")
        val cancelled = scheduledFuture!!.cancel(false)
        isActive = !cancelled
        logger.debug("Background task cancelled: ", cancelled)
    }

    @Synchronized
    fun restartBackgroundTask() {
        logger.debug("Restarting background task")
        if (isActive) {
            val cancelled = scheduledFuture!!.cancel(false)
            isActive = !cancelled
            logger.debug("Background task cancelled: ", cancelled)
        }
        scheduledFuture = AppExecutorUtil.getAppScheduledExecutorService()
            .scheduleWithFixedDelay(
                backgroundTask,
                INITIAL_DELAY.toLong(),
                PipelineViewerConfigApp.instance.refreshDelay.toLong(),
                TimeUnit.SECONDS
            )
        isActive = true
    }

    companion object {
        private val logger = Logger.getInstance(BackgroundUpdateService::class.java)

        private const val INITIAL_DELAY = 0
    }
}
