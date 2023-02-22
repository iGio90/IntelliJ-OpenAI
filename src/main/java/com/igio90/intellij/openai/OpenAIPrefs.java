package com.igio90.intellij.openai;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class OpenAIPrefs extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        showDialog();
    }

    public static void showDialog() {
        SettingsDialog dialog = new SettingsDialog();
        dialog.setVisible(true);
        PropertiesComponent.getInstance().setValue("openai_api_key", dialog.getApiKey());
    }

    public static class SettingsDialog extends JDialog {
        private final JTextField mApiKeyField;

        private String mApiKey;

        public SettingsDialog() {
            setTitle("API Key");
            setSize(400, 150);
            setModal(true);

            // Create a panel to hold the UI components
            JPanel panel = new JPanel();

            GroupLayout layout = new GroupLayout(panel);
            panel.setLayout(layout);
            layout.setAutoCreateGaps(true);
            layout.setAutoCreateContainerGaps(true);

            // Add a label for the API key field
            JLabel apiKeyLabel = new JLabel("API Key:");
            mApiKeyField = new JTextField();
            mApiKeyField.setText(PropertiesComponent.getInstance().getValue("openai_api_key", ""));
            JButton okButton = new JButton("OK");
            okButton.addActionListener(e -> {
                mApiKey = mApiKeyField.getText();
                dispose();
            });
            JButton cancelButton = new JButton("Cancel");
            cancelButton.addActionListener(e -> dispose());

            layout.setHorizontalGroup(
                    layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                            .addGroup(layout.createSequentialGroup()
                                    .addComponent(apiKeyLabel)
                                    .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                                    .addComponent(mApiKeyField)
                            )
                            .addGroup(layout.createSequentialGroup()
                                    .addGap(0, 0, Short.MAX_VALUE)
                                    .addComponent(okButton)
                                    .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                                    .addComponent(cancelButton)
                            )
            );

            layout.setVerticalGroup(
                    layout.createSequentialGroup()
                            .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                                    .addComponent(apiKeyLabel)
                                    .addComponent(mApiKeyField)
                            )
                            .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                                    .addComponent(okButton)
                                    .addComponent(cancelButton)
                            )
            );

            // Add the panel to the dialog
            add(panel);

            setLocationRelativeTo(null);
        }

        public String getApiKey() {
            return mApiKey;
        }
    }
}
