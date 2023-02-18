package com.igio90.intellij.openai.processors;

import com.igio90.intellij.openai.utils.DocumentUtils;
import com.intellij.openapi.editor.Document;
import org.json.JSONObject;

class LintProcessor extends BaseProcessor {
    LintProcessor(Document document, int lineNum, Processors.OnProcessFinished onProcessFinished) {
        super(document, lineNum, onProcessFinished);
    }

    @Override
    protected String getUrl() {
        return "https://api.openai.com/v1/edits";
    }

    @Override
    protected JSONObject getRequestObject() {
        JSONObject object = new JSONObject();
        String documentText = getDocumentTextWithoutTriggerLine();

        object.put("input", documentText);
        object.put(
                "instruction",
                "apply lint, correct empty lines between variables and functions, indents using 4 spaces and stylus fixes"
        );
        object.put("temperature", 0.1);
        object.put("model", "code-davinci-edit-001");
        object.put("n", 1);
        return object;
    }

    @Override
    protected void onResponse(String content) {
        DocumentUtils.replaceAllText(
                getDocument(),
                content
        );
    }
}
