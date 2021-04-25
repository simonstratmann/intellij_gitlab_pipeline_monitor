package de.sist.gitlab.ui;

import javax.swing.*;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class UnmappedRemoteDialog extends JDialog {
    private JPanel contentPane;
    private JButton buttonOK;
    private JTextField accessTokenInput;
    private JLabel label;
    private JRadioButton radioAskAgain;
    private JRadioButton radioNoAskRemote;
    private JRadioButton radioNoAskProject;
    private JRadioButton radioDoMonitor;

    private Response response;

    public UnmappedRemoteDialog(String remoteUrl) {
        setContentPane(contentPane);
        setModal(true);
        getRootPane().setDefaultButton(buttonOK);

        ButtonGroup buttonGroup = new ButtonGroup();
        buttonGroup.add(radioAskAgain);
        buttonGroup.add(radioNoAskRemote);
        buttonGroup.add(radioNoAskProject);
        buttonGroup.add(radioDoMonitor);
        radioAskAgain.setSelected(true);

        label.setText("<html>The remote <b>" + remoteUrl + "</b> is not tracked by the gitlab pipeline viewer.<br>" +
                "Do you want to monitor pipelines built for this branch?<br>" +
                "You may still add or modify this in the settings later." +
                "</html>");

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
    }

    private void onOK() {
        if (radioAskAgain.isSelected()) {
            response = new Response(Cancel.ASK_AGAIN, null);
        } else if (radioNoAskRemote.isSelected()) {
            response = new Response(Cancel.IGNORE_REMOTE, null);
        } else if (radioNoAskProject.isSelected()) {
            response = new Response(Cancel.IGNORE_PROJECT, null);
        } else if (radioDoMonitor.isSelected()) {
            response = new Response(null, accessTokenInput.getText());
        }
        dispose();
    }

    private void onCancel() {
        accessTokenInput.setText(null);
        response = new Response(Cancel.ASK_AGAIN, null);
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
        private final String accessToken;

        public Response(Cancel cancel, String accessToken) {
            this.cancel = cancel;
            this.accessToken = accessToken;
        }

        public Cancel getCancel() {
            return cancel;
        }

        public String getAccessToken() {
            return accessToken;
        }
    }
}
