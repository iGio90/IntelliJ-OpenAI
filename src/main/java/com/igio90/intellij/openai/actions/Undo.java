package com.igio90.intellij.openai.actions;

import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;

import java.util.Collection;
import java.util.List;

public class Undo implements IAction {
    @Override
    public String getAction() {
        return "undo";
    }

    @Override
    public String getActionDescription() {
        return "user want to undo last action or get back to a previous state";
    }

    @Override
    public String getType() {
        return "boolean";
    }

    @Override
    public Collection<String> getAutoCompletion() {
        return List.of("undo", "back");
    }

    @Override
    public boolean perform(Project project, Object... data) throws Throwable {
        UndoManager.getInstance(project).undo(
                FileEditorManager.getInstance(project).getSelectedEditor()
        );
        return true;
    }
}
