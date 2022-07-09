
package de.sist.gitlab.pipelinemonitor.gitlab.mapping;

import javax.annotation.Generated;

@Generated("jsonschema2pojo")
public class Project {

    private String name;
    private String id;
    private boolean jobsEnabled;
    private MergeRequests mergeRequests;
    private Pipelines pipelines;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public boolean isJobsEnabled() {
        return jobsEnabled;
    }

    public void setJobsEnabled(boolean jobsEnabled) {
        this.jobsEnabled = jobsEnabled;
    }

    public MergeRequests getMergeRequests() {
        return mergeRequests;
    }

    public void setMergeRequests(MergeRequests mergeRequests) {
        this.mergeRequests = mergeRequests;
    }


    public Pipelines getPipelines() {
        return pipelines;
    }

    public void setPipelines(Pipelines pipelines) {
        this.pipelines = pipelines;
    }

}
