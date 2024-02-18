package de.sist.gitlab.pipelinemonitor.config;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class GitlabApplicationConfigurable implements Configurable {

    private ConfigFormApp configForm;

    public GitlabApplicationConfigurable() {
    }

    @Override
    public String getDisplayName() {
        return "GitLab Pipeline Viewer";
    }


    @Nullable
    @Override
    public JComponent createComponent() {
        configForm = new ConfigFormApp();
        ConfigProvider.getInstance().setConfigOpen();
        configForm.init();
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
        ConfigProvider.getInstance().setConfigClosed();
        configForm.disposable.dispose();
        configForm = null;
    }

}
