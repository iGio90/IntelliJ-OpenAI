package com.igio90.intellij.openai.actions;

import com.intellij.ide.DataManager;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.components.JBList;
import com.intellij.util.ThrowableRunnable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class OpenFile implements IAction {

    private final String action = "open_file";

    @Override
    public String getName() {
        return "OpenFile";
    }

    @Override
    public String getAction() {
        return action;
    }

    @Override
    public String getType() {
        return "string";
    }

    @Override
    public Collection<String> getAutoCompletion() {
        return List.of("Open");
    }

    @Override
    public void perform(Project project, Object... data) {
        var fileName = data[0].toString();
        ApplicationManager.getApplication().invokeLater(() -> {
            try {
                WriteCommandAction.writeCommandAction(project).run((ThrowableRunnable<Throwable>) () -> {
                    PsiFile[] psiFiles = FilenameIndex
                            .getFilesByName(project, fileName, GlobalSearchScope.projectScope(project));

                    if (psiFiles.length == 0) {
                        // No matching files found
                        Notification notification = new Notification(
                                "OpenAI.ErrorNotifications",
                                "OpenAI",
                                "No files found with name " + fileName,
                                NotificationType.ERROR
                        );
                        Notifications.Bus.notify(notification);
                    } else if (psiFiles.length == 1) {
                        // Only one matching file found, open it in editor
                        PsiFile psiFile = psiFiles[0];
                        VirtualFile virtualFile = psiFile.getVirtualFile();
                        FileEditorManager.getInstance(project).openFile(virtualFile, true);
                    } else {
                        // Multiple matching files found, show list and open selected file in editor
                        List<String> fileNames = new ArrayList<>();
                        for (PsiFile psiFile : psiFiles) {
                            fileNames.add(psiFile.getVirtualFile().getName());
                        }
                        JBList<String> fileList = new JBList<>(fileNames);
                        fileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
                        PopupChooserBuilder builder = new PopupChooserBuilder(fileList);
                        builder.setTitle("Select a File to Open");
                        builder.setItemChoosenCallback(() -> {
                            int selectedIndex = fileList.getSelectedIndex();
                            if (selectedIndex >= 0) {
                                PsiFile psiFile = psiFiles[selectedIndex];
                                VirtualFile virtualFile = psiFile.getVirtualFile();
                                FileEditorManager.getInstance(project).openFile(virtualFile, true);
                            }
                        });
                        builder.createPopup().showInBestPositionFor(DataManager.getInstance().getDataContext());
                    }
                });
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        });
    }
}