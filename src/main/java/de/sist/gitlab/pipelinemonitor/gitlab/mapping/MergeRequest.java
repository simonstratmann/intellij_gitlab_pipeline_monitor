
package de.sist.gitlab.pipelinemonitor.gitlab.mapping;

import javax.annotation.Generated;

@Generated("jsonschema2pojo")
public class MergeRequest {

    private String sourceBranch;
    private String webUrl;
    private String title;
    private HeadPipeline headPipeline;


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


    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public HeadPipeline getHeadPipeline() {
        return headPipeline;
    }

    public void setHeadPipeline(HeadPipeline headPipeline) {
        this.headPipeline = headPipeline;
    }

    public static class HeadPipeline {

        private String ref;


        public String getRef() {
            return ref;
        }

        public void setRef(String ref) {
            this.ref = ref;
        }
    }

}
