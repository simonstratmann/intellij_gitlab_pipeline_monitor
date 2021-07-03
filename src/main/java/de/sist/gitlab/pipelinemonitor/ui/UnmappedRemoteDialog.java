package de.sist.gitlab.pipelinemonitor.ui;

import com.google.common.base.Strings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.ComponentValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.DocumentAdapter;
import de.sist.gitlab.pipelinemonitor.config.ConfigProvider;
import de.sist.gitlab.pipelinemonitor.config.Mapping;
import de.sist.gitlab.pipelinemonitor.gitlab.GitlabService;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Optional;

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
    private final Disposable disposable;

    public UnmappedRemoteDialog(String remoteUrl, Disposable disposable) {
        this.disposable = disposable;
        logger.debug(String.format("Showing dialog for remote %s and unknown host and project", remoteUrl));
        createCommonUiComponents(remoteUrl);
        label.setText("<html>The unknown remote <b>" + remoteUrl + "</b> was detected<br>but the gitlab host and project path could not be be determined.<br>" +
                "Do you want to monitor pipelines built for the associated project?<br>" +
                "Please enter the correct gitlab host and project path and an access token if needed for access." +
                "</html>");
    }

    public UnmappedRemoteDialog(String remoteUrl, String host, String projectPath, Disposable disposable) {
        this.disposable = disposable;
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

        buttonOK.addActionListener(e -> onOK(remoteUrl));

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
        //noinspection DuplicatedCode
        installHostValidator();
        installProjectPathValidator();
    }

    private void installHostValidator() {
        new ComponentValidator(disposable).withValidator(() -> {
            if (Strings.isNullOrEmpty(hostInput.getText())) {
                return new ValidationInfo("Please enter a host. It's the URL of the project's gitlab web UI", hostInput);
            }

            return null;
        }).installOn(hostInput);
        hostInput.getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull DocumentEvent e) {
                ComponentValidator.getInstance(hostInput).ifPresent(ComponentValidator::revalidate);
            }
        });
    }

    private void installProjectPathValidator() {
        new ComponentValidator(disposable).withValidator(() -> {
            if (Strings.isNullOrEmpty(projectPathInput.getText())) {
                return new ValidationInfo("Please enter a project path. It's the part of the project's web UI URL after the host", projectPathInput);
            }
            if (projectPathInput.getText().startsWith("/")) {
                return new ValidationInfo("The project path should not start with \"/\"", projectPathInput);
            }
            if (projectPathInput.getText().endsWith(".git")) {
                return new ValidationInfo("The project path should not end with \".git\"", projectPathInput);
            }
            return null;
        }).installOn(projectPathInput);
        projectPathInput.getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull DocumentEvent e) {
                ComponentValidator.getInstance(projectPathInput).ifPresent(ComponentValidator::revalidate);
            }
        });
    }

    private void onOK(String remoteUrl) {
        if (radioDoMonitor.isSelected()) {
            tryAndCreateMapping(remoteUrl);
        } else {
            if (radioAskAgain.isSelected()) {
                response = new Response(Cancel.ASK_AGAIN);
            } else if (radioNoAskRemote.isSelected()) {
                response = new Response(Cancel.IGNORE_REMOTE);
            } else if (radioNoAskProject.isSelected()) {
                response = new Response(Cancel.IGNORE_PROJECT);
            }
            dispose();
        }
    }

    private void tryAndCreateMapping(String remoteUrl) {
        if (Strings.isNullOrEmpty(hostInput.getText()) || Strings.isNullOrEmpty(projectPathInput.getText())) {
            Messages.showWarningDialog("Please fill out the gitlab host and project path", "Incomplete Data");
            return;
        }
        final Optional<Mapping> mappingOptional = GitlabService.createMappingWithProjectNameAndId(remoteUrl, hostInput.getText(), projectPathInput.getText(), accessTokenInput.getText());
        if (!mappingOptional.isPresent()) {
            Messages.showWarningDialog("The connection to gitlab failed or the project could not be found.", "Gitlab Connection Error");
            return;
        }

        response = new Response(null, mappingOptional.get(), accessTokenInput.getText());
        dispose();
    }

    private void onCancel() {
        accessTokenInput.setText(null);
        response = new Response(Cancel.ASK_AGAIN);
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
        private final Mapping mapping;
        private final String accessToken;

        public Response(Cancel cancel) {
            this.cancel = cancel;
            this.mapping = null;
            this.accessToken = null;
        }

        public Response(Cancel cancel, Mapping mapping, String accessToken) {
            this.cancel = cancel;
            this.mapping = mapping;
            this.accessToken = accessToken;
        }

        public Cancel getCancel() {
            return cancel;
        }

        public Mapping getMapping() {
            return mapping;
        }

        public String getAccessToken() {
            return accessToken;
        }


    }
}
