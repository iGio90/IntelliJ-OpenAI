package com.igio90.intellij.openai;

import com.igio90.intellij.openai.actions.JumpToLine;
import com.igio90.intellij.openai.actions.OpenFile;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.JBColor;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.ui.UIUtil;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class Modal extends AnAction {


    private static final Map<String, String> ACTIONS = Map.of(
            "open_file", "string",
            "jump_to_line", "integer",
            "delete_line", "integer",
            "delete_text", "string",
            "jump_to_function", "string",
            "create_code", "string",
            "create_documentation", "string",
            "read_line", "integer",
            "build_project", "boolean",
            "run_project", "boolean"
    );

    private static final ArrayList<String> CRITERIA = new ArrayList<>(
            Arrays.asList(
                    "output must be a json array",
                    "array must contains zero or more json objects with a two key value inside",
                    "objects must be ordered according to the user input",
                    "first key-value must have a key \"action\"",
                    "first key-value must have a value that match one of the actions I gave you in the list before",
                    "second key-value must have a key \"data\"",
                    "second key-value value must be retrieved from the user input",
                    "second key-value value must have a data type matching what I gave you in the list before",
                    // somehow needed, or it will start assuming that the user want to perform things
                    // in example, I told it to navigate to file xy.java, and it assumed I also wanted to jump to some line
                    "do not assume the user want to perform additional actions from the input, the result must include only specified actions"
            )
    );

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
        Component parent = WindowManager.getInstance().getIdeFrame(project).getComponent();

        if (!ensureApiKey(project)) {
            return;
        }

        mPopup = createPopup(JBPopupFactory.getInstance(), project);
        mPopup.setSize(new Dimension(getScreenWidth(), mPopup.getContent().getPreferredSize().height));
        mPopup.show(new RelativePoint(parent, getPopupPoint(parent, getScreenWidth())));
        mInput.requestFocusInWindow();
    }

    private void addAutoCompletion(JTextField textField) {
        //AutoCompleteDecorator.decorate(textField, items.toArray(), false);
    }

    private JTextField createJTextField() {
        var jTextField = new JTextField();
        jTextField.setBorder(BorderFactory.createEmptyBorder());
        jTextField.setFont(UIUtil.getLabelFont().deriveFont(16f));
        jTextField.setPreferredSize(new Dimension(-1, 40));
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

                    if (input.isBlank()) {
                        return;
                    }

                    mInput.setForeground(JBColor.GRAY);
                    mInput.setEditable(false);

                    new Task.Backgroundable(project, "Processing") {
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
                                        throw new RuntimeException(e);
                                    }
                                });
                            } catch (Throwable e) {
                                e.printStackTrace();
                                String[] errorMessage = e.getMessage().split("\n");
                                errorNotification(project, "Exception performing request\n: " + String.join("\n", errorMessage));
                            }

                            indicator.setIndeterminate(false);
                        }
                    }.queue();

                    mPopup.closeOk(null);
                }
            }
        });

        panel.add(mInput, BorderLayout.CENTER);

        panel.setBackground(JBColor.namedColor("OpenAI.PanelBackground", new JBColor(0xdedede, 0x3c3f41)));

        /*
        can be useful later for an eventual history

        // Create the list of suggestions
        JList<String> list = new JBList<>();
        list.setBorder(BorderFactory.createEmptyBorder());
        list.setFont(UIUtil.getLabelFont().deriveFont(16f));
        JScrollPane scrollPane = new JBScrollPane(list);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());

        // Add the list to the panel
        panel.add(scrollPane, BorderLayout.CENTER);
         */

        // Return the panel
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