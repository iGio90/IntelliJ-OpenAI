package com.igio90.intellij.openai.processors;

import com.igio90.intellij.openai.utils.DocumentUtils;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.ui.JBColor;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.ui.UIUtil;
import lombok.extern.slf4j.Slf4j;
import name.fraser.neil.plaintext.diff_match_patch;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

@Slf4j
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
        LinkedList<diff_match_patch.Diff> diffs = new diff_match_patch().diff_main(contentBefore, content);
        int lineNum = 0;
        for (diff_match_patch.Diff diff : diffs) {
            String[] lines = diff.text.split("\n");
            log.debug("diff - op:" + diff.operation.name() + " " + diff.text);
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

        if (getCurrentIndent() == 0) {
            DocumentUtils.replaceTextAtLine(
                    getDocument(),
                    getLineNum(),
                    content,
                    "code gen"
            );
        } else {
            DocumentUtils.replaceAllText(
                    getDocument(),
                    content,
                    "code gen"
            );
        }

        ArrayList<RangeHighlighter> highlighters = new ArrayList<>();
        ApplicationManager.getApplication().invokeLater(() -> {
            try {
                WriteCommandAction.writeCommandAction(DocumentUtils.getProject()).run((ThrowableRunnable<Throwable>) () -> {
                    if (!changedLines.isEmpty()) {
                        for (int i = 0; i < changedLines.size(); i++) {
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
                        Editor editor = FileEditorManager.getInstance(DocumentUtils.getProject()).getSelectedTextEditor();
                        editor.getCaretModel().addCaretListener(new CaretListener() {
                            @Override
                            public void caretPositionChanged(@NotNull CaretEvent event) {
                                if (getDocument().getText().length() != contentBefore.length()) {
                                    for (RangeHighlighter rangeHighlighter : highlighters) {
                                        DocumentUtils.clearHighlightRange(
                                                getDocument(),
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
}
