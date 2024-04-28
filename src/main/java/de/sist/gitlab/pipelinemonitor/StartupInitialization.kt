package de.sist.gitlab.pipelinemonitor

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import de.sist.gitlab.pipelinemonitor.BackgroundUpdateService
import de.sist.gitlab.pipelinemonitor.git.GitService
import de.sist.gitlab.pipelinemonitor.lights.LightsControl
import de.sist.gitlab.pipelinemonitor.notifier.NotifierService

class StartupInitialization : ProjectActivity {

    companion object {
        private val logger = Logger.getInstance(StartupInitialization::class.java)
    }

    override suspend fun execute(project: Project) {
        //Get service so it's initialized
        project.getService(NotifierService::class.java)
        project.getService(LightsControl::class.java)
        project.getService(BackgroundUpdateService::class.java)
        logger.debug("Running startup initialization (reloading git repositories)")
        project.getService(GitService::class.java).reloadGitRepositories()
    }
}
