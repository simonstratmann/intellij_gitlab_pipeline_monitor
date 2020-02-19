package de.sist.gitlab;

import com.fasterxml.jackson.core.type.TypeReference;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import de.sist.gitlab.config.PipelineViewerConfig;
import de.sist.gitlab.notifier.IncompleteConfigListener;
import okhttp3.Call;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.apache.http.client.utils.URIBuilder;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class GitlabService {

    Logger logger = Logger.getInstance(GitlabService.class);
    private static final String PIPELINE_SUFFIX = "/api/v4/projects/%d/pipelines";
    private final HttpClientService httpClient;
    private final PipelineViewerConfig config;
    private final Project project;

    public GitlabService(Project project) {
        this.project = project;
        httpClient = ServiceManager.getService(project, HttpClientService.class);
        config = project.getService(PipelineViewerConfig.class);
    }

    public List<PipelineJobStatus> getStatuses() throws IOException {
        List<PipelineTo> pipelines;
        try {
            pipelines = getPipelines();
        } catch (IOException e) {
            logger.error("Unable to connect to gitlab", e);
            throw e;
        }

        Set<PipelineJobStatus> statuses = new HashSet<>();
        for (PipelineTo pipeline : pipelines.stream().sorted(Comparator.comparing(PipelineTo::getUpdatedAt).reversed()).collect(Collectors.toList())) {
            statuses.add(new PipelineJobStatus(pipeline.getRef(), pipeline.getCreatedAt(), pipeline.getUpdatedAt(), pipeline.getStatus(), pipeline.getWebUrl()));
        }

        return new ArrayList<>(statuses);
    }

    public List<PipelineTo> getPipelines() throws IOException {
        PipelineViewerConfig config = PipelineViewerConfig.getInstance(project);
        if (config.getGitlabProjectId() == null || config.getGitlabUrl() == null) {
            project.getMessageBus().syncPublisher(IncompleteConfigListener.CONFIG_INCOMPLETE).handleIncompleteConfig("Incomplete config");
            logger.info("Gitlab project ID and/or URL not set");
        }
        List<PipelineTo> pipelines = makeUrlCall(1);
        pipelines.addAll(makeUrlCall(2));
        return pipelines;
    }

    private List<PipelineTo> makeUrlCall(int page) throws IOException {
        URL url;
        try {
            url = new URIBuilder(config.getGitlabUrl())
                    .setPath(String.format(PIPELINE_SUFFIX, config.getGitlabProjectId()))
                    .addParameter("page", String.valueOf(page))
                    .addParameter("per_page", "100")
                    .build().toURL();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }

        Call call = httpClient.getClient().newCall(new Request.Builder().url(url).build());
        Response response = call.execute();
        if (!response.isSuccessful()) {
            logger.error("Error contacting gitlab: " + response);
            throw new IOException("Error loading pipelines: " + response);
        }
        try (ResponseBody body = response.body()) {
            if (body == null) {
                throw new IOException("Empty body: " + response);
            }
            String json = body.string();

            return Jackson.OBJECT_MAPPER.readValue(json, new TypeReference<List<PipelineTo>>() {
            });
        }
    }

}
