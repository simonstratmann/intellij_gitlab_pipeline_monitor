package de.sist.gitlab.pipelinemonitor.config;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.util.Disposer;
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
        Disposer.dispose(configForm.disposable);
        configForm = null;
    }

}
