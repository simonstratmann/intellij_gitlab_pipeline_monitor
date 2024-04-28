package de.sist.gitlab.pipelinemonitor.update

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/**
 * Keeps track of a "version" that's currently installed. Allows showing update notifications in the future.
 */
class AfterUpdateNotifier : ProjectActivity {

    override suspend fun execute(project: Project) {
        val installedVersion = UpdateNotificationPersistance.getInstance().installedVersion
        if (installedVersion == null) {
            UpdateNotificationPersistance.getInstance().installedVersion = CURRENT_VERSION
        }
    }

    companion object {
        const val CURRENT_VERSION: Int = 1
    }
}
