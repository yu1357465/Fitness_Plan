package com.example.fitness_plan;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Switch;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.example.fitness_plan.data.AppDatabase;
import com.example.fitness_plan.data.WorkoutDao;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SettingsActivity extends AppCompatActivity {

    private Switch switchLbs;

    // 数据库相关
    private WorkoutDao workoutDao;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    // 备份相关启动器
    private ActivityResultLauncher<String> exportLauncher;
    private ActivityResultLauncher<String[]> importLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        setTitle("系统设置");

        // 1. 初始化数据库 (备份需要用到 DAO)
        workoutDao = AppDatabase.getDatabase(this).workoutDao();

        // 2. 初始化备份组件
        initBackupLaunchers();

        // 3. 单位切换逻辑
        switchLbs = findViewById(R.id.switchLbs);
        SharedPreferences prefs = getSharedPreferences("fitness_prefs", MODE_PRIVATE);
        boolean isLbs = prefs.getBoolean("DEFAULT_IS_LBS", false);
        switchLbs.setChecked(isLbs);

        switchLbs.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("DEFAULT_IS_LBS", isChecked).apply();
            Toast.makeText(SettingsActivity.this, "已保存，返回主页后生效", Toast.LENGTH_SHORT).show();
        });

        // 4. 绑定点击事件
        findViewById(R.id.btnExport).setOnClickListener(v -> {
            String fileName = "FitnessData_" + System.currentTimeMillis() + ".csv";
            exportLauncher.launch(fileName);
        });

        findViewById(R.id.btnImport).setOnClickListener(v -> {
            importLauncher.launch(new String[]{"text/*", "text/csv"});
        });
    }

    // --- 将 MainActivity 的备份逻辑搬运至此 ---
    private void initBackupLaunchers() {
        // 导出
        exportLauncher = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("text/csv"),
                uri -> {
                    if (uri != null) {
                        Toast.makeText(this, "正在生成备份...", Toast.LENGTH_SHORT).show();
                        executorService.execute(() -> {
                            boolean success = com.example.fitness_plan.utils.UnifiedBackupUtils.exportUnifiedData(this, uri, workoutDao);
                            runOnUiThread(() -> {
                                if (success) Toast.makeText(this, "导出成功！", Toast.LENGTH_SHORT).show();
                                else Toast.makeText(this, "导出失败", Toast.LENGTH_SHORT).show();
                            });
                        });
                    }
                }
        );

        // 导入
        importLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri != null) {
                        new androidx.appcompat.app.AlertDialog.Builder(this)
                                .setTitle("恢复备份")
                                .setMessage("即将覆盖现有数据，确定继续吗？")
                                .setPositiveButton("确定", (d, w) -> {
                                    Toast.makeText(this, "正在恢复...", Toast.LENGTH_SHORT).show();
                                    executorService.execute(() -> {
                                        boolean success = com.example.fitness_plan.utils.UnifiedBackupUtils.importUnifiedData(this, uri, workoutDao);
                                        runOnUiThread(() -> {
                                            if (success) Toast.makeText(this, "恢复成功！请重启App或返回刷新", Toast.LENGTH_LONG).show();
                                            else Toast.makeText(this, "文件格式错误", Toast.LENGTH_SHORT).show();
                                        });
                                    });
                                })
                                .setNegativeButton("取消", null)
                                .show();
                    }
                }
        );
    }
}