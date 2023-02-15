package com.igio90.intellij.openai.processors;

import com.igio90.intellij.openai.DocumentUtils;
import com.intellij.openapi.editor.Document;
import org.json.JSONObject;

class DocProcessor extends BaseProcessor {
    DocProcessor(Document document, int lineNum) {
        super(document, lineNum);
    }

    @Override
    protected String getUrl() {
        return "https://api.openai.com/v1/completions";
    }

    @Override
    protected JSONObject getRequestObject() {
        JSONObject object = new JSONObject();
        String documentText = getDocument().getText();
        String[] lines = documentText.split("\n");
        documentText = documentText.replace(lines[getLineNum()] + "\n", "").replaceAll("\n\n", "\n");

        object.put(
                "prompt",
                "```" + documentText + "```\n\n" +
                        "generate documentation for the code at line: " + getLineNum() + " " +
                        "and output only the documentation as a code comment"
        );
        object.put("stream", false);
        object.put("temperature", 0.2);
        object.put("max_tokens", 2048);
        object.put("model", "text-davinci-003");
        object.put("n", 1);
        return object;
    }

    @Override
    protected void onResponse(String content) {
        while (content.startsWith("`")) {
            content = content.substring(1);
        }
        while (content.startsWith("\n")) {
            content = content.substring(1);
        }
        while (content.endsWith("`")) {
            content = content.substring(0, content.length() - 1);
        }
        while (content.endsWith("\n")) {
            content = content.substring(0, content.length() - 1);
        }
        DocumentUtils.replaceTextAtLine(
                getDocument(),
                getLineNum(),
                content
        );
    }
}
