package com.igio90.intellij.openai.actions;

import com.igio90.intellij.openai.Modal;
import com.igio90.intellij.openai.utils.DocumentUtils;
import com.igio90.intellij.openai.utils.OpenAiInputManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.ui.JBColor;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.ui.UIUtil;
import lombok.extern.slf4j.Slf4j;
import name.fraser.neil.plaintext.diff_match_patch;
import okhttp3.Request;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class CreateCode implements IAction {

    @Override
    public String getAction() {
        return "create_code";
    }

    @Override
    public String getActionDescription() {
        return "user want to add, modify or create code";
    }

    @Override
    public String getType() {
        return "string";
    }

    @Override
    public Collection<String> getAutoCompletion() {
        return List.of("Create");
    }

    @Override
    public boolean perform(Project project, Object... data) throws Throwable {
        var query = data[0].toString();
        Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
        if (editor == null) {
            return false;
        }
        Document document = editor.getDocument();
        String documentContent = document.getText();

        CompletableFuture<Boolean> future = new CompletableFuture<>();

        ProgressManager.getInstance().run(new Task.Backgroundable(project, "generating code...") {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                String newCode;

                try {
                    JSONObject object = OpenAiInputManager.getAiCodeEditCommand(documentContent, query);
                    Request request = OpenAiInputManager.openAiEditRequest(object);
                    try (Response response = OpenAiInputManager.response(request)) {
                        if (!response.isSuccessful() || response.body() == null) {
                            com.igio90.intellij.openai.Modal.errorNotification(project, "OpenAI request failed with code: " + response.code());
                            future.complete(false);
                            return;
                        }

                        object = new JSONObject(response.body().string());
                    }
                    JSONArray choices = object.getJSONArray("choices");
                    newCode = choices.getJSONObject(0).getString("text").replaceAll("^\\n+", "");
                } catch (Throwable e) {
                    e.printStackTrace();
                    future.completeExceptionally(e);
                    return;
                }

                LinkedList<diff_match_patch.Diff> diffs = new diff_match_patch().diff_main(documentContent, newCode);
                StringBuilder diffCode = new StringBuilder();
                ArrayList<Integer> changedLines = new ArrayList<>();
                int currentLine = 0;
                for (diff_match_patch.Diff diff : diffs) {
                    if (diff.operation.equals(diff_match_patch.Operation.DELETE)) {
                        continue;
                    }
                    int diffLines = diff.text.split("\n").length;
                    if (diff.operation.equals(diff_match_patch.Operation.EQUAL)) {
                        diffCode.append(diff.text);
                    } else if (diff.operation.equals(diff_match_patch.Operation.INSERT)) {
                        diffCode.append(diff.text);
                        for (int i = 0; i < diffLines; i++) {
                            changedLines.add(currentLine + i);
                        }
                    }
                    currentLine += diffLines;
                }

                DocumentUtils.replaceAllText(
                        document,
                        diffCode.toString(),
                        "code gen"
                );

                // complete the future or the invokeLater will stuck everything
                future.complete(true);

                ArrayList<RangeHighlighter> highlighters = new ArrayList<>();
                ApplicationManager.getApplication().invokeLater(() -> {
                    try {
                        WriteCommandAction.writeCommandAction(project).run((ThrowableRunnable<Throwable>) () -> {
                            if (!changedLines.isEmpty()) {
                                for (int i = 0; i < changedLines.size(); i++) {
                                    int line = changedLines.get(i);
                                    if (i == 0) {
                                        DocumentUtils.moveCaret(
                                                line, document.getLineStartOffset(line)
                                        );
                                    }
                                    highlighters.add(
                                            DocumentUtils.highlightRange(
                                                    document,
                                                    document.getLineStartOffset(line),
                                                    document.getLineEndOffset(line),
                                                    UIUtil.isUnderDarcula() ? DocumentUtils.DARK_GREEN : JBColor.GREEN
                                            )
                                    );
                                }
                            }

                            if (!highlighters.isEmpty()) {
                                editor.getCaretModel().addCaretListener(new CaretListener() {
                                    @Override
                                    public void caretPositionChanged(@NotNull CaretEvent event) {
                                        if (document.getText().length() != documentContent.length()) {
                                            for (RangeHighlighter rangeHighlighter : highlighters) {
                                                DocumentUtils.clearHighlightRange(
                                                        document,
                                                        rangeHighlighter
                                                );
                                            }
                                        }
                                        editor.getCaretModel().removeCaretListener(this);
                                    }
                                });
                            }
                        });
                    } catch (Throwable e) {
                        e.printStackTrace();
                    }
                });
            }
        });

        return future.get();
    }
}