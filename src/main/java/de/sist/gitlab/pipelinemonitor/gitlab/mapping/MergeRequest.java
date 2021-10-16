
package de.sist.gitlab.pipelinemonitor.gitlab.mapping;

import javax.annotation.Generated;

@Generated("jsonschema2pojo")
public class MergeRequest {

    private String id;
    private String sourceBranch;
    private String webUrl;
    private String reference;

    public String getId() {
        return id.replace("gid://gitlab/MergeRequest/", "");
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSourceBranch() {
        return sourceBranch;
    }

    public void setSourceBranch(String sourceBranch) {
        this.sourceBranch = sourceBranch;
    }

    public String getWebUrl() {
        return webUrl;
    }

    public void setWebUrl(String webUrl) {
        this.webUrl = webUrl;
    }

    public String getReference() {
        return reference.replace("!", "");
    }

    public void setReference(String reference) {
        this.reference = reference;
    }
}
