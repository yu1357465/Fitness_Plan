package com.example.fitness_plan;

import android.app.Activity;
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
import com.example.fitness_plan.utils.UnifiedBackupUtils;

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

    // 两个启动器：一个用于保存(Create)，一个用于打开(Open)
    private ActivityResultLauncher<String> backupLauncher;
    private ActivityResultLauncher<String[]> restoreLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        workoutDao = AppDatabase.getDatabase(this).workoutDao();
        prefs = getSharedPreferences("fitness_prefs", MODE_PRIVATE);

        unitRadioGroup = findViewById(R.id.unitRadioGroup);
        rbKg = findViewById(R.id.rbKg);
        rbLbs = findViewById(R.id.rbLbs);

        addStrategyRadioGroup = findViewById(R.id.addStrategyRadioGroup);
        rbAddToPlan = findViewById(R.id.rbAddToPlan);
        rbTemporary = findViewById(R.id.rbTemporary);

        // 1. 回显设置
        boolean defaultIsLbs = prefs.getBoolean("DEFAULT_IS_LBS", false);
        if (defaultIsLbs) rbLbs.setChecked(true);
        else rbKg.setChecked(true);

        boolean defaultAddToPlan = prefs.getBoolean("DEFAULT_ADD_TO_PLAN", true);
        if (defaultAddToPlan) rbAddToPlan.setChecked(true);
        else rbTemporary.setChecked(true);

        // 2. 监听器
        unitRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            boolean isLbs = (checkedId == R.id.rbLbs);
            saveUnitSetting(isLbs);
        });

        addStrategyRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            boolean addToPlan = (checkedId == R.id.rbAddToPlan);
            prefs.edit().putBoolean("DEFAULT_ADD_TO_PLAN", addToPlan).apply();
        });

        // ============================================================
        //  3. 注册文件操作 Launcher (本地保存/本地读取)
        // ============================================================

        // A. 备份 Launcher (CreateDocument)
        // 作用：打开系统保存对话框，用户选好位置后，返回 Uri
        backupLauncher = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("application/json"),
                uri -> {
                    if (uri != null) {
                        UnifiedBackupUtils.writeBackupToUri(this, uri, (success, msg) -> {
                            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                        });
                    }
                }
        );

        // B. 恢复 Launcher (OpenDocument)
        // 作用：打开系统文件选择器，用户选中文件后，返回 Uri
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

        // 4. 绑定按钮点击
        findViewById(R.id.btnBackup).setOnClickListener(v -> {
            // 生成默认文件名，如: Backup_20231025.json
            String fileName = "Backup_" + new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(new Date()) + ".json";
            // 启动系统保存界面
            backupLauncher.launch(fileName);
        });

        findViewById(R.id.btnRestore).setOnClickListener(v -> {
            // 启动系统选择界面，限制为 json 或所有文件
            restoreLauncher.launch(new String[]{"application/json", "*/*"});
        });
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