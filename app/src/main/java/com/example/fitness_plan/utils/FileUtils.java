package com.example.fitness_plan.utils;

import android.os.Environment;
import java.io.File;

public class FileUtils {

    // App 的总文件夹名称
    private static final String ROOT_DIR_NAME = "FitnessPlan";

    // 两个子文件夹名称
    private static final String DIR_BACKUP = "备份资料";
    private static final String DIR_REPORT = "导出的报表";

    /**
     * 获取 App 的根目录
     * 路径: .../Documents/FitnessPlan
     */
    public static File getAppDir() {
        // 使用 Documents 目录，方便用户在文件管理器中找到
        File documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
        File appDir = new File(documentsDir, ROOT_DIR_NAME);
        if (!appDir.exists()) {
            appDir.mkdirs();
        }
        return appDir;
    }

    /**
     * 获取【备份资料】文件夹
     * 路径: .../FitnessPlan/备份资料
     */
    public static File getBackupDir() {
        File root = getAppDir();
        File backupDir = new File(root, DIR_BACKUP);
        if (!backupDir.exists()) {
            backupDir.mkdirs();
        }
        return backupDir;
    }

    /**
     * 获取【导出的报表】文件夹
     * 路径: .../FitnessPlan/导出的报表
     */
    public static File getReportDir() {
        File root = getAppDir();
        File reportDir = new File(root, DIR_REPORT);
        if (!reportDir.exists()) {
            reportDir.mkdirs();
        }
        return reportDir;
    }
}