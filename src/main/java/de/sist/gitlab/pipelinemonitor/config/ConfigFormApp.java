package de.sist.gitlab.pipelinemonitor.config;

import com.google.common.base.Strings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.ComponentValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBList;
import de.sist.gitlab.pipelinemonitor.BackgroundUpdateService;
import de.sist.gitlab.pipelinemonitor.lights.LightsControl;
import de.sist.gitlab.pipelinemonitor.ui.TokenDialog;
import org.apache.commons.lang3.tuple.Pair;
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
    private JRadioButton radioMrPipelineBranchName;
    private JRadioButton radioMRPipelineTitle;
    private JTextField mrPipelinePrefixTextbox;
    private JTextField maxTags;
    private JLabel maxTagsLabel;
    private JCheckBox checkBoxForBranchesWhichExist;
    private JTextField maxAgeDays;
    private JTextField textFieldAlwaysMonitor;
    private JCheckBox checkBoxShowProgressBar;
    private JTextField refreshDelay;
    private final CollectionListModel<String> mappingsModel = new CollectionListModel<>();
    private final CollectionListModel<String> ignoredRemotesModel = new CollectionListModel<>();

    Disposable disposable;

    public ConfigFormApp() {
        appConfigPanel.setBorder(IdeBorderFactory.createTitledBorder("GitLab Settings (Application Scope)"));
    }


    public void init() {
        config = PipelineViewerConfigApp.getInstance();
        loadSettings();
        createMappingsPanel();
        createIgnoredRemotesPanel();

        ButtonGroup displayTypeButtonGroup = new ButtonGroup();
        displayTypeButtonGroup.add(radioDisplayTypeIcons);
        displayTypeButtonGroup.add(radioDisplayTypeLinks);
        displayTypeButtonGroup.add(radioDisplayTypeIds);

        ButtonGroup mrPipelineBttonGroup = new ButtonGroup();
        mrPipelineBttonGroup.add(radioMRPipelineTitle);
        mrPipelineBttonGroup.add(radioMrPipelineBranchName);

        disposable = Disposer.newDisposable();

        addPositiveNumberValidator(maxAgeDays, true);
        addPositiveNumberValidator(connectTimeout, false);
        addPositiveNumberValidator(maxTags, true);
        addPositiveNumberValidator(refreshDelay, false);


        showForTagsCheckBox.addChangeListener(e -> {
            maxTags.setVisible(showForTagsCheckBox.isSelected());
            maxTagsLabel.setVisible(showForTagsCheckBox.isSelected());
        });
    }

    private void addPositiveNumberValidator(JTextField field, boolean isNullAllowed) {
        new ComponentValidator(disposable).withValidator(() -> {
            if (field.getText() == null || field.getText().isBlank()) {
                if (isNullAllowed) {
                    return null;
                } else {
                    return new ValidationInfo("Please enter a positive value", field);
                }
            }
            try {
                if (Integer.parseInt(field.getText()) <= 0) {
                    return new ValidationInfo("Please enter a positive value", field);
                }
            } catch (NumberFormatException e) {
                return new ValidationInfo("Please enter a numeric value", field);
            }
            return null;

        }).installOn(field);
        field.getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull DocumentEvent e) {
                ComponentValidator.getInstance(field).ifPresent(ComponentValidator::revalidate);
            }
        });
    }


    public void apply() {
        config.setMergeRequestTargetBranch(mergeRequestTargetBranch.getText());
        config.setShowNotificationForWatchedBranches(watchedBranchesNotificationCheckbox.isSelected());
        config.setShowConnectionErrors(showConnectionErrorsCheckbox.isSelected());
        config.setShowForTags(showForTagsCheckBox.isSelected());
        config.maxLatestTags = Strings.isNullOrEmpty(maxTags.getText()) ? null : Integer.parseInt(maxTags.getText());
        config.mappings.clear();
        config.mappings.addAll(mappingsModel.getItems().stream().map(Mapping::toMapping).toList());
        config.ignoredRemotes.clear();
        config.ignoredRemotes.addAll(ignoredRemotesModel.getItems());
        config.urlOpenerCommand = urlOpenerTextbox.getText();
        config.connectTimeout = Integer.parseInt(connectTimeout.getText());
        if (radioDisplayTypeIds.isSelected()) {
            config.setDisplayType(PipelineViewerConfigApp.DisplayType.ID);
        } else if (radioDisplayTypeIcons.isSelected()) {
            config.setDisplayType(PipelineViewerConfigApp.DisplayType.ICON);
        } else {
            config.setDisplayType(PipelineViewerConfigApp.DisplayType.LINK);
        }
        config.setMrPipelineDisplayType(radioMRPipelineTitle.isSelected() ? PipelineViewerConfigApp.MrPipelineDisplayType.TITLE : PipelineViewerConfigApp.MrPipelineDisplayType.SOURCE_BRANCH);
        config.mrPipelinePrefix = mrPipelinePrefixTextbox.getText();
        config.maxAgeDays = Strings.isNullOrEmpty(maxAgeDays.getText()) ? null : Integer.parseInt(maxAgeDays.getText());
        config.refreshDelay = Strings.isNullOrEmpty(refreshDelay.getText()) ? 30 : Integer.parseInt(refreshDelay.getText());
        config.setOnlyForRemoteBranchesExist(checkBoxForBranchesWhichExist.isSelected());
        config.setAlwaysMonitorHostsFromString(textFieldAlwaysMonitor.getText());
        config.setShowProgressBar(checkBoxShowProgressBar.isSelected());

        List<String> statusesToWatch = new ArrayList<>();

        config.setStatusesToWatch(statusesToWatch);

        for (Project project : ProjectManager.getInstance().getOpenProjects()) {
            ApplicationManager.getApplication().invokeLater(() -> {
                project.getService(BackgroundUpdateService.class).restartBackgroundTask();
                project.getService(LightsControl.class).initialize(project);
            });
        }
        ApplicationManager.getApplication().getMessageBus().syncPublisher(ConfigChangedListener.CONFIG_CHANGED).configChanged();
    }

    public void loadSettings() {
        watchedBranchesNotificationCheckbox.setSelected(config.isShowNotificationForWatchedBranches());
        showConnectionErrorsCheckbox.setSelected(config.isShowConnectionErrorNotifications());
        mergeRequestTargetBranch.setText(config.getMergeRequestTargetBranch());
        showForTagsCheckBox.setSelected(config.isShowForTags());
        maxTags.setText(config.maxLatestTags == null ? null : String.valueOf(config.maxLatestTags));
        urlOpenerTextbox.setText(config.urlOpenerCommand);
        radioDisplayTypeIcons.setSelected(config.getDisplayType() == PipelineViewerConfigApp.DisplayType.ICON);
        radioDisplayTypeIds.setSelected(config.getDisplayType() == PipelineViewerConfigApp.DisplayType.ID);
        radioDisplayTypeLinks.setSelected(config.getDisplayType() == PipelineViewerConfigApp.DisplayType.LINK);
        connectTimeout.setText(String.valueOf(config.connectTimeout));
        radioMRPipelineTitle.setSelected(config.getMrPipelineDisplayType() == PipelineViewerConfigApp.MrPipelineDisplayType.TITLE);
        radioMrPipelineBranchName.setSelected(config.getMrPipelineDisplayType() == PipelineViewerConfigApp.MrPipelineDisplayType.SOURCE_BRANCH);
        mrPipelinePrefixTextbox.setText(config.mrPipelinePrefix);
        maxAgeDays.setText(config.maxAgeDays == null ? null : String.valueOf(config.maxAgeDays));
        refreshDelay.setText(String.valueOf(config.refreshDelay));
        checkBoxForBranchesWhichExist.setSelected(config.isOnlyForRemoteBranchesExist());
        textFieldAlwaysMonitor.setText(config.getAlwaysMonitorHostsAsString());
        checkBoxShowProgressBar.setSelected(config.isShowProgressBar());

        mappingsModel.replaceAll(config.mappings.stream()
                .map(Mapping::toSerializable)
                .sorted()
                .collect(Collectors.toList()));
        ignoredRemotesModel.replaceAll(ConfigProvider.getInstance().getIgnoredRemotes().stream().sorted().collect(Collectors.toList()));
        maxTags.setVisible(showForTagsCheckBox.isSelected());
        maxTagsLabel.setVisible(showForTagsCheckBox.isSelected());
    }

    public boolean isModified() {
        return !mappingsModel.getItems().stream().map(Mapping::toMapping).toList().equals(config.mappings)
               || ConfigProvider.isNotEqualIgnoringEmptyOrNull(config.getMergeRequestTargetBranch(), mergeRequestTargetBranch.getText())
               || !Objects.equals(config.isShowNotificationForWatchedBranches(), watchedBranchesNotificationCheckbox.isSelected())
               || !Objects.equals(config.isShowConnectionErrorNotifications(), showConnectionErrorsCheckbox.isSelected())
               || !Objects.equals(config.isShowForTags(), showForTagsCheckBox.isSelected())
               || isDifferentNumber(maxTags.getText(), config.maxLatestTags)
               || ConfigProvider.isNotEqualIgnoringEmptyOrNull(config.urlOpenerCommand, urlOpenerTextbox.getText())
               || !Objects.equals(new HashSet<>(ConfigProvider.getInstance().getIgnoredRemotes()), new HashSet<>(ignoredRemotesModel.getItems()))
               || isDifferentNumber(connectTimeout.getText(), config.connectTimeout)
               || radioDisplayTypeIcons.isSelected() && config.getDisplayType() != PipelineViewerConfigApp.DisplayType.ICON
               || radioDisplayTypeIds.isSelected() && config.getDisplayType() != PipelineViewerConfigApp.DisplayType.ID
               || radioDisplayTypeLinks.isSelected() && config.getDisplayType() != PipelineViewerConfigApp.DisplayType.LINK
               || radioMrPipelineBranchName.isSelected() && config.getMrPipelineDisplayType() != PipelineViewerConfigApp.MrPipelineDisplayType.SOURCE_BRANCH
               || !Objects.equals(config.mrPipelinePrefix, mrPipelinePrefixTextbox.getText())
               || !Objects.equals(config.isOnlyForRemoteBranchesExist(), checkBoxForBranchesWhichExist.isSelected())
               || isDifferentNumber(maxAgeDays.getText(), config.maxAgeDays)
               || isDifferentNumber(refreshDelay.getText(), config.refreshDelay)
               || !Objects.equals(config.getAlwaysMonitorHostsAsString(), textFieldAlwaysMonitor.getText())
               || config.isShowProgressBar() != checkBoxShowProgressBar.isSelected()
                ;
    }

    private boolean isDifferentNumber(String numberAsString, Integer number) {
        //null,1
        //1,null
        if (Strings.isNullOrEmpty(numberAsString) ^ number == null) {
            return true;
        }
        //null, null
        if (Strings.isNullOrEmpty(numberAsString) && number == null) {
            return false;
        }
        //1,1
        return !Integer.valueOf(numberAsString).equals(number);
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
                PipelineViewerConfigApp.getInstance().remotesAskAgainNextTime.remove(Mapping.toMapping(s).getRemote());
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


        final AnAction tokenAction = new AnAction("Set Access Token") {

            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                for (String mappingString : mappingList.getSelectedValuesList()) {
                    final Mapping mapping = Mapping.toMapping(mappingString);
                    final Pair<String, TokenType> tokenAndType = ConfigProvider.getTokenAndType(mapping.getRemote(), mapping.getHost());
                    final String token = tokenAndType.getLeft();
                    final TokenType tokenType = tokenAndType.getRight();
                    final TokenType preselectedTokenType = token == null ? TokenType.PERSONAL : tokenType;
                    new TokenDialog.Wrapper(e.getProject(),
                            "Please enter the access token for " + mapping.getRemote(),
                            token,
                            preselectedTokenType,
                            response -> ConfigProvider.saveToken(mapping, response.getLeft(), response.getRight(), e.getProject()))
                            .show();
                }
            }

            @Override
            public @NotNull ActionUpdateThread getActionUpdateThread() {
                return ActionUpdateThread.BGT;
            }

            @Override
            public void update(@NotNull AnActionEvent e) {
                e.getPresentation().setEnabled(!mappingList.getSelectedValuesList().isEmpty());
            }
        };

        decorator.addExtraAction(tokenAction);
        mappingsPanel.add(decorator.createPanel());
        mappingsPanel.setBorder(IdeBorderFactory.createTitledBorder("Git Remote To Gitlab Project Mapping"));
    }

    private void createIgnoredRemotesPanel() {
        JBList<String> ignoredRemotesList = new JBList<>(ignoredRemotesModel);

        ToolbarDecorator decorator = ToolbarDecorator.createDecorator(ignoredRemotesList, ignoredRemotesModel);
        decorator.setRemoveAction(anActionButton -> {
            for (String s : ignoredRemotesList.getSelectedValuesList()) {
                ignoredRemotesModel.remove(s);
                PipelineViewerConfigApp.getInstance().remotesAskAgainNextTime.remove(s);
            }
        });

        ignoredRemotesPanel.add(decorator.createPanel());
        ignoredRemotesPanel.setBorder(IdeBorderFactory.createTitledBorder("Ignored Remotes"));
    }


    private void createUIComponents() {
        // TODO: place custom component creation code here
    }
}
