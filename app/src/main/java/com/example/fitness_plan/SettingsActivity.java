package com.example.fitness_plan;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.example.fitness_plan.data.AppDatabase;
import com.example.fitness_plan.data.ExerciseEntity;
import com.example.fitness_plan.data.HistoryEntity;
import com.example.fitness_plan.data.WorkoutDao;
import com.example.fitness_plan.utils.CsvExportUtils;
import com.example.fitness_plan.utils.MediaStoreUtils;
import com.example.fitness_plan.utils.UnifiedBackupUtils;
import com.google.android.material.snackbar.Snackbar; // 确保导入了 Snackbar

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
    private com.google.android.material.textfield.TextInputEditText etHeight, etWingspan, etBodyWeight;
    private android.widget.Button btnSaveBodyMetrics;

    // 【只保留恢复数据的 Launcher】备份和导出现在是静默的，不需要 Launcher
    private ActivityResultLauncher<Intent> restoreLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // 状态栏美化
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            getWindow().getDecorView().setSystemUiVisibility(
                    android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                            android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
            getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        }

        workoutDao = AppDatabase.getDatabase(this).workoutDao();
        prefs = getSharedPreferences("fitness_prefs", MODE_PRIVATE);

        // ============================================================
        //  新增：初始化身体维度模块
        // ============================================================
        etHeight = findViewById(R.id.etHeight);
        etWingspan = findViewById(R.id.etWingspan);
        etBodyWeight = findViewById(R.id.etBodyWeight);
        btnSaveBodyMetrics = findViewById(R.id.btnSaveBodyMetrics);

        // A. 读取已保存的数据（如果有的话，默认为空字符串或 0）
        float savedHeight = prefs.getFloat("USER_HEIGHT_CM", 0f);
        float savedWingspan = prefs.getFloat("USER_WINGSPAN_CM", 0f);
        float savedWeight = prefs.getFloat("USER_BODY_WEIGHT_KG", 0f);

        // 格式化：如果是 0 就不显示（留空让用户填），否则去掉多余的 ".0"
        if (savedHeight > 0) etHeight.setText(String.valueOf(savedHeight).replace(".0", ""));
        if (savedWingspan > 0) etWingspan.setText(String.valueOf(savedWingspan).replace(".0", ""));
        if (savedWeight > 0) etBodyWeight.setText(String.valueOf(savedWeight).replace(".0", ""));

        // B. 点击保存按钮的逻辑
        btnSaveBodyMetrics.setOnClickListener(v -> {
            try {
                // 读取输入框的文字
                String hStr = etHeight.getText() != null ? etHeight.getText().toString().trim() : "";
                String wStr = etWingspan.getText() != null ? etWingspan.getText().toString().trim() : "";
                String bwStr = etBodyWeight.getText() != null ? etBodyWeight.getText().toString().trim() : "";

                // 转换成数字（如果为空则存 0）
                float height = hStr.isEmpty() ? 0f : Float.parseFloat(hStr);
                float wingspan = wStr.isEmpty() ? 0f : Float.parseFloat(wStr);
                float bodyWeight = bwStr.isEmpty() ? 0f : Float.parseFloat(bwStr);

                // 简单的防呆校验：防止身高 3 米这种离谱数据破坏后续的算法
                if (height > 250 || wingspan > 250 || bodyWeight > 300) {
                    Toast.makeText(this, "输入的数据似乎有些惊人，请检查是否填错了单位", Toast.LENGTH_LONG).show();
                    return;
                }

                // 存入 SharedPreferences
                prefs.edit()
                        .putFloat("USER_HEIGHT_CM", height)
                        .putFloat("USER_WINGSPAN_CM", wingspan)
                        .putFloat("USER_BODY_WEIGHT_KG", bodyWeight)
                        .apply();

                // 为了防呆，保存完收起系统软键盘
                android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(Activity.INPUT_METHOD_SERVICE);
                if (imm != null && getCurrentFocus() != null) {
                    imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
                }

                Toast.makeText(this, "身体维度数据已保存！", Toast.LENGTH_SHORT).show();

            } catch (NumberFormatException e) {
                Toast.makeText(this, "格式错误，请确保只输入数字和小数点", Toast.LENGTH_SHORT).show();
            }
        });

        // 初始化控件
        unitRadioGroup = findViewById(R.id.unitRadioGroup);
        rbKg = findViewById(R.id.rbKg);
        rbLbs = findViewById(R.id.rbLbs);
        addStrategyRadioGroup = findViewById(R.id.addStrategyRadioGroup);
        rbAddToPlan = findViewById(R.id.rbAddToPlan);
        rbTemporary = findViewById(R.id.rbTemporary);

        boolean defaultIsLbs = prefs.getBoolean("DEFAULT_IS_LBS", false);
        if (defaultIsLbs) rbLbs.setChecked(true); else rbKg.setChecked(true);
        boolean defaultAddToPlan = prefs.getBoolean("DEFAULT_ADD_TO_PLAN", true);
        if (defaultAddToPlan) rbAddToPlan.setChecked(true); else rbTemporary.setChecked(true);

        unitRadioGroup.setOnCheckedChangeListener((group, checkedId) -> saveUnitSetting(checkedId == R.id.rbLbs));
        addStrategyRadioGroup.setOnCheckedChangeListener((group, checkedId) ->
                prefs.edit().putBoolean("DEFAULT_ADD_TO_PLAN", checkedId == R.id.rbAddToPlan).apply());


        // ============================================================
        //  1. 恢复数据 Launcher (只有这个需要系统文件选择器)
        // ============================================================
        restoreLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        if (uri != null) {
                            UnifiedBackupUtils.restoreFromUri(this, uri, (success, msg) -> {
                                if (success) {
                                    Toast.makeText(this, "✅ " + msg, Toast.LENGTH_LONG).show();
                                } else {
                                    Toast.makeText(this, "❌ " + msg, Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                    }
                }
        );

        // ============================================================
        //  2. 按钮点击事件
        // ============================================================

        // A. 备份按钮 -> 静默保存到 Downloads/FitnessPlan_Backup
        findViewById(R.id.btnBackup).setOnClickListener(v -> {
            String fileName = "Backup_" + getTimestamp() + ".json";

            executorService.execute(() -> {
                // 生成 JSON 内容
                String jsonContent = UnifiedBackupUtils.generateBackupJsonSync(this);

                if (jsonContent != null) {
                    // 静默保存
                    Uri savedUri = MediaStoreUtils.saveBackupToDownloads(this, fileName, jsonContent);

                    runOnUiThread(() -> {
                        if (savedUri != null) {
                            showSuccessSnackbar("备份成功", savedUri, "application/json");
                        } else {
                            Toast.makeText(this, "备份失败", Toast.LENGTH_SHORT).show();
                        }
                    });
                } else {
                    runOnUiThread(() -> Toast.makeText(this, "数据生成失败", Toast.LENGTH_SHORT).show());
                }
            });
        });

        // B. 导出报表 -> 静默保存到 Downloads 根目录
        findViewById(R.id.btnExportCsv).setOnClickListener(v -> {
            String fileName = "Report_" + getTimestamp() + ".csv";

            executorService.execute(() -> {
                // 【修正点】这里必须是 List<HistoryEntity>，因为 getAllHistory 返回的是历史记录
                List<HistoryEntity> list = workoutDao.getAllHistory();

                // 使用新的 CsvExportUtils.generateCsvContent 方法
                String csvContent = CsvExportUtils.generateCsvContent(list, workoutDao);

                // 静默保存
                Uri savedUri = MediaStoreUtils.saveReportToDownloads(this, fileName, csvContent);

                runOnUiThread(() -> {
                    if (savedUri != null) {
                        showSuccessSnackbar("报表已导出", savedUri, "text/csv");
                    } else {
                        Toast.makeText(this, "导出失败", Toast.LENGTH_SHORT).show();
                    }
                });
            });
        });

        // C. 恢复数据
        findViewById(R.id.btnRestore).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                // 【核心修改】更精准的 URI 构建
                // 1. 获取标准的 Download 文件夹名 (通常是 "Download"，不带 s)
                String downloadDir = Environment.DIRECTORY_DOWNLOADS;

                // 2. 拼接子目录: "Download/FitnessPlan_Backup"
                String path = downloadDir + "/" + MediaStoreUtils.BACKUP_SUB_DIR;

                // 3. 构建 URI
                // 注意：Android 的 ExternalStorageProvider 格式通常是 "primary:路径"
                // 必须使用 Uri.encode 防止空格或特殊字符导致解析失败
                Uri initialUri = Uri.parse("content://com.android.externalstorage.documents/document/primary:" + Uri.encode(path));

                intent.putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI, initialUri);
            }

            restoreLauncher.launch(intent);
        });

        // D. 打开文件夹按钮 -> 跳转系统"下载管理" APP
        findViewById(R.id.btnOpenFolder).setOnClickListener(v -> {
            try {
                Intent intent = new Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(this, "无法直接打开下载页，请手动打开文件管理", Toast.LENGTH_SHORT).show();
            }
        });

        // E. 清空历史
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
    //  辅助方法
    // ============================================================

    private String getTimestamp() {
        return new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
    }

    // ============================================================
    //  【防崩溃版】自定义顶部通知栏
    //  - 移除了 MaterialCardView，改用原生 LinearLayout + 背景绘制
    //  - 100% 稳健，杜绝闪退
    // ============================================================
    private void showSuccessSnackbar(String message, Uri fileUri, String mimeType) {
        // 1. 安全检查：确保不在后台线程操作 UI，且 Activity 未销毁
        if (isFinishing() || isDestroyed()) return;

        // 2. 获取根布局
        android.view.ViewGroup rootView = findViewById(android.R.id.content);
        if (rootView == null) return;

        // 3. 动态创建一个容器 (使用 LinearLayout 代替 CardView)
        android.widget.LinearLayout container = new android.widget.LinearLayout(this);
        container.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        container.setGravity(android.view.Gravity.CENTER_VERTICAL);
        container.setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12));

        // --- 关键：手动绘制圆角背景 (防崩溃) ---
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(0xFF004D40); // 深青色背景
        bg.setCornerRadius(dpToPx(28)); // 大圆角
        container.setBackground(bg);

        // --- 设置阴影 (Android 5.0+) ---
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            container.setElevation(dpToPx(8));
        }

        // --- 容器位置参数 ---
        android.widget.FrameLayout.LayoutParams params = new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
        );
        params.gravity = android.view.Gravity.TOP;
        // 【在这里调整高度】修改 50 这个数字。例如改成 80 会更靠下，改成 30 会更靠上。
        // 建议范围：30 ~ 100 之间调整
        params.topMargin = dpToPx(80); // 避开状态栏
        params.leftMargin = dpToPx(16);
        params.rightMargin = dpToPx(16);
        container.setLayoutParams(params);

        // --- A. 图标 (增加 try-catch 防止资源找不到导致的崩溃) ---
        android.widget.ImageView iconView = new android.widget.ImageView(this);
        try {
            iconView.setImageResource(R.drawable.ic_check_circle);
        } catch (Exception e) {
            // 如果找不到图标，就用系统默认的作为兜底，防止崩溃
            iconView.setImageResource(android.R.drawable.checkbox_on_background);
        }
        iconView.setColorFilter(0xFFB2DFDB); // 浅青色
        android.widget.LinearLayout.LayoutParams iconParams = new android.widget.LinearLayout.LayoutParams(dpToPx(24), dpToPx(24));
        iconParams.rightMargin = dpToPx(12);
        container.addView(iconView, iconParams);

        // --- B. 文字 ---
        android.widget.TextView tvMessage = new android.widget.TextView(this);
        tvMessage.setText(message);
        tvMessage.setTextSize(16); // 字号
        tvMessage.setTextColor(android.graphics.Color.WHITE);
        tvMessage.setTypeface(null, android.graphics.Typeface.BOLD);
        // 占满剩余空间
        android.widget.LinearLayout.LayoutParams textParams = new android.widget.LinearLayout.LayoutParams(
                0,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                1.0f
        );
        container.addView(tvMessage, textParams);

        // --- C. 按钮 ---
        android.widget.TextView btnAction = new android.widget.TextView(this);
        btnAction.setText("立即查看");
        btnAction.setTextSize(16);
        btnAction.setTextColor(0xFFB2DFDB); // 浅青色高亮
        btnAction.setTypeface(null, android.graphics.Typeface.BOLD);
        btnAction.setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4));

        // 点击波纹效果
        android.util.TypedValue outValue = new android.util.TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true);
        btnAction.setBackgroundResource(outValue.resourceId);

        btnAction.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(fileUri, mimeType);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(intent);
                dismissCustomSnackbar(container, rootView);
            } catch (Exception e) {
                Toast.makeText(this, "无法打开文件", Toast.LENGTH_SHORT).show();
            }
        });
        container.addView(btnAction);

        // 4. 添加到界面并开始动画
        rootView.addView(container);

        // --- 核心动画 ---
        container.setTranslationY(-dpToPx(150)); // 初始在屏幕外
        container.setAlpha(0f);

        container.animate()
                .translationY(0)
                .alpha(1f)
                .setDuration(400)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .start();

        // 5. 自动消失
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            dismissCustomSnackbar(container, rootView);
        }, 3500);
    }

    // 辅助：收回动画 (保持不变)
    private void dismissCustomSnackbar(android.view.View view, android.view.ViewGroup parent) {
        if (view.getParent() == null) return;

        view.animate()
                .translationY(-dpToPx(150))
                .alpha(0f)
                .setDuration(300)
                .setInterpolator(new android.view.animation.AccelerateInterpolator())
                .withEndAction(() -> parent.removeView(view))
                .start();
    }

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