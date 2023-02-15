package com.igio90.intellij.openai.processors;

import com.igio90.intellij.openai.DocumentUtils;
import com.intellij.openapi.editor.Document;
import org.json.JSONObject;

class CodeProcessor extends BaseProcessor {
    CodeProcessor(Document document, int lineNum, int currentIndent, String query, String language) {
        super(document, lineNum, currentIndent, query, language);
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
            String documentText = getDocument().getText();
            String[] lines = documentText.split("\n");
            documentText = documentText.replace(lines[getLineNum()] + "\n", "").replaceAll("\n\n", "\n");

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
    }
}
