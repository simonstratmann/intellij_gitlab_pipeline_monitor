
package de.sist.gitlab.pipelinemonitor.update;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;

/**
 * Keeps track of a "version" that's currently installed. Allows showing update notifications in the future.
 */
public class AfterUpdateNotifier implements StartupActivity {

    public static final int CURRENT_VERSION = 1;

    @Override
    public void runActivity(@NotNull Project project) {
        final Integer installedVersion = UpdateNotificationPersistance.getInstance().getInstalledVersion();
        if (installedVersion == null) {
            UpdateNotificationPersistance.getInstance().setInstalledVersion(CURRENT_VERSION);
        }
    }
}
