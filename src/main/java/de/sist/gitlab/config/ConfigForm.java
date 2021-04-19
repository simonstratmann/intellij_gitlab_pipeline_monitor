package de.sist.gitlab.config;

import com.google.common.base.Strings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComponentValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBList;
import de.sist.gitlab.BackgroundUpdateService;
import de.sist.gitlab.lights.LightsControl;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

@SuppressWarnings("unused")
public class ConfigForm {

    private PipelineViewerConfigProject projectConfig;
    private Project project;

    private JPanel mainPanel;
    private JPanel branchesToIgnorePanel;
    private JPanel branchesToWatchPanel;

    private JList<String> branchesToIgnoreList;

    private JPanel projectIdPanel;
    private JTextField gitlabUrlField;
    private JTextField authTokenField;
    private JLabel lightsLabel;
    private JTextField lightsBranch;
    private JTextField mergeRequestTargetBranch;
    private JTextField projectId;
    private JList<String> branchesToWatchList;

    private CollectionListModel<String> branchesToIgnoreListModel;
    private CollectionListModel<String> branchesToWatchListModel;

    public ConfigForm() {
        createBranchesToIgnorePanel();
        createBranchesToWatchPanel();

        projectIdPanel.setBorder(IdeBorderFactory.createTitledBorder("GitLab Settings (Project Scope)"));
    }

    public void init(Project project) {
        this.project = project;
        projectConfig = PipelineViewerConfigProject.getInstance(project);
        projectConfig.initIfNeeded();
        loadSettings();

        ConfigFormApp.createValidators(gitlabUrlField, projectId);

        gitlabUrlField.getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull DocumentEvent e) {
                ComponentValidator.getInstance(gitlabUrlField).ifPresent(ComponentValidator::revalidate);
            }
        });

    }


    public void apply() {
        projectConfig.setGitlabUrl(gitlabUrlField.getText());
        projectConfig.setGitlabAuthToken(authTokenField.getText());
        projectConfig.setGitlabProjectId(projectId.getText());
        projectConfig.setBranchesToIgnore(branchesToIgnoreListModel.toList());
        projectConfig.setBranchesToWatch(branchesToWatchListModel.toList());
        projectConfig.setMergeRequestTargetBranch(mergeRequestTargetBranch.getText());
        List<String> statusesToWatch = new ArrayList<>();

        projectConfig.setShowLightsForBranch(lightsBranch.getText());

        ApplicationManager.getApplication().invokeLater(() -> {
            ServiceManager.getService(project, BackgroundUpdateService.class).restartBackgroundTask();
            ServiceManager.getService(project, LightsControl.class).initialize(project);
        });
    }

    public void loadSettings() {
        gitlabUrlField.setText(projectConfig.getGitlabUrl());
        authTokenField.setText(projectConfig.getGitlabAuthToken());
        if (projectConfig.getGitlabProjectId() != null) {
            projectId.setText(projectConfig.getGitlabProjectId() == null ? null : String.valueOf(projectConfig.getGitlabProjectId()));
        }
        branchesToWatchListModel.replaceAll(projectConfig.getBranchesToWatch());
        branchesToIgnoreListModel.replaceAll(projectConfig.getBranchesToIgnore());

        lightsBranch.setText(projectConfig.getShowLightsForBranch());
        mergeRequestTargetBranch.setText(projectConfig.getMergeRequestTargetBranch());
    }

    public boolean isModified() {
        return
                !Objects.equals(gitlabUrlField.getText(), projectConfig.getGitlabUrl())
                        || !Objects.equals(projectConfig.getGitlabProjectId(), Strings.isNullOrEmpty(projectId.getText()) ? null : Integer.parseInt(projectId.getText()))
                        || !Objects.equals(projectConfig.getGitlabAuthToken(), authTokenField.getText())
                        || !Objects.equals(projectConfig.getShowLightsForBranch(), lightsBranch.getText())
                        || !Objects.equals(projectConfig.getMergeRequestTargetBranch(), mergeRequestTargetBranch.getText())
                        || !new HashSet<>(branchesToWatchListModel.getItems()).equals(new HashSet<>(projectConfig.getBranchesToWatch()))
                        || !new HashSet<>(branchesToIgnoreListModel.getItems()).equals(new HashSet<>(projectConfig.getBranchesToIgnore()))
                ;
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
