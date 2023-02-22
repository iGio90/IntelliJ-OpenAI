package com.igio90.intellij.openai.actions;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.ThrowableRunnable;

import java.util.Collection;
import java.util.List;

public class JumpToLine implements IAction {
    private final String action = "jump_to_line";

    @Override
    public String getName() {
        return "JumpToLine";
    }

    @Override
    public String getAction() {
        return action;
    }

    @Override
    public String getType() {
        return "integer";
    }

    @Override
    public Collection<String> getAutoCompletion() {
        return List.of("Jump");
    }

    @Override
    public void perform(Project project, Object... data) {
        var lineNum = Integer.parseInt(data[0].toString());
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
