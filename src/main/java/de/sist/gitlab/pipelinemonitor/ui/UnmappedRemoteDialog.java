package de.sist.gitlab.pipelinemonitor.ui;

import com.google.common.base.Strings;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.Messages;
import de.sist.gitlab.pipelinemonitor.config.ConfigProvider;

import javax.swing.*;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class UnmappedRemoteDialog extends JDialog {

    private static final Logger logger = Logger.getInstance(UnmappedRemoteDialog.class);

    private JPanel contentPane;
    private JButton buttonOK;
    private JTextField accessTokenInput;
    private JLabel label;
    private JRadioButton radioAskAgain;
    private JRadioButton radioNoAskRemote;
    private JRadioButton radioNoAskProject;
    private JRadioButton radioDoMonitor;
    private JLabel hostLabel;
    private JTextField hostInput;
    private JLabel projectLabel;
    private JTextField projectPathInput;

    private Response response;

    public UnmappedRemoteDialog(String remoteUrl) {
        logger.debug(String.format("Showing dialog for remote %s and unknown host and project", remoteUrl));
        createCommonUiComponents(remoteUrl);
        label.setText("<html>The unknown remote <b>" + remoteUrl + "</b> was detected<br>but the gitlab host and project path could not be be determined.<br>" +
                "Do you want to monitor pipelines built for the associated project?<br>" +
                "Please enter the correct gitlab host and project path and an access token if needed for access." +
                "</html>");
    }

    public UnmappedRemoteDialog(String remoteUrl, String host, String projectPath) {
        logger.debug(String.format("Showing dialog for remote %s, host %s and project path %s", remoteUrl, host, projectPath));

        projectPathInput.setText(projectPath);
        hostInput.setText(host);
        createCommonUiComponents(remoteUrl);
        label.setText("<html>The unknown remote <b>" + remoteUrl + "</b> was detected.<br>" +
                "Do you want to monitor pipelines built for the associated project?<br>" +
                "Please enter an access token if needed for access. You may correct the host and project path." +
                "</html>");
    }

    private void createCommonUiComponents(String remoteUrl) {
        setContentPane(contentPane);
        setModal(true);
        getRootPane().setDefaultButton(buttonOK);

        ButtonGroup buttonGroup = new ButtonGroup();
        buttonGroup.add(radioAskAgain);
        buttonGroup.add(radioNoAskRemote);
        buttonGroup.add(radioNoAskProject);
        buttonGroup.add(radioDoMonitor);
        radioAskAgain.setSelected(true);

        buttonOK.addActionListener(e -> onOK());

        // call onCancel() when cross is clicked
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                onCancel();
            }
        });

        // call onCancel() on ESCAPE
        contentPane.registerKeyboardAction(e -> onCancel(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        accessTokenInput.setText(ConfigProvider.getToken(remoteUrl));
    }

    private void onOK() {
        final String gitlabHost = hostInput.getText();
        final String projectPath = projectPathInput.getText();
        if (radioAskAgain.isSelected()) {
            response = new Response(Cancel.ASK_AGAIN, gitlabHost, projectPath, null);
        } else if (radioNoAskRemote.isSelected()) {
            response = new Response(Cancel.IGNORE_REMOTE, gitlabHost, projectPath, null);
        } else if (radioNoAskProject.isSelected()) {
            response = new Response(Cancel.IGNORE_PROJECT, gitlabHost, projectPath, null);
        } else if (radioDoMonitor.isSelected()) {
            if (Strings.isNullOrEmpty(hostInput.getText()) || Strings.isNullOrEmpty(projectPathInput.getText())) {
                Messages.showWarningDialog("Please fill out the gitlab host and project path", "Incomplete Data");
                return;
            }
            response = new Response(null, gitlabHost, projectPath, accessTokenInput.getText());
        }
        dispose();
    }

    private void onCancel() {
        accessTokenInput.setText(null);
        response = new Response(Cancel.ASK_AGAIN, null, null, null);
        dispose();
    }

    public Response showDialog() {
        this.setLocationRelativeTo(null);
        pack();
        setTitle("Gitlab Pipeline Viewer - Unknown remote found");
        setVisible(true);
        setModalityType(ModalityType.DOCUMENT_MODAL);
        return response;
    }


    public enum Cancel {
        ASK_AGAIN,
        IGNORE_REMOTE,
        IGNORE_PROJECT
    }

    public static class Response {

        private final Cancel cancel;
        private final String gitlabHost;
        private final String projectPath;
        private final String accessToken;

        public Response(Cancel cancel, String gitlabHost, String projectPath, String accessToken) {
            this.cancel = cancel;
            this.gitlabHost = gitlabHost;
            this.projectPath = projectPath;
            this.accessToken = accessToken;
        }

        public Cancel getCancel() {
            return cancel;
        }

        public String getAccessToken() {
            return accessToken;
        }

        public String getGitlabHost() {
            return gitlabHost;
        }

        public String getProjectPath() {
            return projectPath;
        }
    }
}
