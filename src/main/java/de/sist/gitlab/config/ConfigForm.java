package de.sist.gitlab.config;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBList;
import de.sist.gitlab.BackgroundUpdateService;
import de.sist.gitlab.lights.LightsControl;

import javax.swing.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@SuppressWarnings("unused")
public class ConfigForm {

    private PipelineViewerConfigProject projectConfig;
    private Project project;

    private JPanel mainPanel;
    private JPanel branchesToIgnorePanel;
    private JPanel branchesToWatchPanel;

    private JList<String> branchesToIgnoreList;

    private JPanel projectConfigPanel;
    private JLabel lightsLabel;
    private JTextField lightsBranch;
    private JTextField mergeRequestTargetBranch;
    private JCheckBox enabledCheckbox;
    private JList<String> branchesToWatchList;

    private CollectionListModel<String> branchesToIgnoreListModel;
    private CollectionListModel<String> branchesToWatchListModel;

    public ConfigForm() {
        createBranchesToIgnorePanel();
        createBranchesToWatchPanel();

        projectConfigPanel.setBorder(IdeBorderFactory.createTitledBorder("GitLab Settings (Project Scope)"));
    }

    public void init(Project project) {
        this.project = project;
        projectConfig = PipelineViewerConfigProject.getInstance(project);
        loadSettings();
    }

    public void apply() {
        projectConfig.setBranchesToIgnore(branchesToIgnoreListModel.toList());
        projectConfig.setBranchesToWatch(branchesToWatchListModel.toList());
        projectConfig.setMergeRequestTargetBranch(mergeRequestTargetBranch.getText());
        projectConfig.setEnabled(enabledCheckbox.isSelected());
        List<String> statusesToWatch = new ArrayList<>();

        projectConfig.setShowLightsForBranch(lightsBranch.getText());

        ApplicationManager.getApplication().invokeLater(() -> {
            ServiceManager.getService(project, BackgroundUpdateService.class).restartBackgroundTask();
            ServiceManager.getService(project, LightsControl.class).initialize(project);
        });
        ApplicationManager.getApplication().getMessageBus().syncPublisher(ConfigChangedListener.CONFIG_CHANGED).configChanged();
    }

    public void loadSettings() {
        branchesToWatchListModel.replaceAll(projectConfig.getBranchesToWatch().stream().sorted().collect(Collectors.toList()));
        branchesToIgnoreListModel.replaceAll(projectConfig.getBranchesToIgnore().stream().sorted().collect(Collectors.toList()));
        mergeRequestTargetBranch.setText(projectConfig.getMergeRequestTargetBranch());
        enabledCheckbox.setSelected(projectConfig.isEnabled());

        lightsBranch.setText(projectConfig.getShowLightsForBranch());
    }

    public boolean isModified() {
        return
                !Objects.equals(projectConfig.getShowLightsForBranch(), lightsBranch.getText())
                        || !Objects.equals(projectConfig.getMergeRequestTargetBranch(), mergeRequestTargetBranch.getText())
                        || !new HashSet<>(branchesToWatchListModel.getItems()).equals(new HashSet<>(projectConfig.getBranchesToWatch()))
                        || !new HashSet<>(branchesToIgnoreListModel.getItems()).equals(new HashSet<>(projectConfig.getBranchesToIgnore()))
                        || enabledCheckbox.isSelected() != projectConfig.isEnabled()
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
        branchesToWatchPanel.setBorder(IdeBorderFactory.createTitledBorder("Branches / Tags to Always Watch"));

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
        branchesToIgnorePanel.setBorder(IdeBorderFactory.createTitledBorder("Branches / Tags to Ignore"));
    }

}
