package com.example.fitness_plan.utils;

import com.example.fitness_plan.data.EntityNameCache;
import com.example.fitness_plan.data.ExerciseBaseEntity;
import com.example.fitness_plan.data.HistoryEntity;
import com.example.fitness_plan.data.WorkoutDao;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CsvExportUtils {

    /**
     * 【V2.1 封神版】扁平化导出引擎
     * 完美提取底层原生的 planName 和动态连表查询的 Category
     * * @param historyList 历史记录列表
     * @param dao 传入 DAO 用于连表查询最新的动作类型
     */
    public static String generateCsvContent(List<HistoryEntity> historyList, WorkoutDao dao) {
        StringBuilder sb = new StringBuilder();

        // ⭐ 极客防呆：加入 UTF-8 BOM，防止 Windows Excel 打开时中文乱码！
        sb.append('\ufeff');

        // 1. 表头 (完美扩充为 8 个核心维度)
        sb.append("日期,计划名称,训练日主题,动作名称,动作类型,重量(kg),组数,次数\n");

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

        // 2. 遍历扁平化拼装
        for (HistoryEntity item : historyList) {
            // --- A. 解析日期 ---
            sb.append(sdf.format(new Date(item.date))).append(",");

            // --- B. 计划名称 与 训练日程 ---
            // 直接读取 V2.1 数据库手术新增的真实字段，彻底告别脆弱的 split 字符串切割！
            String planName = (item.planName != null && !item.planName.isEmpty()) ? item.planName : "无法追溯";
            String dayName = (item.workoutName != null && !item.workoutName.isEmpty()) ? item.workoutName : "自由训练";

            sb.append(escapeCsv(planName)).append(",");
            sb.append(escapeCsv(dayName)).append(",");

            // --- C. 动作数据与目标肌群 ---
            String exerciseName = "未知动作";
            String category = "其他";

            // 机制 (Mechanism): 用 baseId 瞬间击穿动作库，拿取最新鲜的中文翻译类型
            if (dao != null) {
                ExerciseBaseEntity base = dao.getExerciseBaseById(item.baseId);
                if (base != null) {
                    exerciseName = base.name;
                    category = translateCategoryToChinese(base.category);
                }
            } else {
                // 降级保护
                exerciseName = EntityNameCache.getInstance().getExerciseName(item.baseId);
            }

            sb.append(escapeCsv(exerciseName)).append(",");
            sb.append(escapeCsv(category)).append(",");

            // --- D. 核心容量 ---
            sb.append(item.weight).append(",");
            sb.append(item.sets).append(",");
            sb.append(item.reps).append("\n");
        }

        return sb.toString();
    }

    /**
     * 辅助方法：处理 CSV 中的特殊字符
     */
    private static String escapeCsv(String val) {
        if (val == null) return "";
        if (val.contains(",") || val.contains("\"") || val.contains("\n")) {
            return "\"" + val.replace("\"", "\"\"") + "\"";
        }
        return val;
    }

    /**
     * 辅助方法：将底层英文类型翻译为优美的中文导出
     */
    private static String translateCategoryToChinese(String rawCategory) {
        if (rawCategory == null) return "其他";
        switch (rawCategory) {
            case "Chest": return "胸部";
            case "Back": return "背部";
            case "Legs&Glutes": return "腿臀";
            case "Shoulders": return "肩部";
            case "Arms": return "手臂";
            case "Core": return "核心";
            default: return "其他";
        }
    }
}