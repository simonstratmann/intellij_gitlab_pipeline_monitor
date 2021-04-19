package de.sist.gitlab.config;

import com.google.common.base.Strings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.ComponentValidator;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBList;
import de.sist.gitlab.BackgroundUpdateService;
import de.sist.gitlab.lights.LightsControl;
import de.sist.gitlab.validator.UrlValidator;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

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
    private JPanel mappingsPanel;
    private JPanel ignoredRemotesPanel;
    private final CollectionListModel<String> mappingsModel = new CollectionListModel<>();
    private final CollectionListModel<String> ignoredRemotesModel = new CollectionListModel<>();


    public ConfigFormApp() {
        projectIdPanel.setBorder(IdeBorderFactory.createTitledBorder("GitLab Settings (Application Scope)"));
    }

    public void init() {
        config = PipelineViewerConfigApp.getInstance();
        loadSettings();
        createMappingsPanel();
        createIgnoredRemotesPanel();

        createValidator(gitlabUrlField);
    }

    static void createValidator(JTextField gitlabUrlField) {
        new ComponentValidator(Objects.requireNonNull(DialogWrapper.findInstanceFromFocus()).getDisposable()).withValidator(() -> {
            boolean valid = Strings.isNullOrEmpty(gitlabUrlField.getText()) || UrlValidator.getInstance().isValid(gitlabUrlField.getText());
            if (!valid) {
                return new ValidationInfo("The gitlab URL doesn't seem to be valid", gitlabUrlField).asWarning();
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
        config.setMergeRequestTargetBranch(mergeRequestTargetBranch.getText());
        config.setShowNotificationForWatchedBranches(watchedBranchesNotificationCheckbox.isSelected());
        config.setShowConnectionErrors(showConnectionErrorsCheckbox.isSelected());
        config.setMappings(mappingsModel.getItems().stream().map(Mapping::toMapping).filter(Objects::nonNull).collect(Collectors.toList()));
        config.setIgnoredRemotes(ignoredRemotesModel.getItems());
        List<String> statusesToWatch = new ArrayList<>();

        config.setStatusesToWatch(statusesToWatch);

        for (Project project : ProjectManager.getInstance().getOpenProjects()) {
            ApplicationManager.getApplication().invokeLater(() -> {
                ServiceManager.getService(project, BackgroundUpdateService.class).restartBackgroundTask();
                ServiceManager.getService(project, LightsControl.class).initialize(project);
            });
        }
        ApplicationManager.getApplication().getMessageBus().syncPublisher(ConfigChangedListener.CONFIG_CHANGED).configChanged();
    }

    public void loadSettings() {
        gitlabUrlField.setText(config.getGitlabUrl());
        authTokenField.setText(config.getGitlabAuthToken());
        watchedBranchesNotificationCheckbox.setSelected(config.isShowNotificationForWatchedBranches());
        showConnectionErrorsCheckbox.setSelected(config.isShowConnectionErrorNotifications());
        mergeRequestTargetBranch.setText(config.getMergeRequestTargetBranch());

        mappingsModel.removeAll();
        config.getMappings().stream().map(Mapping::toSerializable).forEach(mappingsModel::add);

        ignoredRemotesModel.removeAll();
        config.getIgnoredRemotes().forEach(ignoredRemotesModel::add);
    }

    public boolean isModified() {
        return !Objects.equals(gitlabUrlField.getText(), config.getGitlabUrl())
                || !mappingsModel.getItems().stream().map(Mapping::toMapping).filter(Objects::nonNull).collect(Collectors.toList()).equals(config.getMappings())
                || !Objects.equals(config.getGitlabAuthToken(), authTokenField.getText())
                || !Objects.equals(config.getMergeRequestTargetBranch(), mergeRequestTargetBranch.getText())
                || !Objects.equals(config.isShowNotificationForWatchedBranches(), watchedBranchesNotificationCheckbox.isSelected())
                || !Objects.equals(config.isShowConnectionErrorNotifications(), showConnectionErrorsCheckbox.isSelected())
                || !Objects.equals(new HashSet<>(config.getIgnoredRemotes()), new HashSet<>(ignoredRemotesModel.getItems()))
                ;
    }

    public JPanel getMainPanel() {
        return mainPanel;
    }

    private void createMappingsPanel() {
        JBList<String> mappingList = new JBList<>(mappingsModel);

        ToolbarDecorator decorator = ToolbarDecorator.createDecorator(mappingList, mappingsModel);
        decorator.setAddAction(anActionButton -> {
            String newMapping = Messages.showInputDialog("Please enter the mapping using the format '<git remote>=<Project ID>'.", "New Mapping", null);
            if (newMapping != null) {
                mappingsModel.add(newMapping);
            }
        });
        decorator.setRemoveAction(anActionButton -> {
            for (String s : mappingList.getSelectedValuesList()) {
                mappingsModel.remove(s);
            }
        });
        decorator.setEditAction(anActionButton -> {
            if (mappingList.getSelectedValuesList().size() != 1) {
                return;
            }
            final String selectedValueBefore = mappingList.getSelectedValuesList().get(0);
            String newValue = Messages.showInputDialog("Please enter the new value (format '<git remote>=<Project ID>').", "Change Mapping", null, selectedValueBefore, null);
            if (newValue != null) {
                mappingsModel.remove(selectedValueBefore);
                mappingsModel.add(newValue);
            }
        });
        mappingsPanel.add(decorator.createPanel());
        mappingsPanel.setBorder(IdeBorderFactory.createTitledBorder("Git Remote To Gitlab Project Mapping"));
    }

    private void createIgnoredRemotesPanel() {
        JBList<String> ignoredRemotesList = new JBList<>(ignoredRemotesModel);

        ToolbarDecorator decorator = ToolbarDecorator.createDecorator(ignoredRemotesList, ignoredRemotesModel);
        decorator.setRemoveAction(anActionButton -> {
            for (String s : ignoredRemotesList.getSelectedValuesList()) {
                ignoredRemotesModel.remove(s);
            }
        });

        ignoredRemotesPanel.add(decorator.createPanel());
        ignoredRemotesPanel.setBorder(IdeBorderFactory.createTitledBorder("Ignored Remotes"));
    }


}
