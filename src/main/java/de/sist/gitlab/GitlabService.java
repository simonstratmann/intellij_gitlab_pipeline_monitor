package de.sist.gitlab;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.base.Strings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.io.HttpRequests;
import de.sist.gitlab.config.ConfigProvider;
import de.sist.gitlab.config.Mapping;
import de.sist.gitlab.config.PipelineViewerConfigApp;
import de.sist.gitlab.config.PipelineViewerConfigProject;
import de.sist.gitlab.git.GitInitListener;
import de.sist.gitlab.notifier.IncompleteConfigListener;
import de.sist.gitlab.ui.UnmappedRemoteDialog;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import org.apache.http.client.utils.URIBuilder;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class GitlabService implements Disposable {

    private static final Logger logger = Logger.getInstance(GitlabService.class);
    private static final String PROJECTS_SUFFIX = "/api/v4/projects/%s/";
    private static final String PIPELINES_SUFFIX = "pipelines/";
    private final ConfigProvider config = ServiceManager.getService(ConfigProvider.class);
    private final Project project;
    private String gitlabHtmlBaseUrl;
    private boolean incompleteConfigNotificationShown = false;
    private final Map<String, String> idToName = new HashMap<>();

    public GitlabService(Project project) {
        this.project = project;

        project.getMessageBus().connect().subscribe(GitInitListener.GIT_INITIALIZED, this::checkForUnmappedRemotes);
    }

    public void retrieveProjectNames() {
        for (Mapping mapping : PipelineViewerConfigApp.getInstance().getMappings()) {
            if (!idToName.containsKey(mapping.getGitlabProjectId())) {
                final URIBuilder uriBuilder = getProjectsBaseUriBuilder(false, mapping.getGitlabProjectId());
                final Map<String, Object> map;
                try {

                    final String url = uriBuilder.build().toString();
                    String response = HttpRequests.request(url).readString();

                    map = Jackson.OBJECT_MAPPER.readValue(response, new TypeReference<Map<String, Object>>() {
                    });
                    idToName.put(mapping.getGitlabProjectId(), (String) map.get("name"));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    public Map<String, List<PipelineJobStatus>> getPipelineInfos() throws IOException {
        Map<String, List<PipelineJobStatus>> gitlabProjectToPipelines = new HashMap<>();
        for (Map.Entry<String, List<PipelineTo>> entry : getPipelines().entrySet()) {
            final List<PipelineJobStatus> jobStatuses = entry.getValue().stream()
                    .map(pipeline -> new PipelineJobStatus(pipeline.getRef(), entry.getKey(), pipeline.getCreatedAt(), pipeline.getUpdatedAt(), pipeline.getStatus(), pipeline.getWebUrl()))
                    .sorted(Comparator.comparing(PipelineJobStatus::getUpdateTime, Comparator.nullsFirst(Comparator.naturalOrder())).reversed())
                    .collect(Collectors.toList());
            gitlabProjectToPipelines.put(entry.getKey(), jobStatuses);
        }
        return gitlabProjectToPipelines;
    }

    private void checkForUnmappedRemotes(List<GitRepository> gitRepositories) {
        final Set<String> handledMappings = new HashSet<>();
        final List<Mapping> mappings = config.getMappings(project);
        for (GitRepository gitRepository : gitRepositories) {
            for (GitRemote remote : gitRepository.getRemotes()) {
                for (String url : remote.getUrls()) {
                    if (handledMappings.contains(url)) {
                        continue;
                    }
                    if (PipelineViewerConfigApp.getInstance().getIgnoredRemotes().contains(url)) {
                        continue;
                    }
                    if (mappings.isEmpty() || mappings.stream().noneMatch(mapping -> url.equals(mapping.getRemote()))) {
                        final Disposable disposable = Disposer.newDisposable();
                        final UnmappedRemoteDialog.Response response = new UnmappedRemoteDialog(url, disposable).showDialog();
                        //Even if the user chose "Ask again" don't ask again right now if the remote is used multiple times
                        handledMappings.add(url);
                        if (response.getCancel() == null) {
                            PipelineViewerConfigApp.getInstance().getMappings().add(new Mapping(url, response.getProjectId()));
                        } else if (response.getCancel() == UnmappedRemoteDialog.Cancel.IGNORE_REMOTE) {
                            PipelineViewerConfigApp.getInstance().getIgnoredRemotes().add(url);
                        } else if (response.getCancel() == UnmappedRemoteDialog.Cancel.IGNORE_PROJECT) {
                            PipelineViewerConfigProject.getInstance(project).setEnabled(false);
                        }
                        Disposer.dispose(disposable);
                    }
                }
            }
        }
    }


    public String getProjectNameById(String projectId) {
        return idToName.getOrDefault(projectId, "<Unknown project name>");
    }

    private Map<String, List<PipelineTo>> getPipelines() throws IOException {
        ConfigProvider config = ConfigProvider.getInstance();
        boolean configIncomplete = config == null || config.getMappings(project) == null || config.getMappings(project).isEmpty() || config.getGitlabUrl(project) == null;
        if (configIncomplete & !incompleteConfigNotificationShown) {
            project.getMessageBus().syncPublisher(IncompleteConfigListener.CONFIG_INCOMPLETE).handleIncompleteConfig("Incomplete config");
            logger.info("GitLab URL not set");
            incompleteConfigNotificationShown = true;
            return Collections.emptyMap();
        }
        if (config == null) {
            return Collections.emptyMap();
        }
        final Map<String, List<PipelineTo>> projectToPipelines = new HashMap<>();
        for (Mapping mapping : config.getMappings(project)) {
            List<PipelineTo> pipelines = makePipelinesUrlCall(1, mapping.getGitlabProjectId());
            pipelines.addAll(makePipelinesUrlCall(2, mapping.getGitlabProjectId()));
            projectToPipelines.put(mapping.getGitlabProjectId(), pipelines);
        }
        return projectToPipelines;
    }

    private List<PipelineTo> makePipelinesUrlCall(int page, String projectId) throws IOException {
        String url;
        try {
            URIBuilder uriBuilder = getProjectsBaseUriBuilder(true, projectId);

            uriBuilder.addParameter("page", String.valueOf(page))
                    .addParameter("per_page", "100");

            url = uriBuilder.build().toString();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        String json = HttpRequests.request(url).readString();

        //While we're at it we load the gitlab HTML base URL
        getGitlabHtmlBaseUrl(projectId);

        return Jackson.OBJECT_MAPPER.readValue(json, new TypeReference<List<PipelineTo>>() {
        });
    }

    public String getGitlabHtmlBaseUrl(String projectId) {
        if (gitlabHtmlBaseUrl == null) {
            try {
                URIBuilder uriBuilder = getProjectsBaseUriBuilder(false, projectId);
                String json = HttpRequests.request(uriBuilder.build().toString()).readString();
                Map<String, Object> map = Jackson.OBJECT_MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {
                });
                gitlabHtmlBaseUrl = (String) map.get("web_url");
            } catch (IOException | URISyntaxException e) {
                throw new RuntimeException(e);
            }
        }
        return gitlabHtmlBaseUrl;
    }

    private URIBuilder getProjectsBaseUriBuilder(boolean pipelines, String projectId) {
        String path = String.format(PROJECTS_SUFFIX, projectId);
        if (pipelines) {
            path += PIPELINES_SUFFIX;
        }
        //Fucking URIBuilder does not allow appending paths
        URIBuilder uriBuilder;
        try {
            uriBuilder = new URIBuilder(config.getGitlabUrl(project));
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        path = (Strings.nullToEmpty(uriBuilder.getPath()) + "/" + path).replace("//", "/");
        uriBuilder = uriBuilder.setPath(path);
        if (config.getGitlabAuthToken(project) != null) {
            uriBuilder.addParameter("private_token", config.getGitlabAuthToken(project));
        }
        return uriBuilder;
    }


    @Override
    public void dispose() {

    }
}
