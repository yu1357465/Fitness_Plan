package com.example.fitness_plan.utils;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.example.fitness_plan.data.HistoryEntity;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CsvExportUtils {

    public static boolean exportHistoryToCSV(Context context, Uri uri, List<HistoryEntity> historyList) {
        try {
            StringBuilder sb = new StringBuilder();

            // 1. 【修改】优化表头顺序，适合 Excel 透视表分析
            // 顺序：日期 -> 计划 -> 日程 -> 动作 -> 数据
            sb.append("日期,计划名称,训练日程,动作名称,重量(kg),组数,次数\n");

            // 2. 格式化日期
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

            // 3. 遍历写入数据
            for (HistoryEntity item : historyList) {

                // --- A. 解析日期 ---
                sb.append(sdf.format(new Date(item.date))).append(",");

                // --- B. 【核心逻辑】拆分 计划名称 和 训练日程 ---
                String fullTitle = (item.workoutName == null) ? "自由训练" : item.workoutName;
                String planName = "";
                String dayName = fullTitle;

                // 理论 vs 现实：如果标题包含 " - "，则切分；否则视为没有计划，全是日程名
                if (fullTitle.contains(" - ")) {
                    String[] parts = fullTitle.split(" - ", 2); // 限制只切成2份，防止标题里自己也有横杠
                    planName = parts[0];
                    dayName = parts.length > 1 ? parts[1] : "";
                }

                sb.append(escapeCsv(planName)).append(","); // 计划列
                sb.append(escapeCsv(dayName)).append(",");  // 日程列

                // --- C. 动作数据 ---
                sb.append(escapeCsv(item.exerciseName)).append(",");
                sb.append(item.weight).append(",");
                sb.append(item.sets).append(",");
                sb.append(item.reps).append("\n");
            }

            // 4. 写入文件
            try (OutputStream os = context.getContentResolver().openOutputStream(uri)) {
                if (os != null) {
                    // 【重要】写入 BOM 头 (0xEF, 0xBB, 0xBF)，强制 Excel 识别 UTF-8
                    // 如果不加这行，Windows Excel 打开中文会是乱码
                    os.write(new byte[]{(byte)0xEF, (byte)0xBB, (byte)0xBF});

                    os.write(sb.toString().getBytes(StandardCharsets.UTF_8));
                    return true;
                }
            }

        } catch (Exception e) {
            Log.e("CsvExport", "Export failed", e);
        }
        return false;
    }

    // 辅助方法：处理 CSV 中的特殊字符 (如果内容包含逗号，用引号包起来)
    private static String escapeCsv(String val) {
        if (val == null) return "";
        // Excel 规则：如果有逗号，必须用双引号包起来；如果有双引号，要转义成两个双引号
        if (val.contains(",") || val.contains("\"") || val.contains("\n")) {
            return "\"" + val.replace("\"", "\"\"") + "\"";
        }
        return val;
    }
}