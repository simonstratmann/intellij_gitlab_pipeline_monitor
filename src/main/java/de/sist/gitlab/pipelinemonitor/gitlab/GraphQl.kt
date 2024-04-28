// (C) 2021 PPI AG
package de.sist.gitlab.pipelinemonitor.gitlab

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.io.HttpRequests
import de.sist.gitlab.pipelinemonitor.Jackson
import de.sist.gitlab.pipelinemonitor.config.ConfigProvider
import de.sist.gitlab.pipelinemonitor.config.PipelineViewerConfigApp
import de.sist.gitlab.pipelinemonitor.config.PipelineViewerConfigApp.GitlabInfo
import de.sist.gitlab.pipelinemonitor.gitlab.mapping.Data
import de.sist.gitlab.pipelinemonitor.gitlab.mapping.DataWrapper
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*
import java.util.concurrent.ExecutionException
import java.util.stream.Collectors

/**
 * @author PPI AG
 */
object GraphQl {
    private val logger = Logger.getInstance(GraphQl::class.java)

    private const val QUERY_TEMPLATE = "{\n" +
            "  project(fullPath: \"%s\") {\n" +
            "    name\n" +
            "    id\n" +
            "    jobsEnabled\n" +  //Get all merge requests for given source branches which are open, sorted by creation date (we only want the newest)
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
            "    pipelines(status:SUCCESS) {\n" +
            "        nodes {\n" +
            "          id\n" +
            "          detailedStatus {\n" +
            "            group\n" +
            "          }" +
            "      } \n" +
            "    }\n" +
            "  }\n" +
            "}\n"

    private const val REQUEST_TEMPLATE = "{\"query\": \"%s\"}"

    private fun buildQuery(projectPath: String, sourceBranches: List<String>, supportsRef: Boolean): String {
        val sourceBranchesString = sourceBranches.stream().map { x: String -> "\"" + x + "\"" }.collect(Collectors.joining(","))
        val graphqlQuery = String.format(QUERY_TEMPLATE, projectPath, sourceBranchesString, (if (supportsRef) "ref" else "id"))
        return String.format(REQUEST_TEMPLATE, graphqlQuery.replace("\"", "\\\"").replace("\\r?\\n".toRegex(), ""))
    }

    private fun parse(response: String): Data {
        try {
            val data = Jackson.OBJECT_MAPPER.readValue(response, DataWrapper::class.java).data
            //gid://gitlab/Project/16957139 -> 16957139
            var id = data.project.id
            if (id.contains("/")) {
                id = data.project.id.substring(id.lastIndexOf("/") + 1)
            }
            data.project.id = id
            return data
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    private fun determineSupportsRef(gitlabHost: String, accessToken: String?, projectPath: String, sourceBranches: List<String>): Optional<Boolean> {
        if (PipelineViewerConfigApp.instance.gitlabInstanceInfos.containsKey(gitlabHost)) {
            val info = PipelineViewerConfigApp.instance.gitlabInstanceInfos[gitlabHost]
            if (info!!.lastCheck.isBefore(Instant.now().minus(1, ChronoUnit.DAYS))) {
                logger.debug("Found recent supportsRef info for $gitlabHost: $info")
                return Optional.of(info.isSupportsRef)
            }
        }
        var supportsRef: Boolean
        val graphQlUrl = "$gitlabHost/api/graphql"
        try {
            val graphQlQuery = buildQuery(projectPath, sourceBranches, true)
            logger.debug("Reading project data using URL ", graphQlUrl, " and query ", graphQlQuery)

            val responseString = call(accessToken, graphQlUrl, graphQlQuery)
            if (responseString == null || responseString.contains("Field 'ref' doesn't exist on type")) {
                logger.info("Determined $gitlabHost does not support ref from response: $responseString")
                supportsRef = false
            } else {
                supportsRef = true
            }
        } catch (e: Exception) {
            logger.info("Error determining if $gitlabHost supports ref", e)
            supportsRef = false
        }

        val info = GitlabInfo(Instant.now(), supportsRef)
        PipelineViewerConfigApp.instance.gitlabInstanceInfos[gitlabHost] = info
        logger.debug("Updated gitlab info for $gitlabHost: $info")

        return Optional.of(supportsRef)
    }

    fun makeCall(gitlabHost: String, accessToken: String?, projectPath: String, sourceBranches: List<String>, mrRelevant: Boolean): Optional<Data> {
        val graphQlUrl = "$gitlabHost/api/graphql"

        val supportsRef: Optional<Boolean>
        if (mrRelevant) {
            supportsRef = determineSupportsRef(gitlabHost, accessToken, projectPath, sourceBranches)
            if (supportsRef.isEmpty) {
                return Optional.empty()
            }
        } else {
            supportsRef = Optional.of(false)
        }

        val graphQlQuery = buildQuery(projectPath, sourceBranches, supportsRef.get())
        val responseString: String?
        try {
            logger.debug("Reading project data using URL ", graphQlUrl, " and query ", graphQlQuery)

            responseString = call(accessToken, graphQlUrl, graphQlQuery)
        } catch (e: Exception) {
            logger.info("Error loading project data using URL $graphQlUrl and query $graphQlQuery", e)
            return Optional.empty()
        }
        if (responseString == null) {
            //Already logged
            return Optional.empty()
        }
        try {
            return Optional.of(parse(responseString))
        } catch (e: Exception) {
            logger.info("Error reading project data using URL $graphQlUrl and query $graphQlQuery with response $responseString", e)
            return Optional.empty()
        }
    }

    @Throws(InterruptedException::class, ExecutionException::class)
    private fun call(accessToken: String?, graphQlUrl: String, graphQlQuery: String): String? {
        val responseString = ApplicationManager.getApplication().executeOnPooledThread<String> {
            if (GitlabAccessLogger.GITLAB_ACCESS_LOGGER.isDebugEnabled) {
                val cleanedUrl = if (accessToken == null) graphQlUrl else graphQlUrl.replace(accessToken, "<accessToken>")
                GitlabAccessLogger.GITLAB_ACCESS_LOGGER.debug("Calling ", cleanedUrl)
            }
            HttpRequests.post(graphQlUrl, "application/json")
                .readTimeout(ConfigProvider.instance.connectTimeoutSeconds * 1000)
                .connectTimeout(ConfigProvider.instance.connectTimeoutSeconds * 1000) //Is handled in connection step
                .throwStatusCodeException(false)
                .connect { request: HttpRequests.Request ->
                    val response: String
                    try {
                        if (accessToken != null) {
                            request.connection.setRequestProperty("Authorization", "Bearer $accessToken")
                            logger.debug("Using access token with length ", accessToken.length)
                        } else {
                            logger.debug("Not using access token as none is set")
                        }
                        request.write(graphQlQuery)
                        response = request.readString()
                    } catch (e: Exception) {
                        logger.warn("Error connecting to gitlab", e)
                        return@connect null
                    }
                    logger.debug("Got response from query\n:", response)
                    response
                }
        }
            .get()
        return responseString
    }
}
