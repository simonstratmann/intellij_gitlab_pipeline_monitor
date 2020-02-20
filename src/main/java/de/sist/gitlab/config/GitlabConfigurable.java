package de.sist.gitlab.config;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class GitlabConfigurable implements Configurable {

    private ConfigForm configForm;
    private Project project;

    public GitlabConfigurable(Project project) {
        this.project = project;
    }


    @Override
    public String getDisplayName() {
        return "GitLab Pipeline Viewer";
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        configForm = new ConfigForm();
        configForm.init(project);
        return configForm.getMainPanel();
    }

    @Override
    public boolean isModified() {
        return configForm != null && configForm.isModified();
    }

    @Override
    public void apply() throws ConfigurationException {
        configForm.apply();
    }

    @Override
    public void reset() {
        configForm.loadSettings();
    }

    @Override
    public void disposeUIResources() {
        configForm = null;
    }

}
