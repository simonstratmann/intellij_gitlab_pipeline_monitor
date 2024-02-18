package de.sist.gitlab.pipelinemonitor.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import de.sist.gitlab.pipelinemonitor.config.TokenType;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.function.Consumer;

public class TokenDialog extends JDialog {
    private JPanel contentPane;
    private JLabel messageLabel;
    private JRadioButton radioButtonPersonalToken;
    private JRadioButton radioButtonProjectToken;
    private JTextField token;

    public TokenDialog(String message, String oldToken, TokenType tokenType) {
        setContentPane(contentPane);
        setModal(true);
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
    }

    public static class Wrapper extends DialogWrapper {

        private final TokenDialog tokenDialog;
        private final Consumer<Pair<String, TokenType>> responseConsumer;

        public Wrapper(Project project, String message, String oldToken, TokenType tokenType, Consumer<Pair<String, TokenType>> responseConsumer) {
            super(project, false, IdeModalityType.PROJECT);
            this.tokenDialog = new TokenDialog(message, oldToken, tokenType);
            this.responseConsumer = responseConsumer;
            setTitle("Gitlab Pipeline Viewer - Access Token");
            init();
        }

        @Override
        protected void doOKAction() {
            responseConsumer.accept(
                    Pair.of(tokenDialog.token.getText(), tokenDialog.radioButtonPersonalToken.isSelected() ? TokenType.PERSONAL : TokenType.PROJECT));
            super.doOKAction();
        }

        @Override
        protected @Nullable JComponent createCenterPanel() {
            return (JComponent) tokenDialog.getContentPane();
        }
    }


}
