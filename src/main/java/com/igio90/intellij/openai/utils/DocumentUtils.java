package com.igio90.intellij.openai.utils;

import com.intellij.application.options.CodeStyle;
import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.ui.JBColor;
import com.intellij.util.ThrowableRunnable;

import java.awt.*;

public class DocumentUtils {
    public static final JBColor DARK_GREEN = new JBColor("darkgreen", JBColor.GREEN.darker().darker());

    public static Project getProject() {
        return ProjectManager.getInstance().getOpenProjects()[0];
    }

    public static Document getCurrentDocument() {
        return getCurrentDocument(getProject());
    }

    public static Document getCurrentDocument(Project project) {
        FileEditorManager editorManager = FileEditorManager.getInstance(project);
        VirtualFile[] selectedFiles = editorManager.getSelectedFiles();
        if (selectedFiles.length > 0) {
            VirtualFile selectedFile = selectedFiles[0];
            return FileDocumentManager.getInstance().getDocument(selectedFile);
        }
        return null;
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

    public static RangeHighlighter highlightRange(Document document, int start, int end, JBColor color) {
        MarkupModel markupModel = DocumentMarkupModel.forDocument(document, DocumentUtils.getProject(), false);
        return markupModel.addRangeHighlighter(start, end, HighlighterLayer.SELECTION, new TextAttributes(null, color, null, null, Font.PLAIN), HighlighterTargetArea.EXACT_RANGE);
    }

    public static void clearHighlightRange(Document document, RangeHighlighter highlighter) {
        MarkupModel markupModel = DocumentMarkupModel.forDocument(document, DocumentUtils.getProject(), false);
        markupModel.removeHighlighter(highlighter);
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
        return indent.length();
    }

    public static String getCurrentIndent(Document document, int lineOffset) {
        String indent = CodeStyleManager.getInstance(getProject()).getLineIndent(document, lineOffset);
        return indent == null ? "" : indent;
    }

    public static void removeLine(Document document, int lineNum) {
        ApplicationManager.getApplication().invokeLater(() -> {
            try {
                WriteCommandAction.writeCommandAction(getProject()).run((ThrowableRunnable<Throwable>) () -> {
                    document.replaceString(
                            document.getLineStartOffset(lineNum),
                            document.getLineEndOffset(lineNum) + 1,
                            ""
                    );
                });
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        });
    }

    public static void replaceAllText(Document document, String text, String actionName) {
        ApplicationManager.getApplication().invokeLater(() -> {
            try {
                WriteCommandAction.writeCommandAction(getProject()).run((ThrowableRunnable<Throwable>) () -> {
                    CommandProcessor.getInstance().executeCommand(
                            DocumentUtils.getProject(),
                            () -> {
                                document.deleteString(0, document.getTextLength());
                                document.insertString(0, text);
                                CommandProcessor.getInstance().setCurrentCommandName(actionName);
                            },
                            actionName,
                            actionName,
                            document
                    );
                });
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        });
    }

    public static void replaceTextAtLine(Document document, int lineNum, String text, String actionName) {
        replaceTextAtLine(document, lineNum, text, actionName, true);
    }

    public static void replaceTextAtLine(
            Document document,
            int lineNum,
            String text,
            String actionName,
            boolean moveCaret
    ) {
        ApplicationManager.getApplication().invokeLater(() -> {
            try {
                WriteCommandAction.writeCommandAction(getProject()).run((ThrowableRunnable<Throwable>) () -> {
                    CommandProcessor.getInstance().executeCommand(
                            DocumentUtils.getProject(),
                            internalReplaceTextAtLine(document, lineNum, text, actionName, moveCaret),
                            actionName,
                            actionName,
                            document
                    );
                });
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static Runnable internalReplaceTextAtLine(
            Document document, int lineNum, String text, String actionName, boolean moveCaret
    ) {
        return () -> {
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

            if (moveCaret) {
                int insertedLineCount = text.split("\\r?\\n").length - 1;
                moveCaret(lineNum, document.getLineEndOffset(lineNum + insertedLineCount));
            }

            if (actionName != null) {
                CommandProcessor.getInstance().setCurrentCommandName(actionName);
            }
        };
    }

    public static void moveCaret(int line, int offset) {
        Editor editor = FileEditorManager.getInstance(getProject()).getSelectedTextEditor();
        if (editor != null) {
            editor.getCaretModel().moveToOffset(offset);
            LogicalPosition position = new LogicalPosition(line, 0);
            editor.getScrollingModel().scrollTo(position, ScrollType.CENTER);
        }
    }
}
