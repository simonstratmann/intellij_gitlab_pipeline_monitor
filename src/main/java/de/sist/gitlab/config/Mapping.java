// (C) 2021 PPI AG
package de.sist.gitlab.config;

import com.google.common.base.Objects;

/**
 * @author PPI AG
 */
public class Mapping {

    private String remote;
    private String gitlabProjectId;

    public Mapping() {
    }

    public Mapping(String remote, String gitlabProjectId) {
        this.remote = remote;
        this.gitlabProjectId = gitlabProjectId;
    }

    public String getRemote() {
        return remote;
    }

    public void setRemote(String remote) {
        this.remote = remote;
    }

    public String getGitlabProjectId() {
        return gitlabProjectId;
    }

    public void setGitlabProjectId(String gitlabProjectId) {
        this.gitlabProjectId = gitlabProjectId;
    }

    public static String toSerializable(Mapping mapping) {
        return mapping.getRemote() + "=" + mapping.getGitlabProjectId();
    }

    public static Mapping toMapping(String string) {
        final String[] split = string.split("=");
        if (split.length != 2) {
            return null;
        }
        return new Mapping(split[0], split[1]);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Mapping mapping = (Mapping) o;
        return Objects.equal(remote, mapping.remote) && Objects.equal(gitlabProjectId, mapping.gitlabProjectId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(remote, gitlabProjectId);
    }
}
