package com.igio90.intellij.openai;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class OpenAIPrefs extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        ApiKeyDialog dialog = new ApiKeyDialog();
        dialog.setVisible(true);
        PropertiesComponent.getInstance().setValue("openai_api_key", dialog.getApiKey());
    }

    public static int sumTwoNumbers(int num1, int num2) {
      return num1 + num2;
    }

    public static class ApiKeyDialog extends JDialog {
        private final JTextField mApiKeyField;

        private String mApiKey;

        public ApiKeyDialog() {
            setTitle("API Key");
            setSize(300, 150);
            setModal(true);

            // Create a panel to hold the UI components
            JPanel panel = new JPanel(new GridBagLayout());
            GridBagConstraints constraints = new GridBagConstraints();
            constraints.fill = GridBagConstraints.HORIZONTAL;

            // Add a label for the API key field
            JLabel apiKeyLabel = new JLabel("API Key:");
            constraints.gridx = 0;
            constraints.gridy = 0;
            constraints.gridwidth = 1;
            panel.add(apiKeyLabel, constraints);

            // Add a text field for the API key
            mApiKeyField = new JTextField();
            mApiKeyField.setText(
                    PropertiesComponent.getInstance().getValue("openai_api_key", "")
            );
            constraints.gridx = 1;
            constraints.gridy = 0;
            constraints.gridwidth = 2;
            panel.add(mApiKeyField, constraints);

            // Add an OK button
            JButton okButton = new JButton("OK");
            okButton.addActionListener(e -> {
                mApiKey = mApiKeyField.getText();
                dispose();
            });
            constraints.gridx = 1;
            constraints.gridy = 1;
            constraints.gridwidth = 1;
            panel.add(okButton, constraints);

            // Add a Cancel button
            JButton cancelButton = new JButton("Cancel");
            cancelButton.addActionListener(e -> dispose());
            constraints.gridx = 2;
            constraints.gridy = 1;
            constraints.gridwidth = 1;
            panel.add(cancelButton, constraints);

            // Add the panel to the dialog
            add(panel);

            setLocationRelativeTo(null);
        }

        public String getApiKey() {
            return mApiKey;
        }
    }
}
