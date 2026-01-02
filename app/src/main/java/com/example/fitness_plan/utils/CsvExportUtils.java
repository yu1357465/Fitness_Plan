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

            // 1. 写表头
            sb.append("动作名称,重量(kg),组数,次数,日期,训练标题\n");

            // 2. 格式化日期
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

            // 3. 遍历写入数据
            for (HistoryEntity item : historyList) {
                // 动作名称 (处理逗号)
                sb.append(escapeCsv(item.name)).append(",");

                // 重量
                sb.append(item.weight).append(",");

                // 组数
                sb.append(item.sets).append(",");

                // 次数
                sb.append(item.reps).append(",");

                // 日期
                sb.append(sdf.format(new Date(item.date))).append(",");

                // 【修复】这里原来是 item.sessionType，现在改成 correct 的 item.workoutTitle
                // 如果标题为空，给个默认值
                String title = (item.workoutTitle == null) ? "自由训练" : item.workoutTitle;
                sb.append(escapeCsv(title)).append("\n");
            }

            // 4. 写入文件
            try (OutputStream os = context.getContentResolver().openOutputStream(uri)) {
                if (os != null) {
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
        if (val.contains(",")) {
            return "\"" + val + "\"";
        }
        return val;
    }
}