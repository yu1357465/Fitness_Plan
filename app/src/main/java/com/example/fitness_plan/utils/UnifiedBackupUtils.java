package com.example.fitness_plan.utils;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.example.fitness_plan.data.HistoryEntity;
import com.example.fitness_plan.data.PlanEntity;
import com.example.fitness_plan.data.TemplateEntity;
import com.example.fitness_plan.data.WorkoutDao;
import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class UnifiedBackupUtils {

    private static final String CONFIG_MARKER = "#APP_CONFIG_JSON:";

    private static class ConfigData {
        List<PlanEntity> plans;
        List<TemplateEntity> templates;
    }

    // 导出功能
    public static boolean exportUnifiedData(Context context, Uri uri, WorkoutDao dao) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("动作名称,重量(kg),组数,次数,日期,所属计划\n");

            List<HistoryEntity> historyList = dao.getAllHistory();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

            for (HistoryEntity h : historyList) {
                // 处理名称中的逗号
                String safeName = h.name.contains(",") ? "\"" + h.name + "\"" : h.name;
                String dateStr = sdf.format(new Date(h.date));

                // 【关键修复】现在 HistoryEntity 里有 workoutTitle 了，这里就不会报错了
                String title = (h.workoutTitle == null) ? "" : h.workoutTitle;
                String safeTitle = title.contains(",") ? "\"" + title + "\"" : title;

                sb.append(String.format(Locale.getDefault(), "%s,%.2f,%d,%d,%s,%s\n",
                        safeName, h.weight, h.sets, h.reps, dateStr, safeTitle));
            }

            ConfigData config = new ConfigData();
            config.plans = dao.getAllPlans();
            config.templates = dao.getAllTemplates();

            Gson gson = new Gson();
            String jsonConfig = gson.toJson(config);

            sb.append("\n").append(CONFIG_MARKER).append(jsonConfig);

            try (OutputStream os = context.getContentResolver().openOutputStream(uri)) {
                if (os != null) {
                    os.write(sb.toString().getBytes(StandardCharsets.UTF_8));
                    return true;
                }
            }
        } catch (Exception e) {
            Log.e("UnifiedBackup", "Export failed", e);
        }
        return false;
    }

    // 导入功能
    public static boolean importUnifiedData(Context context, Uri uri, WorkoutDao dao) {
        try {
            InputStream is = context.getContentResolver().openInputStream(uri);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));

            String line;
            Gson gson = new Gson();
            int successCount = 0;
            boolean configRestored = false;
            Map<Integer, Integer> planIdMap = new HashMap<>();

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                // 1. 解析配置
                if (line.startsWith(CONFIG_MARKER)) {
                    try {
                        String jsonStr = line.substring(CONFIG_MARKER.length());
                        ConfigData config = gson.fromJson(jsonStr, ConfigData.class);

                        if (config.plans != null) {
                            dao.deactivateAllPlans();
                            for (PlanEntity oldPlan : config.plans) {
                                int oldId = oldPlan.planId;
                                PlanEntity newPlan = new PlanEntity(oldPlan.planName, oldPlan.isActive);
                                long newId = dao.insertPlan(newPlan);
                                planIdMap.put(oldId, (int) newId);
                            }
                        }
                        if (config.templates != null) {
                            for (TemplateEntity temp : config.templates) {
                                Integer newPlanId = planIdMap.get(temp.planId);
                                if (newPlanId != null) {
                                    TemplateEntity newTemp = new TemplateEntity(
                                            newPlanId, temp.dayName, temp.dayIndex,
                                            temp.exerciseName, temp.defaultWeight,
                                            temp.defaultSets, temp.defaultReps
                                    );
                                    dao.insertTemplate(newTemp);
                                }
                            }
                        }
                        configRestored = true;
                    } catch (Exception e) {
                        Log.e("Import", "Config error", e);
                    }
                    continue;
                }

                if (line.startsWith("动作名称") || line.startsWith("Name")) continue;

                // 2. 解析 CSV 历史
                try {
                    String[] parts = line.split(",");
                    if (parts.length >= 5) {
                        String name = parts[0].replace("\"", "");
                        double weight = Double.parseDouble(parts[1]);
                        int sets = Integer.parseInt(parts[2]);
                        int reps = Integer.parseInt(parts[3]);

                        long dateLong = System.currentTimeMillis();
                        try {
                            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
                            Date d = sdf.parse(parts[4]);
                            if (d != null) dateLong = d.getTime();
                        } catch (Exception ignored) {}

                        // 【关键】读取最后一列的 workoutTitle
                        String title = (parts.length > 5) ? parts[5].replace("\"", "") : "导入记录";

                        // 这里的构造函数必须对应 HistoryEntity 的新构造函数
                        HistoryEntity history = new HistoryEntity(name, weight, reps, sets, dateLong, title);
                        dao.insertHistory(history);
                        successCount++;
                    }
                } catch (Exception ignored) {}
            }
            return successCount > 0 || configRestored;
        } catch (Exception e) {
            return false;
        }
    }
}