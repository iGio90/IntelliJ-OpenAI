package com.igio90.intellij.openai.actions;

import com.igio90.intellij.openai.Modal;
import com.igio90.intellij.openai.processors.Processors;
import com.igio90.intellij.openai.utils.DocumentUtils;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.ui.JBColor;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.ui.UIUtil;
import name.fraser.neil.plaintext.diff_match_patch;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class CreateCode {
    public static void perform(Project project, String query) {
        ApplicationManager.getApplication().invokeLater(() -> {
            try {
                WriteCommandAction.writeCommandAction(project).run((ThrowableRunnable<Throwable>) () -> {
                    Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
                    if (editor == null) {
                        return;
                    }
                    Document document = editor.getDocument();
                    String documentContent = document.getText();

                    new Task.Backgroundable(project, "generating code...") {
                        @Override
                        public void run(@NotNull ProgressIndicator indicator) {
                            String newCode;

                            try {
                                JSONObject object = new JSONObject();
                                object.put("input", documentContent);
                                object.put("instruction", query);
                                object.put("temperature", 0.2);
                                object.put("model", "code-davinci-edit-001");
                                object.put("n", 1);

                                Request request = new Request.Builder()
                                        .url("https://api.openai.com/v1/edits")
                                        .addHeader("Content-Type", "application/json")
                                        .addHeader("Authorization", "Bearer " + com.igio90.intellij.openai.Modal.getOpenAIApiKey())
                                        .post(RequestBody.create(object.toString(), MediaType.parse("application/json")))
                                        .build();
                                Response response = com.igio90.intellij.openai.Modal.getClient().newCall(request).execute();
                                if (!response.isSuccessful() || response.body() == null) {
                                    return;
                                }
                                object = new JSONObject(response.body().string());
                                JSONArray choices = object.getJSONArray("choices");
                                newCode = choices.getJSONObject(0).getString("text").replaceAll("^\\n+", "");
                            } catch (Throwable e) {
                                e.printStackTrace();
                                return;
                            }

                            List<Integer> changedLines = new ArrayList<>();
                            LinkedList<diff_match_patch.Diff> diffs = new diff_match_patch().diff_main(documentContent, newCode);
                            int lineNum = 0;
                            for (diff_match_patch.Diff diff : diffs) {
                                String[] lines = diff.text.split("\n");
                                System.out.println("diff - op:" + diff.operation.name() + " " + diff.text);
                                for (int i = 0; i < lines.length; i++) {
                                    if (diff.operation != diff_match_patch.Operation.EQUAL) {
                                        changedLines.add(lineNum);
                                    }
                                    if (i < lines.length - 1) {
                                        lineNum++;
                                    }
                                }
                            }
                            if (changedLines.size() > 1) {
                                // unsure if this is correct, but I saw it always highlight an extra line
                                changedLines.remove(changedLines.size() - 1);
                            }

                            DocumentUtils.replaceAllText(
                                    document,
                                    newCode,
                                    "code gen"
                            );

                            ArrayList<RangeHighlighter> highlighters = new ArrayList<>();
                            ApplicationManager.getApplication().invokeLater(() -> {
                                try {
                                    WriteCommandAction.writeCommandAction(DocumentUtils.getProject()).run((ThrowableRunnable<Throwable>) () -> {
                                        if (!changedLines.isEmpty()) {
                                            for (int i=0;i<changedLines.size();i++) {
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
                                    throw new RuntimeException(e);
                                }
                            });
                        }
                    }.queue();
                });
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        });
    }
}
