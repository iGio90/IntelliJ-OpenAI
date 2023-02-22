package com.igio90.intellij.openai;

import com.igio90.intellij.openai.actions.IAction;
import com.igio90.intellij.openai.utils.OpenAiInputManager;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.JBColor;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ui.UIUtil;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;
import okhttp3.Response;
import org.jdesktop.swingx.autocomplete.AutoCompleteDecorator;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
public class Modal extends AnAction {
    private JBPopup mPopup;
    private JTextField mInput;

    private JBPopup createPopup(JBPopupFactory factory, Project project) {
        return factory.createComponentPopupBuilder(createPopupPanel(project), null)
                .setRequestFocus(true)
                .setResizable(false)
                .setCancelOnClickOutside(true)
                .createPopup();
    }

    private int getScreenWidth() {
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        return screenSize.width / 3;
    }

    private Point getPopupPoint(Component parent, int width) {
        Rectangle bounds = parent.getBounds();
        int x = bounds.x + (bounds.width - width) / 2;
        int y = bounds.y + bounds.height / 4;
        return new Point(x, y);
    }

    @Override
    public void actionPerformed(AnActionEvent event) {
        Project project = event.getProject();
        Component parent = Objects.requireNonNull(WindowManager.getInstance().getIdeFrame(project)).getComponent();
        if (!ensureApiKey(project)) {
            return;
        }
        mPopup = createPopup(JBPopupFactory.getInstance(), project);
        mPopup.setSize(new Dimension(getScreenWidth(), mPopup.getContent().getPreferredSize().height));
        mPopup.show(
                new RelativePoint(parent, getPopupPoint(parent, getScreenWidth())));
        mInput.requestFocusInWindow();
    }

    private void addAutoCompletion(JTextField textField) {
        AutoCompleteDecorator.decorate(textField, OpenAiInputManager.actions.stream()
                .map(IAction::getAutoCompletion).collect(Collectors.toList()), false);
    }

    private JTextField createJTextField() {
        var jTextField = new JTextField();
        jTextField.setBorder(BorderFactory.createEmptyBorder());
        jTextField.setFont(UIUtil.getLabelFont().deriveFont(16f));
        jTextField.setPreferredSize(new Dimension(-1, 40));
        addAutoCompletion(jTextField);
        return jTextField;
    }

