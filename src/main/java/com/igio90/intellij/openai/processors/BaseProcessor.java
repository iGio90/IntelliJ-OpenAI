package com.igio90.intellij.openai.processors;

import com.igio90.intellij.openai.DocumentUtils;
import com.intellij.openapi.editor.Document;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;

abstract class BaseProcessor extends Thread {
    private final Document mDocument;
    private final int mLineNum;
    private final int mCurrentIndent;
    private final String mQuery;
    private final String mLanguage;
    private final Processors.OnProcessFinished mOnProcessFinished;

    BaseProcessor(Document document, Processors.OnProcessFinished onProcessFinished) {
        this(document, 0, onProcessFinished);
    }

    BaseProcessor(Document document, int lineNum, Processors.OnProcessFinished onProcessFinished) {
        this(document, lineNum, 0, null, null, onProcessFinished);
    }

    BaseProcessor(Document document, int lineNum, int currentIndent, String query, String language, Processors.OnProcessFinished onProcessFinished) {
        mDocument = document;
        mLineNum = lineNum;
        mCurrentIndent = currentIndent;
        mQuery = query;
        mLanguage = language;
        mOnProcessFinished = onProcessFinished;
    }

    String getQuery() {
        return mQuery;
    }

    String getLanguage() {
        return mLanguage;
    }

    Document getDocument() {
        return mDocument;
    }

    int getLineNum() {
        return mLineNum;
    }

    int getCurrentIndent() {
        return mCurrentIndent;
    }

    protected abstract String getUrl();

    protected abstract JSONObject getRequestObject();

    protected abstract void onResponse(String content);

    @Override
    public void run() {
        String apiKey = Processors.getInstance().getOpenAIApiKey();

        String requestUrl = getUrl();
        JSONObject object = getRequestObject();

        Request request = new Request.Builder()
                .url(requestUrl)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer " + apiKey)
                .post(RequestBody.create(object.toString(), MediaType.parse("application/json")))
                .build();
        try {
            Response response = Processors.getInstance().getClient().newCall(request).execute();
            if (!response.isSuccessful() || response.body() == null) {
                DocumentUtils.replaceTextAtLine(
                        getDocument(),
                        getLineNum(),
                        "// failed to generate code... http response code: " + response.code()
                );
                return;
            }
            object = new JSONObject(response.body().string());
            JSONArray choices = object.getJSONArray("choices");
            onResponse(
                    choices.getJSONObject(0).getString("text").replaceAll("^\\n+", "")
            );
        } catch (Throwable e) {
            e.printStackTrace();

            DocumentUtils.replaceTextAtLine(
                    getDocument(),
                    getLineNum(),
                    "// failed to generate code... error: " + e.getMessage()
            );
        }

        if (mOnProcessFinished != null) {
            mOnProcessFinished.onProcessFinished();
        }
    }
}
