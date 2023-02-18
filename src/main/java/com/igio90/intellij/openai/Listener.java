package com.igio90.intellij.openai;

import com.igio90.intellij.openai.processors.Processors;
import com.igio90.intellij.openai.utils.DocumentUtils;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class Listener implements DocumentListener {
    public static final int PROMPT_TYPE_NONE = -1;
    public static final int PROMPT_TYPE_CODE = 0;
    public static final int PROMPT_TYPE_DOC = 1;
    public static final int PROMPT_TYPE_STYLE = 2;

    private final UndoManager mUndoManager;

    public Listener() {
        mUndoManager = UndoManager.getInstance(DocumentUtils.getProject());
    }

    @Override
    public void beforeDocumentChange(@NotNull DocumentEvent event) {
    }

    @Override
    public void documentChanged(@NotNull DocumentEvent event) {
        if (mUndoManager.isUndoOrRedoInProgress()) {
            return;
        }

        Document document = event.getDocument();
        int offset = event.getOffset();
        int length = event.getNewLength();
        TextRange insertedRange = new TextRange(offset, offset + length);
        int lineNum = document.getLineNumber(insertedRange.getStartOffset());
        int lineOffset = document.getLineStartOffset(lineNum);
        int lineEndOffset = document.getLineEndOffset(lineNum);
        String lineText = document.getText(
                new TextRange(lineOffset, lineEndOffset)
        ).trim();

        // Check if the inserted text is a comment
        Map<String, String> commentMarkers = Map.of(
                "//", "C,C++,Java,JavaScript,Rust,Go,Scala,Kotlin,Dart,Swift,TypeScript",
                "#", "Python,Ruby",
                "--", "SQL,Lua,Ada",
                "%", "LaTeX",
                "<!--", "HTML,XML",
                "-->", "HTML,XML",
                "*", "SCSS,Sass,CSS,Less"
        );

        String[] words = lineText.split("\\s+");
        if (words.length < 2) {
            return;
        }

        String commentMarker = words[0];
        if (!commentMarkers.containsKey(commentMarker)) {
            return;
        }

        String commentKeyword = words[1];
        String endStr = null;

        int promptType = PROMPT_TYPE_NONE;
        switch (commentKeyword) {
            case "code":
            case "add":
                promptType = PROMPT_TYPE_CODE;
                break;
            case "document":
                promptType = PROMPT_TYPE_DOC;
                break;
        }

        if (promptType == PROMPT_TYPE_NONE) {
            if (words.length < 3) {
                return;
            }

            if (commentKeyword.equals("generate") || commentKeyword.equals("create")) {
                switch (words[2]) {
                    case "code":
                        promptType = PROMPT_TYPE_CODE;
                        break;
                    case "doc":
                        promptType = PROMPT_TYPE_DOC;
                        break;
                }
            } else if (commentKeyword.equals("apply")) {
                switch (words[2]) {
                    case "lint":
                    case "style":
                        promptType = PROMPT_TYPE_STYLE;
                        break;
                }
            }
        } else {
            endStr = ".";
        }

        if (promptType == PROMPT_TYPE_NONE) {
            return;
        }

        if (commentMarker.equals("<!--")) {
            // in those comment cases, endStr has to be replaced with what ends the comment for the current code lang
            endStr = "-->";
        }

        if (endStr != null && !words[words.length - 1].endsWith(endStr)) {
            return;
        }

        String query = "";
        if (promptType == PROMPT_TYPE_CODE) {
            query = lineText.substring(lineText.indexOf(words[2]), lineText.length() - 1).trim();
            if (query.isBlank()) {
                return;
            }
        }

        String apiKey = PropertiesComponent.getInstance().getValue("openai_api_key", "");
        if (apiKey.isBlank()) {
            DocumentUtils.replaceTextAtLine(
                    document,
                    lineNum,
                    "// insert an openai api key in tools menu -> OpenAI Preferences"
            );
            return;
        }

        String language = DocumentUtils.getLanguage(document);
        if (language == null) {
            return;
        }

        process(document, lineNum, promptType, query);
    }

    private void process(Document document, int lineNum, int promptType, String query) {
        String label = "";
        switch (promptType) {
            case PROMPT_TYPE_CODE:
                label = "Generating code...";
                break;
            case PROMPT_TYPE_DOC:
                label = "Generating doc...";
                break;
            case PROMPT_TYPE_STYLE:
                label = "Applying stylus improvements...";
                break;
        }

        String finalLabel = label;
        new Task.Backgroundable(DocumentUtils.getProject(), finalLabel) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                Processors.OnProcessFinished onProcessFinished = () -> {
                    indicator.setIndeterminate(false);
                };

                indicator.setText(finalLabel);
                indicator.setFraction(0);
                indicator.setIndeterminate(true);

                DocumentUtils.replaceTextAtLine(document, lineNum, "// " + finalLabel);

                switch (promptType) {
                    case PROMPT_TYPE_CODE:
                        Processors.getInstance().processCode(
                            document, lineNum, query, onProcessFinished
                        );
                        break;
                    case PROMPT_TYPE_DOC:
                        Processors.getInstance().processDoc(
                            document, lineNum, onProcessFinished
                        );
                        break;
                    case PROMPT_TYPE_STYLE:
                        Processors.getInstance().processLint(
                            document, lineNum, onProcessFinished
                        );
                        break;
                }
            }
        }.queue();
    }
}