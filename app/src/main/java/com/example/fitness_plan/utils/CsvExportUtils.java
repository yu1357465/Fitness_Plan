package com.example.fitness_plan.utils;

import com.example.fitness_plan.data.EntityNameCache;
import com.example.fitness_plan.data.HistoryEntity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CsvExportUtils {

    /**
     * 【新增】只负责生成 CSV 字符串内容，不负责保存文件
     * 供 SettingsActivity 调用，生成内容后传给 MediaStoreUtils 进行静默保存
     */
    public static String generateCsvContent(List<HistoryEntity> historyList) {
        StringBuilder sb = new StringBuilder();

        // 1. 表头
        sb.append("日期,计划名称,训练日程,动作名称,重量(kg),组数,次数\n");

        // 2. 日期格式化
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

        // 3. 遍历数据
        for (HistoryEntity item : historyList) {
            // --- A. 解析日期 ---
            sb.append(sdf.format(new Date(item.date))).append(",");

            // --- B. 拆分 计划名称 和 训练日程 ---
            String fullTitle = (item.workoutName == null) ? "自由训练" : item.workoutName;
            String planName = "";
            String dayName = fullTitle;

            // 如果标题包含 " - "，则切分 (例如: "5x5强力 - A日")
            if (fullTitle.contains(" - ")) {
                String[] parts = fullTitle.split(" - ", 2);
                planName = parts[0];
                dayName = parts.length > 1 ? parts[1] : "";
            }

            sb.append(escapeCsv(planName)).append(",");
            sb.append(escapeCsv(dayName)).append(",");

            // --- C. 动作数据 ---
            EntityNameCache nameCache = EntityNameCache.getInstance();
            String exerciseName = nameCache.getExerciseName(item.baseId);
            sb.append(escapeCsv(exerciseName)).append(",");
            sb.append(item.weight).append(",");
            sb.append(item.sets).append(",");
            sb.append(item.reps).append("\n");
        }

        return sb.toString();
    }

    /**
     * 辅助方法：处理 CSV 中的特殊字符
     * 如果内容包含逗号、双引号或换行，需要用双引号包起来，并将内部的双引号转义
     */
    private static String escapeCsv(String val) {
        if (val == null) return "";
        if (val.contains(",") || val.contains("\"") || val.contains("\n")) {
            return "\"" + val.replace("\"", "\"\"") + "\"";
        }
        return val;
    }
}