package com.igio90.intellij.openai;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.jcef.JBCefBrowser;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class ChatGPT implements ToolWindowFactory {
    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull com.intellij.openapi.wm.ToolWindow toolWindow) {
        JBCefBrowser browser = new JBCefBrowser("https://chat.openai.com/chat");
        JPanel contentPanel = new JPanel(new BorderLayout());
        contentPanel.add(browser.getComponent(), BorderLayout.CENTER);
        contentPanel.addMouseWheelListener(e -> {
            if (e.isControlDown()) {
                int notches = e.getWheelRotation();
                if (notches < 0) {
                    browser.setZoomLevel(browser.getZoomLevel() + 0.1D);
                } else {
                    browser.setZoomLevel(browser.getZoomLevel() - 0.1D);
                }
            }
        });

        ContentManager contentManager = toolWindow.getContentManager();
        Content content = contentManager.getFactory().createContent(contentPanel, null, false);
        contentManager.addContent(content);
    }
}
