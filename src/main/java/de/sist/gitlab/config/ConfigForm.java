package de.sist.gitlab.config;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComponentValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBList;
import de.sist.gitlab.BackgroundUpdateService;
import org.apache.commons.validator.routines.UrlValidator;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

@SuppressWarnings("unused")
public class ConfigForm {

    private PipelineViewerConfig config;
    private Project project;

    private boolean modified = false;

    private JPanel mainPanel;
    private JSpinner projectIdSpinner;
    private JPanel branchesToIgnorePanel;
    private JPanel branchesToWatchPanel;

    private JList<String> branchesToIgnoreList;

    private JPanel statesToNotifyPanel;
    private JCheckBox showStatusPendingCheckbox;
    private JCheckBox showStatusCanceledCheckbox;
    private JCheckBox showStatusRunningCheckbox;
    private JCheckBox showStatusSkippedCheckbox;
    private JCheckBox showStatusFailedCheckbox;
    private JCheckBox showStatusSuccessCheckbox;
    private JPanel projectIdPanel;
    private JTextField gitlabUrlField;
    private JTextField authTokenField;
    private JList<String> branchesToWatchList;
    private JPanel statesToNotify2;

    private CollectionListModel<String> branchesToIgnoreListModel;
    private CollectionListModel<String> branchesToWatchListModel;

    public ConfigForm() {
        createBranchesToIgnorePanel();
        createBranchesToWatchPanel();

        statesToNotifyPanel.setBorder(IdeBorderFactory.createTitledBorder("Statuses to notify"));
        projectIdPanel.setBorder(IdeBorderFactory.createTitledBorder("GitLab settings"));
    }


