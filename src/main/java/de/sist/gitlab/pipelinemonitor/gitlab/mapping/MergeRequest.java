
package de.sist.gitlab.pipelinemonitor.gitlab.mapping;

import javax.annotation.Generated;

@Generated("jsonschema2pojo")
public class MergeRequest {

    private String sourceBranch;
    private String webUrl;

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

}
