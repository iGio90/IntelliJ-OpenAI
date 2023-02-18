package com.igio90.intellij.openai.processors;

import com.igio90.intellij.openai.utils.DocumentUtils;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.ui.JBColor;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

class CodeProcessor extends BaseProcessor {
    CodeProcessor(Document document, int lineNum, int currentIndent, String query, String language, Processors.OnProcessFinished onProcessFinished) {
        super(document, lineNum, currentIndent, query, language, onProcessFinished);
    }

    @Override
    protected String getUrl() {
        if (getCurrentIndent() == 0) {
            return "https://api.openai.com/v1/completions";
        } else {
            return "https://api.openai.com/v1/edits";
        }
    }

    @Override
    protected JSONObject getRequestObject() {
        JSONObject object = new JSONObject();
        if (getCurrentIndent() == 0) {
            object.put("prompt", "generate " + getLanguage() + " code based on this prompt: " + getQuery());
            object.put("stream", false);
            object.put("temperature", 0.2);
            object.put("max_tokens", 2048);
            object.put("model", "text-davinci-003");
            object.put("n", 1);
        } else {
            String documentText = getDocumentTextWithoutTriggerLine();

            object.put("input", documentText);
            object.put("instruction", getQuery());
            object.put("temperature", 0.2);
            object.put("model", "code-davinci-edit-001");
            object.put("n", 1);
        }
        return object;
    }

    @Override
    protected void onResponse(String content) {
        String contentBefore = getDocumentTextWithoutTriggerLine();

        List<Integer> changedLines = new ArrayList<>();
        List<String> originalLines = new ArrayList<>(List.of(contentBefore.split("\n")));
        List<String> newLines = List.of(content.split("\n"));

        for (int i = 0; i < originalLines.size() && i < newLines.size(); i++) {
            if (!originalLines.get(i).equals(newLines.get(i))) {
                changedLines.add(i);
                originalLines.add(i, newLines.get(i));
            }
        }

        if (getCurrentIndent() == 0) {
            DocumentUtils.replaceTextAtLine(
                    getDocument(),
                    getLineNum(),
                    content
            );
        } else {
            DocumentUtils.replaceAllText(
                    getDocument(),
                    content
            );
        }

        ArrayList<RangeHighlighter> highlighters = new ArrayList<>();
        ApplicationManager.getApplication().invokeLater(() -> {
            try {
                WriteCommandAction.writeCommandAction(DocumentUtils.getProject()).run((ThrowableRunnable<Throwable>) () -> {
                    if (!changedLines.isEmpty()) {
                        for (int i=0;i<changedLines.size();i++) {
                            int line = changedLines.get(i);
                            if (i == 0) {
                                DocumentUtils.moveCaret(
                                        line, getDocument().getLineStartOffset(line)
                                );
                            }
                            highlighters.add(
                                    DocumentUtils.highlightRange(
                                            getDocument(),
                                            getDocument().getLineStartOffset(line),
                                            getDocument().getLineEndOffset(line),
                                            UIUtil.isUnderDarcula() ? DocumentUtils.DARK_GREEN : JBColor.GREEN
                                    )
                            );
                        }
                    }

                    if (!highlighters.isEmpty()) {
                        getDocument().addDocumentListener(new DocumentListener() {
                            @Override
                            public void documentChanged(@NotNull DocumentEvent event) {
                                if (getDocument().getText().length() != contentBefore.length()) {
                                    for (RangeHighlighter rangeHighlighter : highlighters) {
                                        DocumentUtils.clearHighlightRange(
                                                getDocument(),
                                                rangeHighlighter
                                        );
                                    }
                                }
                                getDocument().removeDocumentListener(this);
                            }
                        });
                    }
                });
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        });
    }
}
