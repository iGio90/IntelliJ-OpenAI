package com.igio90.intellij.openai.processors;

import com.igio90.intellij.openai.utils.DocumentUtils;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import okhttp3.OkHttpClient;

import java.util.concurrent.TimeUnit;

public class Processors {
    private static Processors sInstance;

    private final OkHttpClient mClient;

    public static Processors getInstance() {
        if (sInstance == null) {
            sInstance = new Processors();
        }

        return sInstance;
    }

    private Processors() {
        mClient = new OkHttpClient.Builder()
                .callTimeout(2, TimeUnit.MINUTES)
                .readTimeout(2, TimeUnit.MINUTES)
                .build();
    }

    protected OkHttpClient getClient() {
        return mClient;
    }

    protected String getOpenAIApiKey() {
        return PropertiesComponent.getInstance().getValue("openai_api_key", "");
    }

    public void processCode(Document document, int lineNum, String query, OnProcessFinished onProcessFinished) {
        ApplicationManager.getApplication().runReadAction(() -> {
            String language = DocumentUtils.getLanguage(document);
            if (language == null) {
                DocumentUtils.replaceTextAtLine(
                        document,
                        lineNum,
                        "// failed to generate code... no language identified from file"
                );
                return;
            }

            int currentIndentCount = DocumentUtils.getCurrentIndentCount(
                    document,
                    document.getLineStartOffset(lineNum)
            );
            new CodeProcessor(
                    document, lineNum, currentIndentCount, query, language, onProcessFinished
            ).start();
        });
    }

    public void processDoc(Document document, int lineNum, OnProcessFinished onProcessFinished) {
        new DocProcessor(document, lineNum, onProcessFinished).start();
    }

    public void processLint(Document document, int lineNum, OnProcessFinished onProcessFinished) {
        new LintProcessor(document, lineNum, onProcessFinished).start();
    }

    public interface OnProcessFinished {
        void onProcessFinished();
    }
}
