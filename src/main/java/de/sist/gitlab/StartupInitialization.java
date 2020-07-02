package de.sist.gitlab;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import de.sist.gitlab.config.PipelineViewerConfig;
import de.sist.gitlab.git.GitInitListener;
import de.sist.gitlab.git.GitService;
import de.sist.gitlab.lights.LightsControl;
import de.sist.gitlab.notifier.NotifierService;
import git4idea.GitUtil;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class StartupInitialization implements StartupActivity {


    @Override
    public void runActivity(@NotNull Project project) {
        //Get service so it's initialized
        project.getService(NotifierService.class);
        project.getService(LightsControl.class);

        PipelineViewerConfig config = PipelineViewerConfig.getInstance(project);
        if (config != null) {
            config.initIfNeeded();
        }
        project.getService(GitService.class);

        List<GitRepository> repositories = GitUtil.getRepositoryManager(project).getRepositories();
        if (repositories.isEmpty()) {
            return;
        }
        project.getMessageBus().syncPublisher(GitInitListener.GIT_INITIALIZED).handle(repositories.get(0));
    }
}
