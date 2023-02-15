package com.igio90.intellij.openai;

import com.intellij.application.options.CodeStyle;
import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.util.ThrowableRunnable;

public class DocumentUtils {
    public static Project getProject() {
        return ProjectManager.getInstance().getOpenProjects()[0];
    }

    public static PsiFile getCurrentFile(Document document) {
        return PsiDocumentManager.getInstance(getProject()).getPsiFile(document);
    }

    public static String getLanguage(Document document) {
        PsiFile psiFile = getCurrentFile(document);
        if (psiFile != null) {
            FileType fileType = psiFile.getFileType();
            if (fileType instanceof LanguageFileType) {
                Language language = ((LanguageFileType) fileType).getLanguage();
                return language.getID();
            }
        }
        return null;
    }

    public static int getDefaultIndentSize(Document document) {
        Project project = getProject();
        if (project == null) {
            return 4;
        }
        PsiFile psiFile = getCurrentFile(document);
        CommonCodeStyleSettings commonCodeStyleSettings = CodeStyle.getLanguageSettings(psiFile);
        if (commonCodeStyleSettings.getIndentOptions() == null) {
            return 4;
        }
        return commonCodeStyleSettings.getIndentOptions().INDENT_SIZE;
    }

    public static int getCurrentIndentCount(Document document, int lineOffset) {
        String indent = getCurrentIndent(document, lineOffset);
        if (indent == null) {
            return 0;
        }
        return indent.length();
    }

    public static String getCurrentIndent(Document document, int lineOffset) {
        return CodeStyleManager.getInstance(getProject()).getLineIndent(document, lineOffset);
    }

    public static void replaceAllText(Document document, String text) {
        ApplicationManager.getApplication().invokeLater(() -> {
            try {
                WriteCommandAction.writeCommandAction(getProject()).run((ThrowableRunnable<Throwable>) () -> {
                    document.deleteString(0, document.getTextLength());
                    document.insertString(0, text);
                });
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        });
    }

    public static void replaceTextAtLine(Document document, int lineNum, String text) {
        ApplicationManager.getApplication().invokeLater(() -> {
            try {
                WriteCommandAction.writeCommandAction(getProject()).run((ThrowableRunnable<Throwable>) () -> {
                    int lineOffset = document.getLineStartOffset(lineNum);
                    int lineEndOffset = document.getLineEndOffset(lineNum);
                    document.deleteString(lineOffset, lineEndOffset);
                    String indent = getCurrentIndent(document, lineOffset);

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
}
