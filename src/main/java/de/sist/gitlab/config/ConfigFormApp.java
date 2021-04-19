package de.sist.gitlab.config;

import com.google.common.base.Strings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.ComponentValidator;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.IdeBorderFactory;
import de.sist.gitlab.BackgroundUpdateService;
import de.sist.gitlab.lights.LightsControl;
import de.sist.gitlab.validator.UrlValidator;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@SuppressWarnings("unused")
public class ConfigFormApp {

    private PipelineViewerConfigApp config;

    private JPanel mainPanel;

    private JPanel projectIdPanel;
    private JTextField gitlabUrlField;
    private JTextField authTokenField;
    private JTextField mergeRequestTargetBranch;
    private JCheckBox watchedBranchesNotificationCheckbox;
    private JCheckBox showConnectionErrorsCheckbox;
    private JTextField projectId;


    public ConfigFormApp() {
        projectIdPanel.setBorder(IdeBorderFactory.createTitledBorder("GitLab Settings (Application Scope)"));
    }

    public void init() {
        config = PipelineViewerConfigApp.getInstance();
        loadSettings();

        createValidators(gitlabUrlField, projectId);
    }

    static void createValidators(JTextField gitlabUrlField, JTextField projectId) {
        new ComponentValidator(Objects.requireNonNull(DialogWrapper.findInstanceFromFocus()).getDisposable()).withValidator(() -> {
            boolean valid = Strings.isNullOrEmpty(gitlabUrlField.getText()) || UrlValidator.getInstance().isValid(gitlabUrlField.getText());
            if (!valid) {
                return new ValidationInfo("The gitlab URL is not valid", gitlabUrlField);
            }
            return null;
        }).installOn(gitlabUrlField);

        new ComponentValidator(Objects.requireNonNull(DialogWrapper.findInstanceFromFocus()).getDisposable()).withValidator(() -> {
            if (Strings.isNullOrEmpty(projectId.getText())) {
                return null;
            }
            try {
                Integer.parseInt(projectId.getText());
            } catch (NumberFormatException e) {
                return new ValidationInfo("Must be a number", projectId);
            }
            return null;
        }).installOn(projectId);
        gitlabUrlField.getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull DocumentEvent e) {
                ComponentValidator.getInstance(gitlabUrlField).ifPresent(ComponentValidator::revalidate);
            }
        });
        projectId.getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull DocumentEvent e) {
                ComponentValidator.getInstance(projectId).ifPresent(ComponentValidator::revalidate);
            }
        });
    }


    public void apply() {
        config.setGitlabUrl(gitlabUrlField.getText());
        config.setGitlabAuthToken(authTokenField.getText());
        config.setGitlabProjectId(projectId.getText());
        config.setMergeRequestTargetBranch(mergeRequestTargetBranch.getText());
        config.setShowNotificationForWatchedBranches(watchedBranchesNotificationCheckbox.isSelected());
        config.setShowConnectionErrors(showConnectionErrorsCheckbox.isSelected());
        List<String> statusesToWatch = new ArrayList<>();

        config.setStatusesToWatch(statusesToWatch);

        for (Project project : ProjectManager.getInstance().getOpenProjects()) {
            ApplicationManager.getApplication().invokeLater(() -> {
                ServiceManager.getService(project, BackgroundUpdateService.class).restartBackgroundTask();
                ServiceManager.getService(project, LightsControl.class).initialize(project);
            });
        }
    }

    public void loadSettings() {
        gitlabUrlField.setText(config.getGitlabUrl());
        authTokenField.setText(config.getGitlabAuthToken());
        if (config.getGitlabProjectId() != null) {
            projectId.setText(config.getGitlabProjectId() == null ? null : String.valueOf(config.getGitlabProjectId()));
        }
        watchedBranchesNotificationCheckbox.setSelected(config.isShowNotificationForWatchedBranches());
        showConnectionErrorsCheckbox.setSelected(config.isShowConnectionErrorNotifications());

        mergeRequestTargetBranch.setText(config.getMergeRequestTargetBranch());
    }

    public boolean isModified() {
        return !Objects.equals(gitlabUrlField.getText(), config.getGitlabUrl())
                || !Objects.equals(config.getGitlabProjectId(), Strings.isNullOrEmpty(projectId.getText()) ? null : Integer.parseInt(projectId.getText()))
                || !Objects.equals(config.getGitlabAuthToken(), authTokenField.getText())
                || !Objects.equals(config.getMergeRequestTargetBranch(), mergeRequestTargetBranch.getText())
                || !Objects.equals(config.isShowNotificationForWatchedBranches(), watchedBranchesNotificationCheckbox.isSelected())
                || !Objects.equals(config.isShowConnectionErrorNotifications(), showConnectionErrorsCheckbox.isSelected())
                ;
    }

    public JPanel getMainPanel() {
        return mainPanel;
    }

    private void createUIComponents() {
    }


}
