package com.igio90.intellij.openai.actions;

import com.intellij.openapi.project.Project;

import java.util.Collection;

public interface IAction {
    String getName();

    String getAction();

    String getType();

    Collection<String> getAutoCompletion();

    void perform(Project project, Object... data);
}
