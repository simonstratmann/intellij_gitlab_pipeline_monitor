// (C) 2021 PPI AG
package de.sist.gitlab.pipelinemonitor.gitlab;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.io.HttpRequests;
import de.sist.gitlab.pipelinemonitor.Jackson;
import de.sist.gitlab.pipelinemonitor.config.ConfigProvider;
import de.sist.gitlab.pipelinemonitor.gitlab.mapping.Data;
import de.sist.gitlab.pipelinemonitor.gitlab.mapping.DataWrapper;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author PPI AG
 */
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
            "          id\n" +
            "          reference\n" +
            "        }\n" +
            "      } \n" +
            "    }\n" +
            "  }\n" +
            "}\n";

    private static final String REQUEST_TEMPLATE = "{\"query\": \"%s\"}";

    public static String buildQuery(String projectPath, List<String> sourceBranches) {
        final String graphqlQuery = String.format(QUERY_TEMPLATE, projectPath, sourceBranches.stream().map(x -> "\"" + x + "\"").collect(Collectors.joining(",")));
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

    public static Optional<Data> makeCall(String gitlabHost, String accessToken, String projectPath, List<String> sourceBranches, boolean asTest) {
        final String graphQlUrl = gitlabHost + "/api/graphql";

        final String graphQlQuery = buildQuery(projectPath, sourceBranches);
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
                                    if (asTest) {
                                        logger.info("Error connecting to gitlab", e);
                                    } else {
                                        logger.error("Error connecting to gitlab", e);
                                    }
                                    return null;
                                }
                                logger.debug("Got response from query\n:", response);
                                return response;
                            }))
                    .get();
        } catch (Exception e) {
            if (asTest) {
                logger.info("Error loading project data using URL " + graphQlUrl + " and query " + graphQlQuery, e);
            } else {
                logger.error("Error loading project data using URL " + graphQlUrl + " and query " + graphQlQuery, e);
            }
            return Optional.empty();
        }
        if (responseString == null) {
            //Already logged
            return Optional.empty();
        }
        try {
            return Optional.of(parse(responseString));
        } catch (Exception e) {
            if (asTest) {
                logger.info("Error reading project data using URL " + graphQlUrl + " and query " + graphQlQuery + " with response " + responseString, e);
            } else {
                logger.error("Error reading project data using URL " + graphQlUrl + " and query " + graphQlQuery + " with response " + responseString, e);
            }
            return Optional.empty();
        }
    }
}
