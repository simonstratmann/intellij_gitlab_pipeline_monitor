package de.sist.gitlab.pipelinemonitor.ui;

import de.sist.gitlab.pipelinemonitor.config.TokenType;
import org.apache.commons.lang3.tuple.Pair;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Optional;

public class TokenDialog extends JDialog {
    private JPanel contentPane;
    private JButton buttonOK;
    private JButton buttonCancel;
    private JLabel messageLabel;
    private JRadioButton radioButtonPersonalToken;
    private JRadioButton radioButtonProjectToken;
    private JTextField token;

    private Pair<String, TokenType> response;

    public TokenDialog(String message, String oldToken, TokenType tokenType) {
        setContentPane(contentPane);
        setModal(true);
        getRootPane().setDefaultButton(buttonOK);
        messageLabel.setText(message);
        token.setText(oldToken);
        final ButtonGroup buttonGroup = new ButtonGroup();
        buttonGroup.add(radioButtonPersonalToken);
        buttonGroup.add(radioButtonProjectToken);
        if (tokenType == TokenType.PERSONAL) {
            radioButtonPersonalToken.setSelected(true);
        } else {
            radioButtonProjectToken.setSelected(true);
        }

        buttonOK.addActionListener(e -> onOK());

        buttonCancel.addActionListener(e -> onCancel());

        // call onCancel() when cross is clicked
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                onCancel();
            }
        });

        // call onCancel() on ESCAPE
        contentPane.registerKeyboardAction(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onCancel();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    }

    private void onOK() {
        response = Pair.of(token.getText(), radioButtonPersonalToken.isSelected() ? TokenType.PERSONAL : TokenType.PROJECT);
        dispose();
    }

    private void onCancel() {
        dispose();
    }

    public Optional<Pair<String, TokenType>> showDialog() {
        this.setLocationRelativeTo(null);
        pack();
        setTitle("Gitlab Pipeline Viewer - Enter access token");
        setVisible(true);
        setModalityType(ModalityType.DOCUMENT_MODAL);
        return Optional.ofNullable(response);
    }


}
