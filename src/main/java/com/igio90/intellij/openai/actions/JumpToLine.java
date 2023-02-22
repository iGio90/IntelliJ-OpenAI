package com.igio90.intellij.openai.actions;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;

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
    }
}
