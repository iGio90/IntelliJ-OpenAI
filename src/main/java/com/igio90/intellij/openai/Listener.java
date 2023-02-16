package com.igio90.intellij.openai;

import com.igio90.intellij.openai.processors.Processors;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.util.TextRange;

import java.util.Map;

public class Listener implements DocumentListener {
    public static final int PROMPT_TYPE_NONE = -1;
    public static final int PROMPT_TYPE_CODE = 0;
    public static final int PROMPT_TYPE_DOC = 1;
    public static final int PROMPT_TYPE_STYLE = 2;

    @Override
    public void documentChanged(DocumentEvent event) {
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
        boolean skipDotCheck = false;
        int promptType = switch (commentKeyword) {
            case "code" -> PROMPT_TYPE_CODE;
            case "document", "document." -> PROMPT_TYPE_DOC;
            default -> PROMPT_TYPE_NONE;
        };

        if (promptType == PROMPT_TYPE_NONE) {
            if (words.length < 3) {
                return;
            }
            skipDotCheck = true;

            if (commentKeyword.equals("generate") || commentKeyword.equals("create")) {
                promptType = switch (words[2]) {
                    case "code" -> PROMPT_TYPE_CODE;
                    case "doc" -> PROMPT_TYPE_DOC;
                    default -> PROMPT_TYPE_NONE;
                };
            } else if (commentKeyword.equals("apply")) {
                promptType = switch (words[2]) {
                    case "lint", "style" -> PROMPT_TYPE_STYLE;
                    default -> PROMPT_TYPE_NONE;
                };
            }
        }

        if (promptType == PROMPT_TYPE_NONE) {
            return;
        }

        if (!skipDotCheck && !words[words.length - 1].endsWith(".")) {
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

        switch (promptType) {
            case PROMPT_TYPE_CODE -> {
                DocumentUtils.replaceTextAtLine(document, lineNum, "// generating code...");
                Processors.getInstance().processCode(
                        document, lineNum, query
                );
            }
            case PROMPT_TYPE_DOC -> {
                DocumentUtils.replaceTextAtLine(document, lineNum, "// generating doc...");
                Processors.getInstance().processDoc(
                        document, lineNum
                );
            }
            case PROMPT_TYPE_STYLE -> {
                DocumentUtils.replaceTextAtLine(document, lineNum, "// applying lint and stylus fixes...");
                Processors.getInstance().processLint(
                        document, lineNum
                );
            }
        }
    }
}