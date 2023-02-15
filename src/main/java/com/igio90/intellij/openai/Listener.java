package com.igio90.intellij.openai;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.ThrowableRunnable;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class Listener implements DocumentListener {

    private static final OkHttpClient sClient = new OkHttpClient.Builder()
            .callTimeout(2, TimeUnit.MINUTES)
            .readTimeout(2, TimeUnit.MINUTES)
            .build();

    private Project getProject() {
        return ProjectManager.getInstance().getOpenProjects()[0];
    }

    private String getLanguage(Document document) {
        PsiFile psiFile = PsiDocumentManager.getInstance(getProject()).getPsiFile(document);
        if (psiFile != null) {
            FileType fileType = psiFile.getFileType();
            if (fileType instanceof LanguageFileType) {
                Language language = ((LanguageFileType) fileType).getLanguage();
                return language.getID();
            }
        }
        return null;
    }

    private void addText(Document document, int lineOffset, int lineEndOffset, int lineNum, String text) {
        ApplicationManager.getApplication().invokeLater(() -> {
            try {
                WriteCommandAction.writeCommandAction(getProject()).run((ThrowableRunnable<Throwable>) () -> {
                    document.deleteString(lineOffset, lineEndOffset);
                    String indent = CodeStyleManager.getInstance(getProject()).getLineIndent(document, lineOffset);

                    String[] lines = text.split("\n");
                    for (int i = 0; i < lines.length; i++) {
                        lines[i] = indent + lines[i];
                    }
                    String indentedText = String.join("\n", lines);

                    document.insertString(lineOffset, indentedText);
                    Editor editor = FileEditorManager.getInstance(getProject()).getSelectedTextEditor();
                    if (editor != null) {
                        int insertedLineCount = text.split("\\r?\\n").length - 1;
                        editor.getCaretModel().moveToOffset(
                                document.getLineEndOffset(lineNum + insertedLineCount)
                        );
                    }
                });
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        });
    }

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
        if (words.length < 3) {
            return;
        }

        if (!commentMarkers.containsKey(words[0]) ||
                !Objects.equals(words[1], "code") ||
                !words[words.length - 1].endsWith(".")) {
            return;
        }

        String query = lineText.substring(lineText.indexOf(words[2]), lineText.length() - 1).trim();
        if (query.isBlank()) {
            return;
        }

        String language = getLanguage(document);
        if (language == null) {
            return;
        }

        String apiKey = PropertiesComponent.getInstance().getValue("openai_api_key", "");
        if (apiKey.isBlank()) {
            addText(
                    document,
                    lineOffset,
                    lineEndOffset,
                    lineNum,
                    "// insert an openai api key in tools menu -> OpenAI Preferences"
            );
            return;
        }

        addText(document, lineOffset, lineEndOffset, lineNum, "// generating code...");

        new Thread(() -> {
            String prompt = "generate " + language + " code based on this prompt: " + query;
            JSONObject object = new JSONObject();
            object.put("prompt", prompt);
            object.put("stream", false);
            object.put("temperature", 0.2);
            object.put("max_tokens", 2048); // 2048 with cushman | 8000 with davinci
            object.put("model", "text-davinci-003"); // code-cushman-001 | code-davinci-002
            object.put("n", 1);

            Request request = new Request.Builder()
                    .url("https://api.openai.com/v1/completions")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .post(RequestBody.create(object.toString(), MediaType.parse("application/json")))
                    .build();
            try {
                Response response = sClient.newCall(request).execute();
                if (!response.isSuccessful() || response.body() == null) {
                    addText(
                            document,
                            document.getLineStartOffset(lineNum),
                            document.getLineEndOffset(lineNum),
                            lineNum,
                            "// failed to generate code... http response code: " + response.code()
                    );
                    return;
                }
                object = new JSONObject(response.body().string());
                JSONArray choices = object.getJSONArray("choices");
                String code = choices.getJSONObject(0).getString("text").replaceAll("^\\n+", "");
                addText(
                        document,
                        document.getLineStartOffset(lineNum),
                        document.getLineEndOffset(lineNum),
                        lineNum,
                        code
                );
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }
}