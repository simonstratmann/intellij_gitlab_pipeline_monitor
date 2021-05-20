
package de.sist.gitlab.pipelinemonitor;

import java.util.StringJoiner;

/**
 *
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

    @Override
    public String toString() {
        return new StringJoiner(", ", HostAndProjectPath.class.getSimpleName() + "[", "]")
                .add("host='" + host + "'")
                .add("projectPath='" + projectPath + "'")
                .toString();
    }
}
