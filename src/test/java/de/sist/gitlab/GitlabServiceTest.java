package de.sist.gitlab;

import junit.framework.TestCase;

public class GitlabServiceTest extends TestCase {

    public void testGetProjectPathFromRemote() throws Exception {
//        GitlabService.getProjectPathFromRemote("https://gitlab.com/ppiag/intellij_gitlab_pipeline_monitor.git");
//        final Optional<GitlabService.HostAndProjectPath> projectPathFromRemote = GitlabService.getProjectPathFromRemote("https://gitlab.ppi.int/tph/prod/phmaven.git");
        System.out.println(Jackson.OBJECT_MAPPER.writeValueAsString("query {\n" +
                "  project(fullPath:\"clearing/devpro/adoptopenjdk\") {\n" +
                "    name\n" +
                "    id\n" +
                "  }\n" +
                "}"));
    }
}
