// (C) 2021 PPI AG
package de.sist.gitlab;

/**
 */
public class HostAndProjectPath {
    private final String host;
    private final String projectPath;

    public HostAndProjectPath(String host, String projectPath) {
        this.host = host;
        this.projectPath = projectPath;
    }

    public String getHost() {
        return host;
    }

    public String getProjectPath() {
        return projectPath;
    }
}
