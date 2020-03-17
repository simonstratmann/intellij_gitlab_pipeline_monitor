package de.sist.gitlab;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.base.Strings;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.util.io.HttpRequests;
import de.sist.gitlab.config.PipelineViewerConfig;
import de.sist.gitlab.notifier.IncompleteConfigListener;
import org.apache.http.client.utils.URIBuilder;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class GitlabService {

    Logger logger = Logger.getInstance(GitlabService.class);
    private static final String PROJECTS_SUFFIX = "/api/v4/projects/%d/";
    private static final String PIPELINES_SUFFIX = "pipelines/";
    private final PipelineViewerConfig config;
    private final Project project;
    private String gitlabHtmlBaseUrl;

    public GitlabService(Project project) {
        this.project = project;
        config = project.getService(PipelineViewerConfig.class);
    }

    public List<PipelineJobStatus> getStatuses() throws IOException {
        Set<PipelineJobStatus> statuses = new HashSet<>();
        for (PipelineTo pipeline : getPipelines()) {
            statuses.add(new PipelineJobStatus(pipeline.getRef(), pipeline.getCreatedAt(), pipeline.getUpdatedAt(), pipeline.getStatus(), pipeline.getWebUrl()));
        }

        return statuses.stream().sorted(Comparator.comparing(PipelineJobStatus::getUpdateTime).reversed()).collect(Collectors.toList());
    }

    public List<PipelineTo> getPipelines() throws IOException {
        PipelineViewerConfig config = PipelineViewerConfig.getInstance(project);
        if (config == null || config.getGitlabProjectId() == null || Strings.isNullOrEmpty(config.getGitlabUrl())) {
            project.getMessageBus().syncPublisher(IncompleteConfigListener.CONFIG_INCOMPLETE).handleIncompleteConfig("Incomplete config");
            logger.info("GitLab project ID and/or URL not set");
            return Collections.emptyList();
        }
        List<PipelineTo> pipelines = makePipelinesUrlCall(1);
        pipelines.addAll(makePipelinesUrlCall(2));
        return pipelines;
    }

    private List<PipelineTo> makePipelinesUrlCall(int page) throws IOException {
        String url;
        try {
            URIBuilder uriBuilder = getBaseUriBuilder(true);

            uriBuilder.addParameter("page", String.valueOf(page))
                    .addParameter("per_page", "100");


            url = uriBuilder.build().toString();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        String json = HttpRequests.request(url).readString();

        //While we're at it we load the gitlab HTML base URL
        getGitlabHtmlBaseUrl();

        return Jackson.OBJECT_MAPPER.readValue(json, new TypeReference<List<PipelineTo>>() {
        });
    }

    public String getGitlabHtmlBaseUrl() {
        if (gitlabHtmlBaseUrl == null) {
            try {
                URIBuilder uriBuilder = getBaseUriBuilder(false);
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

    private URIBuilder getBaseUriBuilder(boolean pipelines) throws URISyntaxException {
        String url = String.format(PROJECTS_SUFFIX, config.getGitlabProjectId());
        if (pipelines) {
            url += PIPELINES_SUFFIX;
        }
        URIBuilder uriBuilder = new URIBuilder(config.getGitlabUrl()).setPath(url);
        if (config.getGitlabAuthToken() != null) {
            uriBuilder.addParameter("private_token", config.getGitlabAuthToken());
        }
        return uriBuilder;
    }


}
