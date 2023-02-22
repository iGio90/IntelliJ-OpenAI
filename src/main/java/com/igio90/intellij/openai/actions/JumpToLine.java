package com.igio90.intellij.openai.actions;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.ThrowableRunnable;

public class JumpToLine {
    public static void perform(Project project, int lineNum) {
        ApplicationManager.getApplication().invokeLater(() -> {
            try {
                WriteCommandAction.writeCommandAction(project).run((ThrowableRunnable<Throwable>) () -> {
                    Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
                    if (editor == null) {
                        return;
                    }

                    Document document = editor.getDocument();
                    if (document.getLineCount() < lineNum) {
                        return;
                    }

                    int offset = document.getLineStartOffset(lineNum - 1);
                    editor.getCaretModel().moveToOffset(offset);
                    editor.getScrollingModel().scrollToCaret(ScrollType.CENTER);
                });
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        });
    }
}
