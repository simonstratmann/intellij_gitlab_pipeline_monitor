// (C) 2021 PPI AG
package de.sist.gitlab.pipelinemonitor.gitlab;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.io.HttpRequests;
import de.sist.gitlab.pipelinemonitor.Jackson;
import de.sist.gitlab.pipelinemonitor.config.ConfigProvider;
import de.sist.gitlab.pipelinemonitor.config.PipelineViewerConfigApp;
import de.sist.gitlab.pipelinemonitor.gitlab.mapping.Data;
import de.sist.gitlab.pipelinemonitor.gitlab.mapping.DataWrapper;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author PPI AG
 */
@SuppressWarnings("rawtypes")
public class GraphQl {

    private static final Logger logger = Logger.getInstance(GitlabService.class);

    private static final String QUERY_TEMPLATE = "{\n" +
            "  project(fullPath: \"%s\") {\n" +
            "    name\n" +
            "    id\n" +
            "    jobsEnabled\n" +
            //Get all merge requests for given source branches which are open, sorted by creation date (we only want the newest)
            "    mergeRequests(sourceBranches:[%s], state:opened, sort:CREATED_DESC) {\n" +
            "      edges {\n" +
            "        node {\n" +
            "          sourceBranch\n" +
            "          webUrl\n" +
            "          title\n" +
            "          headPipeline {\n" +
            "            %s\n" +
            "          }" +
            "        }\n" +
            "      } \n" +
            "    }\n" +
            "  }\n" +
            "}\n";

    private static final String REQUEST_TEMPLATE = "{\"query\": \"%s\"}";

    private static String buildQuery(String projectPath, List<String> sourceBranches, boolean supportsRef) {
        final String sourceBranchesString = sourceBranches.stream().map(x -> "\"" + x + "\"").collect(Collectors.joining(","));
        final String graphqlQuery = String.format(QUERY_TEMPLATE, projectPath, sourceBranchesString, (supportsRef ? "ref" : "id"));
        return String.format(REQUEST_TEMPLATE, graphqlQuery.replace("\"", "\\\"").replaceAll("[\\r\\n|\\n]", ""));
    }

    private static Data parse(String response) {
        try {
            final Data data = Jackson.OBJECT_MAPPER.readValue(response, DataWrapper.class).getData();
            //gid://gitlab/Project/16957139 -> 16957139
            String id = data.getProject().getId();
            if (id.contains("/")) {
                id = data.getProject().getId().substring(id.lastIndexOf("/") + 1);
            }
            data.getProject().setId(id);
            return data;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Optional<Boolean> determineSupportsRef(String gitlabHost, String accessToken) {
        final String version;
        if (PipelineViewerConfigApp.getInstance().getGitlabInstanceInfos().containsKey(gitlabHost)) {
            final PipelineViewerConfigApp.GitlabInfo info = PipelineViewerConfigApp.getInstance().getGitlabInstanceInfos().get(gitlabHost);
            if (info.getLastCheck().isBefore(Instant.now().minus(1, ChronoUnit.DAYS))) {
                version = info.getVersion();
                return Optional.of(version.startsWith("14.2") || version.startsWith("15"));
            }
        }
        final String url = gitlabHost + "/api/v4/version";
        try {
            final String response = GitlabService.makeApiCall(url, accessToken);
            final Map responseMap = Jackson.OBJECT_MAPPER.readValue(response, Map.class);
            final PipelineViewerConfigApp.GitlabInfo info = new PipelineViewerConfigApp.GitlabInfo(gitlabHost, Instant.now());
            info.setVersion((String) responseMap.get("version"));
            PipelineViewerConfigApp.getInstance().getGitlabInstanceInfos().put(gitlabHost, info);
            logger.debug("Updated gitlab info for " + gitlabHost + ": " + info);
            version = (String) responseMap.get("version");
        } catch (IOException e) {
            logger.error("Unable to load API version using URL " + url, e);
            return Optional.empty();
        } catch (GitlabService.LoginException e) {
            logger.info("Unable to log in to " + gitlabHost + " load API version");
            return Optional.empty();
        }

        return Optional.of(version.startsWith("14.2") || version.startsWith("15"));
    }

    public static Optional<Data> makeCall(String gitlabHost, String accessToken, String projectPath, List<String> sourceBranches, boolean mrRelevant) {
        final String graphQlUrl = gitlabHost + "/api/graphql";

        final Optional<Boolean> supportsRef;
        if (mrRelevant) {
            supportsRef = determineSupportsRef(gitlabHost, accessToken);
            if (supportsRef.isEmpty()) {
                return Optional.empty();
            }
        } else {
            supportsRef = Optional.of(false);
        }

        final String graphQlQuery = buildQuery(projectPath, sourceBranches, supportsRef.get());
        final String responseString;
        try {
            logger.debug("Reading project data using URL ", graphQlUrl, " and query ", graphQlQuery);

            responseString = ApplicationManager.getApplication().executeOnPooledThread(() ->
                            HttpRequests.post(graphQlUrl, "application/json")
                                    .readTimeout(ConfigProvider.getInstance().getConnectTimeoutSeconds() * 1000)
                                    .connectTimeout(ConfigProvider.getInstance().getConnectTimeoutSeconds() * 1000)
                                    //Is handled in connection step
                                    .throwStatusCodeException(false)
                            .connect(request -> {
                                final String response;
                                try {
                                    if (accessToken != null) {
                                        request.getConnection().setRequestProperty("Authorization", "Bearer " + accessToken);
                                        logger.debug("Using access token with length ", accessToken.length());
                                    } else {
                                        logger.debug("Not using access token");
                                    }
                                    request.write(graphQlQuery);
                                    response = request.readString();
                                } catch (Exception e) {
                                    logger.warn("Error connecting to gitlab", e);
                                    return null;
                                }
                                logger.debug("Got response from query\n:", response);
                                return response;
                            }))
                    .get();
        } catch (Exception e) {
                logger.info("Error loading project data using URL " + graphQlUrl + " and query " + graphQlQuery, e);
            return Optional.empty();
        }
        if (responseString == null) {
            //Already logged
            return Optional.empty();
        }
        try {
            return Optional.of(parse(responseString));
        } catch (Exception e) {
                logger.info("Error reading project data using URL " + graphQlUrl + " and query " + graphQlQuery + " with response " + responseString, e);
            return Optional.empty();
        }
    }
}