    public void init(Project project) {
        this.project = project;
        config = PipelineViewerConfig.getInstance(project);
        loadSettings();

        new ComponentValidator(project).withValidator(() -> {
            boolean valid = UrlValidator.getInstance().isValid(gitlabUrlField.getText());
            if (!valid) {
                return new ValidationInfo("The gitlab URL is not valid", gitlabUrlField);
            }
            return null;
        }).installOn(gitlabUrlField);
        gitlabUrlField.getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull DocumentEvent e) {
                ComponentValidator.getInstance(gitlabUrlField).ifPresent(ComponentValidator::revalidate);
            }
        });
    }

    public void apply() {
        config.setGitlabUrl(gitlabUrlField.getText());
        config.setGitlabAuthToken(authTokenField.getText());
        config.setGitlabProjectId((Integer) projectIdSpinner.getValue());
        config.setBranchesToIgnore(branchesToIgnoreListModel.toList());
        config.setBranchesToWatch(branchesToWatchListModel.toList());
        List<String> statusesToWatch = new ArrayList<>();
        if (showStatusCanceledCheckbox.isSelected()) {
            statusesToWatch.add("canceled");
        }
        if (showStatusFailedCheckbox.isSelected()) {
            statusesToWatch.add("failed");
        }
        if (showStatusPendingCheckbox.isSelected()) {
            statusesToWatch.add("pending");
        }
        if (showStatusRunningCheckbox.isSelected()) {
            statusesToWatch.add("running");
        }
        if (showStatusSkippedCheckbox.isSelected()) {
            statusesToWatch.add("skipped");
        }
        if (showStatusSuccessCheckbox.isSelected()) {
            statusesToWatch.add("success");
        }
        config.setStatusesToWatch(statusesToWatch);

        ApplicationManager.getApplication().invokeLater(() -> project.getService(BackgroundUpdateService.class).restartBackgroundTask());
    }

    public void loadSettings() {
        gitlabUrlField.setText(config.getGitlabUrl());
        authTokenField.setText(config.getGitlabAuthToken());
        if (config.getGitlabProjectId() != null) {
            projectIdSpinner.setValue(config.getGitlabProjectId());
        }
        branchesToWatchListModel.replaceAll(config.getBranchesToWatch());
        branchesToIgnoreListModel.replaceAll(config.getBranchesToIgnore());
        showStatusCanceledCheckbox.setSelected(config.getStatusesToWatch().contains("canceled"));
        showStatusFailedCheckbox.setSelected(config.getStatusesToWatch().contains("failed"));
        showStatusPendingCheckbox.setSelected(config.getStatusesToWatch().contains("pending"));
        showStatusRunningCheckbox.setSelected(config.getStatusesToWatch().contains("running"));
        showStatusSkippedCheckbox.setSelected(config.getStatusesToWatch().contains("skipped"));
        showStatusSuccessCheckbox.setSelected(config.getStatusesToWatch().contains("success"));
    }

    public boolean isModified() {
        return
                !Objects.equals(gitlabUrlField.getText(), config.getGitlabUrl())
                        || !Objects.equals(config.getGitlabProjectId(), projectIdSpinner.getValue())
                        || !Objects.equals(config.getGitlabAuthToken(), authTokenField.getText())
                        || !new HashSet<>(branchesToWatchListModel.getItems()).equals(new HashSet<>(config.getBranchesToWatch()))
                        || !new HashSet<>(branchesToIgnoreListModel.getItems()).equals(new HashSet<>(config.getBranchesToIgnore()))
                        || showStatusCanceledCheckbox.isSelected() != config.getStatusesToWatch().contains("canceled")
                        || showStatusFailedCheckbox.isSelected() != config.getStatusesToWatch().contains("failed")
                        || showStatusPendingCheckbox.isSelected() != config.getStatusesToWatch().contains("pending")
                        || showStatusRunningCheckbox.isSelected() != config.getStatusesToWatch().contains("running")
                        || showStatusSkippedCheckbox.isSelected() != config.getStatusesToWatch().contains("skipped")
                        || showStatusSuccessCheckbox.isSelected() != config.getStatusesToWatch().contains("success");
    }

    public JPanel getMainPanel() {
        return mainPanel;
    }

    private void createUIComponents() {
    }

    private void createBranchesToWatchPanel() {
        branchesToWatchListModel = new CollectionListModel<>();
        branchesToWatchList = new JBList<>(branchesToWatchListModel);

        ToolbarDecorator decorator = ToolbarDecorator.createDecorator(branchesToWatchList, branchesToWatchListModel);
        decorator.setAddAction(anActionButton -> {
            String watchBranch = Messages.showInputDialog("Please enter the name of a branch which should always be watched", "Always Watch Branch", null);
            if (watchBranch != null) {
                branchesToWatchListModel.add(watchBranch);
            }
        });
        decorator.setRemoveAction(anActionButton -> {
            for (String s : branchesToWatchList.getSelectedValuesList()) {
                branchesToWatchListModel.remove(s);
            }
        });
        branchesToWatchPanel.add(decorator.createPanel());
        branchesToWatchPanel.setBorder(IdeBorderFactory.createTitledBorder("Branches to always watch"));

    }

    private void createBranchesToIgnorePanel() {
        branchesToIgnoreListModel = new CollectionListModel<>();
        JBList<String> branchesToIgnoreList = new JBList<>(branchesToIgnoreListModel);

        ToolbarDecorator decorator = ToolbarDecorator.createDecorator(branchesToIgnoreList, branchesToIgnoreListModel);
        decorator.setAddAction(anActionButton -> {
            String ignoreBranch = Messages.showInputDialog("Please enter the name of a branch which should not be watched or displayed", "Ignore Branch", null);
            if (ignoreBranch != null) {
                branchesToIgnoreListModel.add(ignoreBranch);
            }
        });
        decorator.setRemoveAction(anActionButton -> {
            for (String s : branchesToIgnoreList.getSelectedValuesList()) {
                branchesToIgnoreListModel.remove(s);
            }
        });
        branchesToIgnorePanel.add(decorator.createPanel());
        branchesToIgnorePanel.setBorder(IdeBorderFactory.createTitledBorder("Branches to ignore"));
    }
}
