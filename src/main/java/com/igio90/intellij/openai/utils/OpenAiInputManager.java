package com.igio90.intellij.openai.utils;

import com.igio90.intellij.openai.actions.CreateCode;
import com.igio90.intellij.openai.actions.IAction;
import com.igio90.intellij.openai.actions.JumpToLine;
import com.igio90.intellij.openai.actions.OpenFile;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.util.ThrowableRunnable;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
public class OpenAiInputManager {

    private static final String newLine = "\n";
    private static final String separator = " - ";
    private static final String prerequisiteText = "given this set of actions and its value type:";
    private static final String userInputText = "given the user input:";
    private static final String criteriaText = "give me an output that match the following criteria";

    private static final Collection<String> CRITERIA =
            List.of("output must be a json array",
                    "array must contains zero or more json objects with a two key value inside",
                    "objects must be ordered according to the user input",
                    "first key-value must have a key \"action\"",
                    "first key-value must have a value that match one of the actions I gave you in the list before",
                    "second key-value must have a key \"data\"",
                    "second key-value value must be retrieved from the user input",
                    "second key-value value must have a data type matching what I gave you in the list before",
                    "second key-value must be wrapped by \" if it is not a string",
                    // somehow needed, or it will start assuming that the user want to perform things
                    // in example, I told it to navigate to file xy.java, and it assumed I also wanted to jump to some line
                    "do not assume the user want to perform additional actions from the input, " +
                            "the result must include only specified actions");
    public static final Set<IAction> actions = registerActions();

    static Set<IAction> registerActions() {
        Set<IAction> a = new HashSet<>();
        a.add(new OpenFile());
        a.add(new JumpToLine());
        a.add(new CreateCode());
        return a;
    }

    static Optional<IAction> getAction(String action) {
        return actions.stream().filter(e -> e.getAction().equalsIgnoreCase(action)).findFirst();
    }

    private static String getOpenAIApiKey() {
        return PropertiesComponent.getInstance().getValue("openai_api_key", "");
    }

    public static StringBuilder getCriteriaString() {
        StringBuilder sb = new StringBuilder(criteriaText);
        sb.append(newLine);
        for (String criteria : CRITERIA) {
            sb.append(separator)
                    .append(criteria)
                    .append(newLine);
        }
        sb.append(newLine);
        return sb;
    }

    public static StringBuilder getUserInputString(String input) {
        StringBuilder sb = new StringBuilder(userInputText);
        sb.append(newLine);
        sb.append(input);
        sb.append(newLine);
        return sb;
    }

    public static StringBuilder getActionSetString() {
        StringBuilder sb = new StringBuilder(prerequisiteText);
        sb.append(newLine);
        for (var entry : actions) {
            sb.append(entry.getAction())
                    .append(separator)
                    .append(entry.getType())
                    .append(newLine);
        }
        sb.append(newLine);
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

    public static JSONObject getAiQueryCommand(String documentContent, String query) {
        JSONObject j = new JSONObject();
        j.put("input", documentContent);
        j.put("instruction", query);
        j.put("temperature", 0.2);
        j.put("model", "code-davinci-edit-001");
        j.put("n", 1);
        return j;
    }

    public static Request openAiGeneralRequest(JSONObject jsonQuery) {
        return new Request.Builder()
                .url("https://api.openai.com/v1/completions")
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer " + getOpenAIApiKey())
                .post(RequestBody.create(jsonQuery.toString(),
                        MediaType.parse("application/json")))
                .build();
    }

    private static void parseAction(Project project, JSONArray actions) {
        try {
            WriteCommandAction.writeCommandAction(project).run((ThrowableRunnable<Throwable>) () -> {
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
        log.debug("open ai actions result:\n\n" + content);
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