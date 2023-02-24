package com.igio90.intellij.openai.actions;

import com.intellij.ide.DataManager;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.components.JBList;
import info.debatty.java.stringsimilarity.JaroWinkler;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

public class OpenFile implements IAction {
    @Override
    public String getAction() {
        return "open_file";
    }

    @Override
    public String getActionDescription() {
        return "user want to open a file";
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
    public boolean perform(Project project, Object... data) {
        var fileName = data[0].toString();
        PsiFile[] psiFiles = FilenameIndex
                .getFilesByName(project, fileName, GlobalSearchScope.projectScope(project));

        if (psiFiles.length == 0) {
            // No matching files found
            JaroWinkler jaroWinkler = new JaroWinkler();
            HashSet<PsiFile> psiFilesSet = new HashSet<>();
            for (String file : FilenameIndex.getAllFilenames(project)) {
                double score = jaroWinkler.similarity(file, fileName);
                if (score > jaroWinkler.getThreshold()) {
                    psiFilesSet.addAll(
                            List.of(
                                    FilenameIndex.getFilesByName(
                                            project, file, GlobalSearchScope.projectScope(project)
                                    )
                            )
                    );
                }
            }

            if (psiFilesSet.isEmpty()) {
                Notification notification = new Notification(
                        "OpenAI.ErrorNotifications",
                        "OpenAI",
                        "No files found with name " + fileName,
                        NotificationType.ERROR
                );
                Notifications.Bus.notify(notification);
                return false;
            } else if (psiFilesSet.size() == 1) {
                FileEditorManager.getInstance(project).openFile(
                        psiFilesSet.stream().iterator().next().getVirtualFile(), true
                );
            } else {
                showFileListPopup(project, psiFilesSet.toArray(new PsiFile[0]));
            }
        } else if (psiFiles.length == 1) {
            // Only one matching file found, open it in editor
            FileEditorManager.getInstance(project).openFile(psiFiles[0].getVirtualFile(), true);
        } else {
            // Multiple matching files found, show list and open selected file in editor
            showFileListPopup(project, psiFiles);
        }
        return true;
    }

    private void showFileListPopup(Project project, PsiFile[] psiFiles) {
        List<String> fileNames = new ArrayList<>();
        for (PsiFile psiFile : psiFiles) {
            String filePath = project.getBasePath() == null ? psiFile.getVirtualFile().getPath() :
                    psiFile.getVirtualFile().getPath().replace(project.getBasePath() + "/", "");
            fileNames.add(filePath + " - " + psiFile.getVirtualFile().getName());
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
}