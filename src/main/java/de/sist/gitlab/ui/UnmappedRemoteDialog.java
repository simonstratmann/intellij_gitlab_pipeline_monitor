package de.sist.gitlab.ui;

import com.google.common.base.Strings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.ui.ComponentValidator;
import com.intellij.openapi.ui.ValidationInfo;
import de.sist.gitlab.validator.UrlValidator;

import javax.swing.*;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class UnmappedRemoteDialog extends JDialog {
    private JPanel contentPane;
    private JButton buttonOK;
    private JButton buttonCancel;
    private JTextField mappingInput;
    private JLabel label;
    private JRadioButton radioAskAgain;
    private JRadioButton radioNoAskRemote;
    private JRadioButton radioNoAskProject;
    private JLabel projectIdLabel;

    private Response response;

    public UnmappedRemoteDialog(String remoteUrl, Disposable disposable) {
        setContentPane(contentPane);
        setModal(true);
        getRootPane().setDefaultButton(buttonOK);

        ButtonGroup buttonGroup = new ButtonGroup();
        buttonGroup.add(radioAskAgain);
        buttonGroup.add(radioNoAskRemote);
        buttonGroup.add(radioNoAskProject);
        radioAskAgain.setSelected(true);
        projectIdLabel.setText("Gitlab project ID for " + remoteUrl);

        label.setText("<html>The remote '" + remoteUrl + "' is not mapped to a gitlab project which means the pipeline viewer is unable<br> to retrieve pipelines for its branches.<br><br>" +
                "Please enter the ID of the gitlab project which builds pipelines for these branches.<br>" +
                "You may still add or modify this mapping in the settings later." +
                "</html>");

        buttonOK.addActionListener(e -> onOK());
        new ComponentValidator(disposable).withValidator(() -> {
            boolean valid = Strings.isNullOrEmpty(mappingInput.getText()) || UrlValidator.getInstance().isValid(mappingInput.getText());
            if (!valid) {
                return new ValidationInfo("Please enter a value", mappingInput);
            }
            return null;
        }).installOn(mappingInput);

        buttonCancel.addActionListener(e -> onCancel());

        // call onCancel() when cross is clicked
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                onCancel();
            }
        });

        // call onCancel() on ESCAPE
        contentPane.registerKeyboardAction(e -> onCancel(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    }

    private void onOK() {
        //Nothing to do, will return content of input
        if (Strings.isNullOrEmpty(mappingInput.getText())) {
            ComponentValidator.getInstance(mappingInput).ifPresent(ComponentValidator::revalidate);
            return;
        }
        response = new Response(null, mappingInput.getText());
        dispose();
    }

    private void onCancel() {
        mappingInput.setText(null);
        if (radioAskAgain.isSelected()) {
            response = new Response(Cancel.ASK_AGAIN, null);
        }
        if (radioNoAskRemote.isSelected()) {
            response = new Response(Cancel.IGNORE_REMOTE, null);
        } else if (radioNoAskProject.isSelected()) {
            response = new Response(Cancel.IGNORE_PROJECT, null);
        }
        dispose();
    }

    public Response showDialog() {
        this.setLocationRelativeTo(null);
        pack();
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
        private final String projectId;


        public Response(Cancel cancel, String projectId) {
            this.cancel = cancel;
            this.projectId = projectId;
        }

        public Cancel getCancel() {
            return cancel;
        }

        public String getProjectId() {
            return projectId;
        }
    }
}
