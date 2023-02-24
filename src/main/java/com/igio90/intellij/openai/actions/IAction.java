package com.igio90.intellij.openai.actions;

import com.intellij.openapi.project.Project;

import java.util.Collection;

public interface IAction {
    String getAction();

    String getActionDescription();

    String getType();

    Collection<String> getAutoCompletion();

    /**
     * return a boolean indicating if the perform was successful
     * otherwise we must stop the chain of actions:
     * <p>
     * i.e: open file + code gen.
     * if open file fails we cant add the code generated in the current opened file
     */
    boolean perform(Project project, Object... data) throws Throwable;
}
