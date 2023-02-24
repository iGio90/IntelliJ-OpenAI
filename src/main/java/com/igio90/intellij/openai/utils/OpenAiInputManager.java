package com.igio90.intellij.openai.utils;

import com.igio90.intellij.openai.actions.*;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
public class OpenAiInputManager {

    private static final String NEW_LINE = "\n";
    private static final String SEPARATOR = " - ";
    private static final String PREREQUISITE_TEXT = "given this set of actions, description and data type expected in the result:";
    private static final String USER_INPUT_TEXT = "given the user input:";
    private static final String CRITERIA_TEXT = "give me an output that match the following criteria";

    private static final Collection<String> CRITERIA =
            List.of("determine which actions the user want to perform from the \"user input\"",
                    "output must be a json array",
                    "do not prepend the json array with any other word, I must be able to parse the output as json",
                    "array should contains zero or more json objects with a two key value inside",
                    "objects must be ordered according to the user input",
                    "first key-value must have a key \"action\"",
                    "first key-value must have a value that match one of the actions I gave you in the list before",
                    "second key-value must have a key \"data\"",
                    "\"data\" value must be retrieved from the user input",
                    "\"data\" value must respect the data type given in the set of actions",
                    "\"data\" must not be empty and must be retrieved from the user input. Do not assume user want to perform more actions if not explicitly requested"
            );
    public static final Set<IAction> actions = registerActions();

    static Set<IAction> registerActions() {
        Set<IAction> a = new HashSet<>();
        a.add(new OpenFile());
        a.add(new JumpToLine());
        a.add(new CreateCode());
        a.add(new Undo());
        return a;
    }

    static Optional<IAction> getAction(String action) {
        return actions.stream().filter(e -> e.getAction().equalsIgnoreCase(action)).findFirst();
    }

    private static String getOpenAIApiKey() {
        return PropertiesComponent.getInstance().getValue("openai_api_key", "");
    }

    public static StringBuilder getCriteriaString() {
        StringBuilder sb = new StringBuilder(NEW_LINE);
        sb.append(CRITERIA_TEXT);
        sb.append(NEW_LINE);
        for (String criteria : CRITERIA) {
            sb.append(SEPARATOR)
                    .append(criteria)
                    .append(NEW_LINE);
        }
        sb.append(NEW_LINE);
        return sb;
    }

    public static StringBuilder getUserInputString(String input) {
        StringBuilder sb = new StringBuilder(USER_INPUT_TEXT);
        sb.append(NEW_LINE);
        sb.append(input);
        sb.append(NEW_LINE);
        return sb;
    }

    public static StringBuilder getActionSetString() {
        StringBuilder sb = new StringBuilder(PREREQUISITE_TEXT);
        sb.append(NEW_LINE);
        for (var entry : actions) {
            sb.append(entry.getAction())
                    .append(SEPARATOR)
                    .append(entry.getActionDescription())
                    .append(SEPARATOR)
                    .append(entry.getType())
                    .append(NEW_LINE);
        }
        sb.append(NEW_LINE);
        return sb;
    }

    public static JSONObject getAiTextCommand(String prompt) {
        JSONObject j = new JSONObject();
        j.put("prompt", prompt);
        j.put("stream", false);
        j.put("temperature", 0.2);
        j.put("max_tokens", 2048);
        j.put("model", "text-davinci-003");
        j.put("n", 1);
        return j;
    }

    public static JSONObject getAiCodeEditCommand(String documentContent, String query) {
        JSONObject j = new JSONObject();
        j.put("input", documentContent);
        j.put("instruction", query);
        j.put("temperature", 0.2);
        j.put("model", "code-davinci-edit-001");
        j.put("n", 1);
        return j;
    }

    public static Request openAiCompletitionRequest(JSONObject jsonQuery) {
        return new Request.Builder()
                .url("https://api.openai.com/v1/completions")
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer " + getOpenAIApiKey())
                .post(RequestBody.create(jsonQuery.toString(),
                        MediaType.parse("application/json")))
                .build();
    }

    public static Request openAiEditRequest(JSONObject jsonQuery) {
        return new Request.Builder()
                .url("https://api.openai.com/v1/edits")
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer " + getOpenAIApiKey())
                .post(RequestBody.create(jsonQuery.toString(),
                        MediaType.parse("application/json")))
                .build();
    }

    private static void parseAction(Project project, JSONArray actions) {
        try {
            WriteCommandAction.writeCommandAction(project).run(() -> {
                for (int i = 0; i < actions.length(); i++) {
                    try {
                        JSONObject actionObject = actions.getJSONObject(i);
                        String action = actionObject.getString("action");
                        Object data = actionObject.get("data");
                        var actionImp = getAction(action);
                        if (actionImp.isEmpty()) {
                            log.warn("There is no action named " + action);
                        } else {
                            actionImp.get().perform(project, data);
                        }
                    } catch (Exception e) {
                        log.error("Failed to parse actions: " + e);
                    }
                }
            });
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static void execResponse(Project project, String body) {
        var aiResponse = new JSONObject(body);
        JSONArray choices = aiResponse.getJSONArray("choices");
        String content = choices.getJSONObject(0).getString("text");
        log.warn("open ai actions result:\n\n" + content);
        JSONArray actions = new JSONArray(content);
        ApplicationManager.getApplication().invokeLater(() -> parseAction(project, actions));
    }

    public static Response response(Request request) throws IOException {
        return new OkHttpClient.Builder()
                .callTimeout(2, TimeUnit.MINUTES)
                .readTimeout(2, TimeUnit.MINUTES)
                .build().newCall(request).execute();
    }
}