    private JComponent createPopupPanel(Project project) {
        JPanel panel = new JPanel(new BorderLayout());
        mInput = createJTextField();
        mInput.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    final String input = mInput.getText().trim();
                    if (input.isBlank())
                        return;
                    mInput.setForeground(JBColor.GRAY);
                    mInput.setEditable(false);
                    new Task.Backgroundable(project, "Processing") {
                        @Override
                        public void run(@NotNull ProgressIndicator indicator) {
                            indicator.setText("Performing actions...");
                            indicator.setFraction(0);
                            indicator.setIndeterminate(true);
                            var aiInput = OpenAiInputManager
                                    .getActionSetString()
                                    .append(OpenAiInputManager.getUserInputString(input))
                                    .append(OpenAiInputManager.getCriteriaString()).toString();
                            log.info("prompt:\n\n" + aiInput);
                            Request request = OpenAiInputManager.openAiGeneralRequest(
                                    OpenAiInputManager.getAiTextCommand(aiInput));
                            try (Response response = OpenAiInputManager.response(request)) {
                                if (!response.isSuccessful() || response.body() == null) {
                                    errorNotification(project,
                                            "Error performing request\n" +
                                                    "http response code: " +
                                                    response.code());
                                    return;
                                }
                                OpenAiInputManager.execResponse(project, response.body().string());
                            } catch (Throwable e) {
                                e.printStackTrace();
                                String[] errorMessage = e.getMessage().split("\n");
                                errorNotification(project, "Exception performing response\n: "
                                        + String.join("\n", errorMessage));
                            }
                            indicator.setIndeterminate(false);
                        }
                    }.queue();

                    /*new Task.Backgroundable(project, "Processing") {
                        @Override
                        public void run(@NotNull ProgressIndicator indicator) {
                            indicator.setText("Performing actions...");
                            indicator.setFraction(0);
                            indicator.setIndeterminate(true);

                            StringBuilder promptBuilder = new StringBuilder(
                                    "given those set of actions and it's value data type:"
                            );
                            promptBuilder.append("\n");
                            for (Map.Entry<String, String> entry : ACTIONS.entrySet()) {
                                promptBuilder.append(entry.getKey())
                                        .append(" - ")
                                        .append(entry.getValue())
                                        .append("\n");
                            }
                            promptBuilder.append("\n")
                                    .append("given the user input:")
                                    .append("\n")
                                    .append(input)
                                    .append("\n")
                                    .append("\n")
                                    .append("give me an output that match the following criteria:")
                                    .append("\n");
                            for (String criteria : CRITERIA) {
                                promptBuilder
                                        .append("- ")
                                        .append(criteria)
                                        .append("\n");
                            }

                            System.out.println("prompt:\n\n" + promptBuilder);
                            JSONObject object = new JSONObject();
                            object.put("prompt", promptBuilder.toString());
                            object.put("stream", false);
                            object.put("temperature", 0.2);
                            object.put("max_tokens", 2048);
                            object.put("model", "text-davinci-003");
                            object.put("n", 1);

                            Request request = new Request.Builder()
                                    .url("https://api.openai.com/v1/completions")
                                    .addHeader("Content-Type", "application/json")
                                    .addHeader("Authorization", "Bearer " + getOpenAIApiKey())
                                    .post(RequestBody.create(object.toString(), MediaType.parse("application/json")))
                                    .build();
                            try {
                                Response response = new OkHttpClient.Builder()
                                        .callTimeout(2, TimeUnit.MINUTES)
                                        .readTimeout(2, TimeUnit.MINUTES)
                                        .build().newCall(request).execute();

                                if (!response.isSuccessful() || response.body() == null) {
                                    errorNotification(project, "Error performing request\nhttp response code: " + response.code());
                                    return;
                                }

                                object = new JSONObject(response.body().string());
                                JSONArray choices = object.getJSONArray("choices");
                                String content = choices.getJSONObject(0).getString("text");
                                System.out.println("open ai actions result:\n\n" + content);
                                JSONArray actions = new JSONArray(content);

                                ApplicationManager.getApplication().invokeLater(() -> {
                                    try {
                                        WriteCommandAction.writeCommandAction(getProject()).run((ThrowableRunnable<Throwable>) () -> {
                                            for (int i = 0; i < actions.length(); i++) {
                                                try {
                                                    JSONObject actionObject = actions.getJSONObject(i);
                                                    String action = actionObject.getString("action");
                                                    Object data = actionObject.get("data");

                                                    switch (action) {
                                                        case "open_file":
                                                            OpenFile.perform(project, (String) data);
                                                            break;
                                                        case "jump_to_line":
                                                            JumpToLine.perform(project, (int) data);
                                                            break;
                                                    }
                                                } catch (Exception e) {
                                                    System.out.println("failed to parse actions: " + e.toString());
                                                    // todo notify user
                                                }
                                            }
                                        });
                                    } catch (Throwable e) {
                                        e.printStackTrace();

                                        String[] errorMessage = e.getMessage().split("\n");
                                        errorNotification(project, "Exception performing request\n: " + String.join("\n", errorMessage));
                                        break;
                                    }
                                });
                            } catch (Throwable e) {
                                e.printStackTrace();
                                String[] errorMessage = e.getMessage().split("\n");
                                errorNotification(project, "Exception performing request\n: " + String.join("\n", errorMessage));
                            }

                            indicator.setIndeterminate(false);
                        }
                    }.queue();*/
                    mPopup.closeOk(null);
                }
            }
        });
        panel.add(mInput, BorderLayout.CENTER);
        panel.setBackground(JBColor.namedColor("OpenAI.PanelBackground", new JBColor(0xdedede, 0x3c3f41)));
        /*
        can be useful later for an eventual history
        JList<String> list = new JBList<>();
        list.setBorder(BorderFactory.createEmptyBorder());
        list.setFont(UIUtil.getLabelFont().deriveFont(16f));
        JScrollPane scrollPane = new JBScrollPane(list);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        panel.add(scrollPane, BorderLayout.CENTER);
        */
        return panel;
    }

    private boolean ensureApiKey(Project project) {
        String apiKey = getOpenAIApiKey();
        if (apiKey.equals("")) {
            Notification notification = new Notification(
                    "OpenAI.ErrorNotifications",
                    "OpenAI",
                    "Please insert an api key",
                    NotificationType.ERROR
            );
            notification.addAction(new NotificationAction("Settings") {
                @Override
                public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
                    OpenAIPrefs preferences = new OpenAIPrefs();
                    preferences.actionPerformed(e);
                    notification.expire();
                }
            });
            Notifications.Bus.notify(notification, project);
            return false;
        }

        return true;
    }

    private void errorNotification(Project project, String content) {
        Notification notification = new Notification(
                "OpenAI.ErrorNotifications",
                "OpenAI error",
                content,
                NotificationType.ERROR
        );
        Notifications.Bus.notify(notification, project);
    }

    private String getOpenAIApiKey() {
        return PropertiesComponent.getInstance().getValue("openai_api_key", "");
    }
}