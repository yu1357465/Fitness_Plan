package com.example.fitness_plan.utils;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class MediaStoreUtils {

    // 备份文件的子目录名：Downloads/FitnessPlan_Backup
    public static final String BACKUP_SUB_DIR = "FitnessPlan_Backup";

    /**
     * 保存备份 (JSON) -> Downloads/FitnessPlan_Backup/
     * @return 保存成功后的 Uri，失败返回 null
     */
    public static Uri saveBackupToDownloads(Context context, String fileName, String jsonContent) {
        String relativePath = Environment.DIRECTORY_DOWNLOADS + File.separator + BACKUP_SUB_DIR;
        return saveToDownloads(context, fileName, jsonContent.getBytes(StandardCharsets.UTF_8), "application/json", relativePath);
    }

    /**
     * 保存报表 (CSV) -> Downloads 根目录
     * @return 保存成功后的 Uri，失败返回 null
     */
    public static Uri saveReportToDownloads(Context context, String fileName, String csvContent) {
        String relativePath = Environment.DIRECTORY_DOWNLOADS;
        return saveToDownloads(context, fileName, csvContent.getBytes(StandardCharsets.UTF_8), "text/csv", relativePath);
    }

    /**
     * 核心实现：写入 MediaStore.Downloads
     */
    private static Uri saveToDownloads(Context context, String fileName, byte[] content, String mimeType, String relativePath) {
        OutputStream fos = null;
        Uri fileUri = null;

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath);

                Uri collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
                fileUri = context.getContentResolver().insert(collection, values);

                if (fileUri != null) {
                    fos = context.getContentResolver().openOutputStream(fileUri);
                }
            } else {
                // Android 9 及以下
                File root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                File dir = root;

                // 处理子目录
                if (relativePath.contains(BACKUP_SUB_DIR)) {
                    dir = new File(root, BACKUP_SUB_DIR);
                    if (!dir.exists()) dir.mkdirs();
                }

                File destFile = new File(dir, fileName);
                fos = new FileOutputStream(destFile);

                // 这种方式生成 file:// Uri，但为了兼容之后的 Action_VIEW，最好用 FileProvider (这里为了简化先略过，高版本走上面的逻辑)
                // 如果是低版本手机，直接返回 null 让外部只提示文字即可，或者配置 FileProvider
                // 这里简单处理：低版本返回 null，只弹 Toast，不弹 Snackbar
            }

            if (fos != null) {
                // CSV 写入 BOM
                if (mimeType.equals("text/csv")) {
                    fos.write(new byte[]{(byte)0xEF, (byte)0xBB, (byte)0xBF});
                }
                fos.write(content);
                fos.flush();
                fos.close();
                return fileUri; // 返回 Uri 以供查看
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}