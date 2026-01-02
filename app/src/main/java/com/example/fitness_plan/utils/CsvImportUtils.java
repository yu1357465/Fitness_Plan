package com.example.fitness_plan.utils;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.example.fitness_plan.data.HistoryEntity;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CsvImportUtils {

    private static final String TAG = "CsvImportUtils";
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

    /**
     * 从 CSV 文件解析出 HistoryEntity 列表
     * @param context 上下文
     * @param uri 用户选择的文件 Uri
     * @return 解析成功的列表，如果失败返回 null
     */
    public static List<HistoryEntity> importHistoryFromCSV(Context context, Uri uri) {
        List<HistoryEntity> importedList = new ArrayList<>();

        try {
            InputStream inputStream = context.getContentResolver().openInputStream(uri);
            if (inputStream == null) return null;

            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            String line;
            boolean isFirstLine = true;

            while ((line = reader.readLine()) != null) {
                // 1. 处理 BOM (Byte Order Mark) - 剔除文件开头的隐藏字符
                if (isFirstLine) {
                    isFirstLine = false;
                    if (line.startsWith("\ufeff")) {
                        line = line.substring(1);
                    }
                    // 跳过表头 (如果第一行包含 "Date" 字样)
                    if (line.toLowerCase().contains("date") || line.contains("日期")) {
                        continue;
                    }
                }

                // 2. 分割 CSV 行
                // 简单分割，暂不支持带逗号的动作名 (如 "Squat, Heavy") 的复杂解析，
                // 如果需要复杂解析需引入 OpenCSV 库，这里为了轻量级用 split。
                String[] tokens = line.split(",");

                // 理论上我们导出有 6 列，读取时至少要有 6 列
                if (tokens.length < 6) {
                    Log.w(TAG, "Skipping invalid line: " + line);
                    continue;
                }

                try {
                    // 3. 解析字段 (对应 ExportUtils 的写入顺序)
                    // Index: 0=Date, 1=Session, 2=Name, 3=Weight, 4=Sets, 5=Reps

                    String dateStr = tokens[0].trim();
                    long date = dateFormat.parse(dateStr).getTime(); // 解析失败会抛异常，跳过此行

                    String sessionType = unescapeCsv(tokens[1].trim());
                    String name = unescapeCsv(tokens[2].trim());

                    double weight = Double.parseDouble(tokens[3].trim());
                    int sets = Integer.parseInt(tokens[4].trim());
                    int reps = Integer.parseInt(tokens[5].trim());

                    // 4. 构建对象
                    // ID 传 0 或不传，让 Room 自动生成新的 ID
                    HistoryEntity entity = new HistoryEntity(name, weight, reps, sets, date, sessionType);
                    importedList.add(entity);

                } catch (Exception e) {
                    Log.e(TAG, "Parse error on line: " + line, e);
                    // 继续解析下一行，不要因为一行错误就导致整个文件失败
                }
            }

            reader.close();
            inputStream.close();

            return importedList;

        } catch (Exception e) {
            Log.e(TAG, "Import failed", e);
            return null;
        }
    }

    // 简单的反转义处理 (去除双引号)
    private static String unescapeCsv(String text) {
        if (text.startsWith("\"") && text.endsWith("\"")) {
            return text.substring(1, text.length() - 1).replace("\"\"", "\"");
        }
        return text;
    }
}