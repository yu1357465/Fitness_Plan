package com.example.fitness_plan;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.example.fitness_plan.data.AppDatabase;
import com.example.fitness_plan.data.ExerciseEntity;
import com.example.fitness_plan.data.WorkoutDao;
import com.example.fitness_plan.utils.CsvExportUtils; // 确保导入了这个
import com.example.fitness_plan.utils.UnifiedBackupUtils;
import com.google.android.material.snackbar.Snackbar; // 【新增】用于更好的提示

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SettingsActivity extends AppCompatActivity {

    private RadioGroup unitRadioGroup;
    private RadioButton rbKg, rbLbs;
    private RadioGroup addStrategyRadioGroup;
    private RadioButton rbAddToPlan, rbTemporary;

    private SharedPreferences prefs;
    private WorkoutDao workoutDao;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    private ActivityResultLauncher<String> backupLauncher;
    private ActivityResultLauncher<String[]> restoreLauncher;
    private ActivityResultLauncher<String> csvExportLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // ============ 【新增】状态栏美化 ============
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            getWindow().getDecorView().setSystemUiVisibility(
                    android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                            android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
            getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        }

        workoutDao = AppDatabase.getDatabase(this).workoutDao();
        prefs = getSharedPreferences("fitness_prefs", MODE_PRIVATE);

        // 初始化控件 (保持原样)
        unitRadioGroup = findViewById(R.id.unitRadioGroup);
        rbKg = findViewById(R.id.rbKg);
        rbLbs = findViewById(R.id.rbLbs);
        addStrategyRadioGroup = findViewById(R.id.addStrategyRadioGroup);
        rbAddToPlan = findViewById(R.id.rbAddToPlan);
        rbTemporary = findViewById(R.id.rbTemporary);

        // 回显设置 (保持原样)
        boolean defaultIsLbs = prefs.getBoolean("DEFAULT_IS_LBS", false);
        if (defaultIsLbs) rbLbs.setChecked(true); else rbKg.setChecked(true);
        boolean defaultAddToPlan = prefs.getBoolean("DEFAULT_ADD_TO_PLAN", true);
        if (defaultAddToPlan) rbAddToPlan.setChecked(true); else rbTemporary.setChecked(true);

        // 监听器 (保持原样)
        unitRadioGroup.setOnCheckedChangeListener((group, checkedId) -> saveUnitSetting(checkedId == R.id.rbLbs));
        addStrategyRadioGroup.setOnCheckedChangeListener((group, checkedId) ->
                prefs.edit().putBoolean("DEFAULT_ADD_TO_PLAN", checkedId == R.id.rbAddToPlan).apply());

        // ============================================================
        //  文件操作 Launchers (核心修改区域)
        // ============================================================

        // 1. 备份 Launcher
        backupLauncher = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("application/json"),
                uri -> {
                    if (uri != null) {
                        UnifiedBackupUtils.writeBackupToUri(this, uri, (success, msg) -> {
                            if (success) {
                                // 【优化】备份成功后，提供“查看”选项 (虽然JSON一般人不看，但保持一致性)
                                showSuccessSnackbar("备份成功", uri, "application/json");
                            } else {
                                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                }
        );

        // 2. 导出 CSV Launcher
        csvExportLauncher = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("text/csv"),
                uri -> {
                    if (uri != null) {
                        executorService.execute(() -> {
                            boolean success = CsvExportUtils.exportHistoryToCSV(this, uri, workoutDao.getAllHistory());
                            runOnUiThread(() -> {
                                if (success) {
                                    // 【优化】导出报表后，直接提示打开
                                    showSuccessSnackbar("报表已导出", uri, "text/csv");
                                } else {
                                    Toast.makeText(this, "导出失败", Toast.LENGTH_SHORT).show();
                                }
                            });
                        });
                    }
                }
        );

        // 3. 恢复 Launcher (保持原样)
        restoreLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri != null) {
                        UnifiedBackupUtils.restoreFromUri(this, uri, (success, msg) -> {
                            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                        });
                    }
                }
        );

        // ============================================================
        //  按钮点击事件 (文件名修改区域)
        // ============================================================

        // 备份按钮
        findViewById(R.id.btnBackup).setOnClickListener(v -> {
            // 【修改】精确到秒: Backup_20231026_143005.json
            String fileName = "Backup_" + getTimestamp() + ".json";
            backupLauncher.launch(fileName);
        });

        // 导出 CSV 按钮
        findViewById(R.id.btnExportCsv).setOnClickListener(v -> {
            // 【修改】精确到秒: Fitness_Report_20231026_143005.csv
            String fileName = "Fitness_Report_" + getTimestamp() + ".csv";
            csvExportLauncher.launch(fileName);
        });

        // 恢复按钮
        findViewById(R.id.btnRestore).setOnClickListener(v -> {
            restoreLauncher.launch(new String[]{"application/json", "*/*"});
        });

        // 清空历史按钮
        findViewById(R.id.btnClearAllHistory).setOnClickListener(v -> {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("⚠️ 最终警告")
                    .setMessage("确定清空所有记录？此操作无法撤销！")
                    .setPositiveButton("清空", (dialog, which) -> {
                        executorService.execute(() -> {
                            workoutDao.deleteAllHistory();
                            runOnUiThread(() -> Toast.makeText(this, "历史记录已清空", Toast.LENGTH_SHORT).show());
                        });
                    })
                    .setNegativeButton("取消", null)
                    .show();
        });
    }

    // ============================================================
    //  辅助方法：生成更精确的时间戳
    // ============================================================
    private String getTimestamp() {
        // 格式：yyyyMMdd_HHmmss (例如: 20231027_153020)
        return new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
    }

    // ============================================================
    //  【升级版】显示悬浮式 Snackbar (更醒目、带图标、位置上移)
    // ============================================================
    private void showSuccessSnackbar(String message, Uri fileUri, String mimeType) {
        // 1. 创建 Snackbar
        Snackbar snackbar = Snackbar.make(findViewById(android.R.id.content), "✅  " + message, Snackbar.LENGTH_LONG);

        // 2. 设置动作
        snackbar.setAction("立即查看", v -> {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(fileUri, mimeType);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(this, "无法打开文件", Toast.LENGTH_SHORT).show();
            }
        });

        // 3. 获取 Snackbar 的根视图
        android.view.View snackbarView = snackbar.getView();

        // ============ 【视觉层：圆角背景】 ============
        android.graphics.drawable.GradientDrawable background = new android.graphics.drawable.GradientDrawable();
        background.setColor(0xFF004D40); // 深青色底
        background.setCornerRadius(dpToPx(24)); // 大圆角
        snackbarView.setBackground(background);

        // ============ 【交互层：位置悬浮】 ============
        android.view.ViewGroup.LayoutParams params = snackbarView.getLayoutParams();
        if (params instanceof android.widget.FrameLayout.LayoutParams) {
            android.widget.FrameLayout.LayoutParams layoutParams = (android.widget.FrameLayout.LayoutParams) params;
            layoutParams.gravity = android.view.Gravity.TOP | android.view.Gravity.CENTER_HORIZONTAL;
            layoutParams.topMargin = dpToPx(110); // 避开标题
            layoutParams.leftMargin = dpToPx(24);
            layoutParams.rightMargin = dpToPx(24);
            layoutParams.bottomMargin = 0;
            snackbarView.setLayoutParams(layoutParams);
        }

        // ============ 【核心新增：字体“增肌”】 ============
        // 找到内部的消息文本控件
        android.widget.TextView tvMessage = snackbarView.findViewById(com.google.android.material.R.id.snackbar_text);
        // 找到内部的动作按钮控件
        android.widget.TextView tvAction = snackbarView.findViewById(com.google.android.material.R.id.snackbar_action);

        if (tvMessage != null) {
            tvMessage.setTextSize(17); // 增大字号
            tvMessage.setTextColor(android.graphics.Color.WHITE); // 纯白文字，最清晰
            tvMessage.setTypeface(null, android.graphics.Typeface.BOLD); // 加粗
            tvMessage.setMaxLines(2); // 防止字太大了换行显示不全
        }

        if (tvAction != null) {
            tvAction.setTextSize(17); // 按钮也同步增大，保持平衡
            tvAction.setTextColor(0xFFB2DFDB); // 浅青色高亮
            tvAction.setTypeface(null, android.graphics.Typeface.BOLD); // 加粗
        }

        // 补回 Padding，因为自定义背景会覆盖默认 Padding
        snackbarView.setPadding(dpToPx(20), dpToPx(14), dpToPx(20), dpToPx(14));

        snackbar.show();
    }

    // 辅助工具：dp 转 px
    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round((float) dp * density);
    }

    private void saveUnitSetting(boolean isLbs) {
        prefs.edit().putBoolean("DEFAULT_IS_LBS", isLbs).apply();
        executorService.execute(() -> {
            List<ExerciseEntity> list = workoutDao.getAllExercises();
            for (ExerciseEntity ex : list) {
                if (ex.isLbs != isLbs) {
                    if (isLbs) ex.weight = ex.weight * 2.20462;
                    else ex.weight = ex.weight / 2.20462;
                    ex.isLbs = isLbs;
                    workoutDao.update(ex);
                }
            }
            runOnUiThread(() -> Toast.makeText(this, "重量单位已更新", Toast.LENGTH_SHORT).show());
        });
    }
}