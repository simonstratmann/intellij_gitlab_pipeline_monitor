package de.sist.gitlab.pipelinemonitor.config;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.ComponentValidator;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBList;
import de.sist.gitlab.pipelinemonitor.BackgroundUpdateService;
import de.sist.gitlab.pipelinemonitor.lights.LightsControl;
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

    private JPanel appConfigPanel;
    private JTextField mergeRequestTargetBranch;
    private JCheckBox watchedBranchesNotificationCheckbox;
    private JCheckBox showConnectionErrorsCheckbox;
    private JPanel mappingsPanel;
    private JPanel ignoredRemotesPanel;
    private JCheckBox showForTagsCheckBox;
    private JTextField urlOpenerTextbox;
    private JRadioButton radioDisplayTypeIcons;
    private JRadioButton radioDisplayTypeIds;
    private JRadioButton radioDisplayTypeLinks;
    private JTextField connectTimeout;
    private final CollectionListModel<String> mappingsModel = new CollectionListModel<>();
    private final CollectionListModel<String> ignoredRemotesModel = new CollectionListModel<>();


    public ConfigFormApp() {
        appConfigPanel.setBorder(IdeBorderFactory.createTitledBorder("GitLab Settings (Application Scope)"));
    }

    public void init() {
        config = PipelineViewerConfigApp.getInstance();
        loadSettings();
        createMappingsPanel();
        createIgnoredRemotesPanel();
        ButtonGroup buttonGroup = new ButtonGroup();
        buttonGroup.add(radioDisplayTypeIcons);
        buttonGroup.add(radioDisplayTypeLinks);
        buttonGroup.add(radioDisplayTypeIds);
        new ComponentValidator(DialogWrapper.findInstanceFromFocus().getDisposable()).withValidator(() -> {
            try {
                Integer.parseInt(connectTimeout.getText());
                return null;
            } catch (NumberFormatException nex) {
                return new ValidationInfo("Please enter a numeric value", connectTimeout);
            }
        }).installOn(connectTimeout);
        connectTimeout.getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull DocumentEvent e) {
                ComponentValidator.getInstance(connectTimeout).ifPresent(ComponentValidator::revalidate);
            }
        });
    }

    public void apply() {
        config.setMergeRequestTargetBranch(mergeRequestTargetBranch.getText());
        config.setShowNotificationForWatchedBranches(watchedBranchesNotificationCheckbox.isSelected());
        config.setShowConnectionErrors(showConnectionErrorsCheckbox.isSelected());
        config.setShowForTags(showForTagsCheckBox.isSelected());
        config.getMappings().clear();
        config.getMappings().addAll(mappingsModel.getItems().stream().map(Mapping::toMapping).collect(Collectors.toList()));
        config.getIgnoredRemotes().clear();
        config.getIgnoredRemotes().addAll(ignoredRemotesModel.getItems());
        config.setUrlOpenerCommand(urlOpenerTextbox.getText());
        config.setConnectTimeout(Integer.parseInt(connectTimeout.getText()));
        if (radioDisplayTypeIds.isSelected()) {
            config.setDisplayType(PipelineViewerConfigApp.DisplayType.ID);
        } else if (radioDisplayTypeIcons.isSelected()) {
            config.setDisplayType(PipelineViewerConfigApp.DisplayType.ICON);
        } else {
            config.setDisplayType(PipelineViewerConfigApp.DisplayType.LINK);
        }

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
        watchedBranchesNotificationCheckbox.setSelected(config.isShowNotificationForWatchedBranches());
        showConnectionErrorsCheckbox.setSelected(config.isShowConnectionErrorNotifications());
        mergeRequestTargetBranch.setText(config.getMergeRequestTargetBranch());
        showForTagsCheckBox.setSelected(config.isShowForTags());
        urlOpenerTextbox.setText(config.getUrlOpenerCommand());
        radioDisplayTypeIcons.setSelected(config.getDisplayType() == PipelineViewerConfigApp.DisplayType.ICON);
        radioDisplayTypeIds.setSelected(config.getDisplayType() == PipelineViewerConfigApp.DisplayType.ID);
        radioDisplayTypeLinks.setSelected(config.getDisplayType() == PipelineViewerConfigApp.DisplayType.LINK);
        connectTimeout.setText(String.valueOf(config.getConnectTimeout()));

        mappingsModel.replaceAll(config.getMappings().stream()
                .map(Mapping::toSerializable)
                .sorted()
                .collect(Collectors.toList()));
        ignoredRemotesModel.replaceAll(ConfigProvider.getInstance().getIgnoredRemotes().stream().sorted().collect(Collectors.toList()));
    }

    public boolean isModified() {
        return !mappingsModel.getItems().stream().map(Mapping::toMapping).collect(Collectors.toList()).equals(config.getMappings())
                || !Objects.equals(config.getMergeRequestTargetBranch(), mergeRequestTargetBranch.getText())
                || !Objects.equals(config.isShowNotificationForWatchedBranches(), watchedBranchesNotificationCheckbox.isSelected())
                || !Objects.equals(config.isShowConnectionErrorNotifications(), showConnectionErrorsCheckbox.isSelected())
                || !Objects.equals(config.isShowForTags(), showForTagsCheckBox.isSelected())
                || !Objects.equals(config.getUrlOpenerCommand(), urlOpenerTextbox.getText())
                || !Objects.equals(new HashSet<>(ConfigProvider.getInstance().getIgnoredRemotes()), new HashSet<>(ignoredRemotesModel.getItems()))
                || config.getConnectTimeout() != Integer.parseInt(connectTimeout.getText())
                || radioDisplayTypeIcons.isSelected() && config.getDisplayType() != PipelineViewerConfigApp.DisplayType.ICON
                || radioDisplayTypeIds.isSelected() && config.getDisplayType() != PipelineViewerConfigApp.DisplayType.ID
                || radioDisplayTypeLinks.isSelected() && config.getDisplayType() != PipelineViewerConfigApp.DisplayType.LINK
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


        final AnActionButton tokenButton = new AnActionButton("Set Access Token") {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                for (String mappingString : mappingList.getSelectedValuesList()) {
                    final Mapping mapping = Mapping.toMapping(mappingString);
                    final String oldAccessToken = ConfigProvider.getToken(mapping);
                    final String newAccessToken = Messages.showInputDialog("Please enter the access token for " + mapping.getHost(), "Gitlab Pipeline Viewer", null, oldAccessToken, null);
                    if (newAccessToken != null) {
                        //Cancel
                        ConfigProvider.saveToken(mapping, newAccessToken);
                    }
                }
            }

        };
        tokenButton.addCustomUpdater(e -> !mappingList.getSelectedValuesList().isEmpty());
        decorator.addExtraAction(tokenButton);
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
