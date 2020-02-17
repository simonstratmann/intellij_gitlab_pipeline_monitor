package de.sist.gitlab;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import de.sist.gitlab.ui.NotifierService;
import git4idea.branch.GitBranchUtil;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;

public class StartupInitialization implements StartupActivity {

    Logger logger = Logger.getInstance(StartupInitialization.class);

    @Override
    public void runActivity(@NotNull Project project) {
        //Get service so it's initialized
        project.getService(NotifierService.class);

        GitRepository currentRepository = GitBranchUtil.getCurrentRepository(project);
        logger.debug("Determined current repository: " + currentRepository);
        if (currentRepository == null) {
            logger.error("Unable to find current repository");
            return;
        }
        if (currentRepository.getRemotes().stream().noneMatch(x -> x.getUrls().stream().anyMatch(y -> y.contains("clearing/phmaven")))) {
            logger.error("Current repository doesn't seem to be for phmaven. Found remotes: " + currentRepository.getRemotes());
            return;
        }

        project.getService(GitlabService.class).setCurrentRepository(currentRepository);
        project.getService(BackgroundUpdateService.class).startBackgroundTask();
    }
}
