package com.example.fitness_plan.utils;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.widget.Toast;

import com.example.fitness_plan.data.AppDatabase;
import com.example.fitness_plan.data.ExerciseEntity;
import com.example.fitness_plan.data.HistoryEntity;
import com.example.fitness_plan.data.PlanEntity;
import com.example.fitness_plan.data.TemplateEntity;
import com.example.fitness_plan.data.WorkoutDao;
import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UnifiedBackupUtils {

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface OnCompleteListener {
        void onComplete(boolean success, String message);
    }

    // 数据包结构 (保持 public 以防 Gson 权限问题)
    public static class BackupData {
        long version;
        long timestamp;
        List<ExerciseEntity> exercises;
        List<PlanEntity> plans;
        List<TemplateEntity> templates;
        List<HistoryEntity> history;
    }

    // ==========================================
    //  1. 备份：写入到用户指定的 Uri (不再负责创建文件，只负责写)
    // ==========================================
    public static void writeBackupToUri(Context context, Uri uri, OnCompleteListener listener) {
        executor.execute(() -> {
            boolean success = false;
            String msg = "";
            try {
                AppDatabase db = AppDatabase.getDatabase(context);
                WorkoutDao dao = db.workoutDao();

                // 1. 打包数据
                BackupData data = new BackupData();
                data.version = 1;
                data.timestamp = System.currentTimeMillis();
                data.exercises = dao.getAllExercises();
                data.plans = dao.getAllPlans();
                data.templates = dao.getAllTemplates();
                data.history = dao.getAllHistory();

                // 2. 转 JSON
                String json = new Gson().toJson(data);

                // 3. 写入 Uri (使用 ParcelFileDescriptor 更加稳健)
                try (ParcelFileDescriptor pfd = context.getContentResolver().openFileDescriptor(uri, "w");
                     FileOutputStream fos = new FileOutputStream(pfd.getFileDescriptor())) {
                    // 如果文件里有旧内容，先截断
                    fos.getChannel().truncate(0);
                    fos.write(json.getBytes());
                }

                success = true;
                msg = "备份成功";

            } catch (Exception e) {
                e.printStackTrace();
                msg = "备份失败: " + e.getMessage();
            }

            boolean finalSuccess = success;
            String finalMsg = msg;
            mainHandler.post(() -> {
                if (listener != null) listener.onComplete(finalSuccess, finalMsg);
            });
        });
    }

    // ==========================================
    //  2. 恢复：从用户指定的 Uri 读取
    // ==========================================
    public static void restoreFromUri(Context context, Uri uri, OnCompleteListener listener) {
        executor.execute(() -> {
            boolean success = false;
            String msg = "";
            try {
                String json = readTextFromUri(context, uri);
                BackupData data = new Gson().fromJson(json, BackupData.class);

                if (data != null) {
                    AppDatabase db = AppDatabase.getDatabase(context);
                    WorkoutDao dao = db.workoutDao();

                    // 清空旧数据 (整机恢复逻辑)
                    dao.clearAllExercises();
                    dao.deactivateAllPlans();

                    List<PlanEntity> oldPlans = dao.getAllPlans();
                    for(PlanEntity p : oldPlans) {
                        dao.deleteTemplatesByPlanId(p.planId);
                        dao.deletePlan(p);
                    }

                    // 写入新数据
                    if (data.plans != null) {
                        for (PlanEntity p : data.plans) dao.insertPlan(p);
                    }
                    if (data.templates != null) {
                        for (TemplateEntity t : data.templates) dao.insertTemplate(t);
                    }
                    if (data.history != null) {
                        for (HistoryEntity h : data.history) dao.insertHistory(h);
                    }
                    if (data.exercises != null) {
                        for (ExerciseEntity e : data.exercises) dao.insert(e);
                    }
                    success = true;
                    msg = "恢复成功";
                } else {
                    msg = "文件格式错误";
                }
            } catch (Exception e) {
                e.printStackTrace();
                msg = "恢复失败: " + e.getMessage();
            }

            boolean finalSuccess = success;
            String finalMsg = msg;
            mainHandler.post(() -> {
                if (listener != null) listener.onComplete(finalSuccess, finalMsg);
            });
        });
    }

    private static String readTextFromUri(Context context, Uri uri) throws IOException {
        StringBuilder stringBuilder = new StringBuilder();
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                stringBuilder.append(line);
            }
        }
        return stringBuilder.toString();
    }
}