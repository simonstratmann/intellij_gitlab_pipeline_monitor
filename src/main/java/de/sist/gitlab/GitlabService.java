package de.sist.gitlab;

import com.fasterxml.jackson.core.type.TypeReference;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.util.io.HttpRequests;
import de.sist.gitlab.config.PipelineViewerConfig;
import de.sist.gitlab.notifier.IncompleteConfigListener;
import okhttp3.Request;
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
    private final PipelineViewerConfig config;
    private final Project project;

    public GitlabService(Project project) {
        this.project = project;
        config = project.getService(PipelineViewerConfig.class);
    }

    public List<PipelineJobStatus> getStatuses() throws IOException {
        Set<PipelineJobStatus> statuses = new HashSet<>();
        for (PipelineTo pipeline : getPipelines().stream().sorted(Comparator.comparing(PipelineTo::getUpdatedAt).reversed()).collect(Collectors.toList())) {
            statuses.add(new PipelineJobStatus(pipeline.getRef(), pipeline.getCreatedAt(), pipeline.getUpdatedAt(), pipeline.getStatus(), pipeline.getWebUrl()));
        }

        return new ArrayList<>(statuses);
    }

    public List<PipelineTo> getPipelines() throws IOException {
        PipelineViewerConfig config = PipelineViewerConfig.getInstance(project);
        if (config.getGitlabProjectId() == null || config.getGitlabUrl() == null) {
            project.getMessageBus().syncPublisher(IncompleteConfigListener.CONFIG_INCOMPLETE).handleIncompleteConfig("Incomplete config");
            logger.info("GitLab project ID and/or URL not set");
        }
        List<PipelineTo> pipelines = makeUrlCall(1);
        pipelines.addAll(makeUrlCall(2));
        return pipelines;
    }

    private List<PipelineTo> makeUrlCall(int page) throws IOException {
        URL url;
        try {
            URIBuilder uriBuilder = new URIBuilder(config.getGitlabUrl())
                    .setPath(String.format(PIPELINE_SUFFIX, config.getGitlabProjectId()))
                    .addParameter("page", String.valueOf(page))
                    .addParameter("per_page", "100");
            if (config.getGitlabAuthToken() != null) {
                uriBuilder.addParameter("private_token", config.getGitlabAuthToken());
            }
            url = uriBuilder
                    .build().toURL();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }

        Request.Builder requestBuilder = new Request.Builder().url(url);
        if (config.getGitlabAuthToken() != null) {
            requestBuilder.addHeader("Private-Token", config.getGitlabAuthToken());
        }

        String json = HttpRequests.request(url.toString()).readString();

        return Jackson.OBJECT_MAPPER.readValue(json, new TypeReference<List<PipelineTo>>() {
        });
    }

}
