package com.example.fitness_plan;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.fitness_plan.data.AppDatabase;
import com.example.fitness_plan.data.ExerciseEntity;
import com.example.fitness_plan.data.HistoryEntity;
import com.example.fitness_plan.data.PlanEntity;
import com.example.fitness_plan.data.TemplateEntity;
import com.example.fitness_plan.data.WorkoutDao;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private WorkoutDao workoutDao;
    private AppDatabase db;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    private TextView tvPageTitle, tvActivePlanName;
    private RecyclerView mainRecyclerView;
    private Button btnFinishWorkout;

    private ExerciseRecyclerAdapter adapter;
    private List<ExerciseEntity> workoutPlan = new ArrayList<>();

    private boolean isLbsMode = false;
    private boolean lastIsLbs = false;
    private boolean defaultAddToPlan = true;
    private String currentPlanName = "自由训练";

    private ItemTouchHelper itemTouchHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        db = AppDatabase.getDatabase(this);
        workoutDao = db.workoutDao();

        tvPageTitle = findViewById(R.id.tvPageTitle);
        tvActivePlanName = findViewById(R.id.tvActivePlanName);
        mainRecyclerView = findViewById(R.id.mainRecyclerView);
        btnFinishWorkout = findViewById(R.id.btnFinishWorkout);

        mainRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        if (mainRecyclerView.getItemAnimator() instanceof androidx.recyclerview.widget.SimpleItemAnimator) {
            ((androidx.recyclerview.widget.SimpleItemAnimator) mainRecyclerView.getItemAnimator()).setSupportsChangeAnimations(false);
        }

        SharedPreferences prefs = getSharedPreferences("fitness_prefs", MODE_PRIVATE);
        lastIsLbs = prefs.getBoolean("DEFAULT_IS_LBS", false);
        isLbsMode = lastIsLbs;
        defaultAddToPlan = prefs.getBoolean("DEFAULT_ADD_TO_PLAN", true);

        loadDataFromDatabase();

        findViewById(R.id.btnSettings).setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        findViewById(R.id.btnHistory).setOnClickListener(v -> startActivity(new Intent(this, HistoryActivity.class)));

        findViewById(R.id.planSelectionContainer).setOnClickListener(v -> showPlanMenu(v));
        findViewById(R.id.daySelectionContainer).setOnClickListener(v -> showDayMenu(v));
        findViewById(R.id.fabAdd).setVisibility(View.GONE);

        // ... 在 onCreate 方法中 ...
        FloatingActionButton fab = findViewById(R.id.fabAdd);

        // 短按：添加新动作
        fab.setOnClickListener(v -> showAddDialog());

        // 【新增】长按：结束训练并归档
        fab.setOnLongClickListener(v -> {
            checkAndShowFinishDialog(); // 调用UI弹窗方法
            return true;
        });

        btnFinishWorkout.setOnClickListener(v -> finishAndSwitchCycle());
    }

    @Override
    protected void onResume() {
        super.onResume();
        SharedPreferences prefs = getSharedPreferences("fitness_prefs", MODE_PRIVATE);
        boolean currentIsLbs = prefs.getBoolean("DEFAULT_IS_LBS", false);
        defaultAddToPlan = prefs.getBoolean("DEFAULT_ADD_TO_PLAN", true);

        if (currentIsLbs != lastIsLbs) {
            lastIsLbs = currentIsLbs;
            isLbsMode = currentIsLbs;
            loadDataFromDatabase();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        clearGhostCard();
    }

    // =========================================================
    //  【新增】实时更新底部按钮状态
    // =========================================================
    private void updateFinishButtonState() {
        if (workoutPlan == null || workoutPlan.isEmpty()) {
            runOnUiThread(() -> {
                btnFinishWorkout.setText("今日无训练");
                btnFinishWorkout.setBackgroundColor(android.graphics.Color.parseColor("#B0BEC5"));
                btnFinishWorkout.setEnabled(false);
            });
            return;
        }

        int uncompletedCount = 0;
        for (ExerciseEntity ex : workoutPlan) {
            if (ex.name != null && !ex.name.equals("新动作") && !ex.isCompleted) {
                uncompletedCount++;
            }
        }

        int finalUncompletedCount = uncompletedCount;

        runOnUiThread(() -> {
            btnFinishWorkout.setEnabled(true);

            if (finalUncompletedCount == 0) {
                // 🟢 全部完成
                btnFinishWorkout.setText("完成打卡");
                btnFinishWorkout.setBackgroundColor(android.graphics.Color.parseColor("#4DB6AC"));
            } else {
                // 🟠 还有未完成
                String countStr = String.valueOf(finalUncompletedCount);
                String prefix = "还剩 ";
                String suffix = " 个动作结束";
                String fullText = prefix + countStr + suffix;

                android.text.SpannableString spannable = new android.text.SpannableString(fullText);

                int start = prefix.length();
                int end = start + countStr.length();

                // 【核心修改】使用自定义的 CenterScaleSpan，参数 1.8f 是放大倍数
                spannable.setSpan(new CenterScaleSpan(1.8f), start, end, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

                // 加粗一下数字，效果更好
                spannable.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), start, end, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

                btnFinishWorkout.setText(spannable);
                btnFinishWorkout.setBackgroundColor(android.graphics.Color.parseColor("#FB8C00"));
            }
        });
    }

    // =========================================================
    //  逻辑 A：添加空白卡片
    // =========================================================
    private void performAddEmptyCard() {
        ExerciseEntity removedGhost = clearGhostCardSync();

        executorService.execute(() -> {
            if (removedGhost != null) {
                workoutDao.delete(removedGhost);
            }

            int sortOrder = workoutDao.getAllExercises().size();
            ExerciseEntity newExercise = new ExerciseEntity("新动作", 0, 0, 0, false);
            newExercise.sortOrder = sortOrder;
            newExercise.isLbs = isLbsMode;

            if (defaultAddToPlan) {
                newExercise.color = "#FFFFFF";
            } else {
                newExercise.color = "#FFF9C4";
            }

            long id = workoutDao.insert(newExercise);
            newExercise.id = (int) id;

            runOnUiThread(() -> {
                if (adapter != null) {
                    adapter.addItem(newExercise);
                    mainRecyclerView.smoothScrollToPosition(adapter.getItemCount() - 1);
                }
            });
        });
    }

    private ExerciseEntity clearGhostCardSync() {
        if (adapter == null || workoutPlan == null) return null;
        ExerciseEntity ghostFound = null;
        for (int i = workoutPlan.size() - 1; i >= 0; i--) {
            ExerciseEntity ex = workoutPlan.get(i);
            if (ex.name.equals("新动作")) {
                ghostFound = ex;
                adapter.removeItem(ex);
                break;
            }
        }
        return ghostFound;
    }

    private void clearGhostCard() {
        ExerciseEntity ghost = clearGhostCardSync();
        if (ghost != null) {
            executorService.execute(() -> workoutDao.delete(ghost));
        }
    }

    // =========================================================
    //  逻辑 B：加载数据
    // =========================================================
    public void loadDataFromDatabase() {
        executorService.execute(() -> {
            PlanEntity activePlan = workoutDao.getActivePlan();
            SharedPreferences prefs = getSharedPreferences("fitness_prefs", MODE_PRIVATE);
            int savedIndex = prefs.getInt("PLAN_INDEX", 0);

            String finalDayTitle = "自由训练";
            String finalPlanName = "暂无计划";

            if (activePlan != null) {
                finalPlanName = activePlan.planName;
                List<String> days = workoutDao.getPlanDays(activePlan.planId);
                if (!days.isEmpty()) {
                    if (savedIndex >= days.size()) savedIndex = 0;
                    finalDayTitle = days.get(savedIndex);
                }
            }

            String titleForUI = finalDayTitle;
            String planNameForUI = finalPlanName;
            currentPlanName = finalDayTitle;

            workoutPlan = workoutDao.getAllExercises();

            Iterator<ExerciseEntity> iterator = workoutPlan.iterator();
            while (iterator.hasNext()) {
                ExerciseEntity ex = iterator.next();
                if (ex.name.equals("新动作")) {
                    workoutDao.delete(ex);
                    iterator.remove();
                }
            }

            Collections.sort(workoutPlan, (o1, o2) -> Integer.compare(o1.sortOrder, o2.sortOrder));

            runOnUiThread(() -> {
                if (tvPageTitle != null) tvPageTitle.setText(titleForUI);
                if (tvActivePlanName != null) tvActivePlanName.setText(planNameForUI);

                adapter = new ExerciseRecyclerAdapter(this, workoutPlan, isLbsMode, new ExerciseRecyclerAdapter.OnItemActionListener() {
                    @Override
                    public void onUpdate(ExerciseEntity exercise) {
                        if(!exercise.name.equals("新动作")) {
                            executorService.execute(() -> workoutDao.update(exercise));
                        }
                    }
                    @Override
                    public void onShowChart(LineChart chart, ExerciseEntity exercise) {
                        loadChartData(chart, exercise.name);
                    }
                    @Override
                    public void onItemLongClick(ExerciseEntity exercise) {}
                    @Override
                    public void onStartDrag(RecyclerView.ViewHolder holder) {
                        clearGhostCard();
                        if (itemTouchHelper != null) itemTouchHelper.startDrag(holder);
                    }
                    @Override
                    public void onOrderChanged() {
                        executorService.execute(() -> {
                            for (int i = 0; i < workoutPlan.size(); i++) {
                                ExerciseEntity e = workoutPlan.get(i);
                                e.sortOrder = i;
                                workoutDao.update(e);
                            }
                        });
                    }
                    @Override
                    public void onRename(ExerciseEntity exercise) {
                        showRenameDialog(exercise);
                    }
                    @Override
                    public void onDelete(ExerciseEntity exercise) {
                        handleSmartDelete(exercise);
                    }
                    @Override
                    public void onTogglePin(ExerciseEntity exercise) {
                        handleTogglePin(exercise);
                    }
                    @Override
                    public void onAddEmptyCard() {
                        performAddEmptyCard();
                    }

                    // 【关键修复】这里补上了接口方法
                    @Override
                    public void onCompletionChanged() {
                        updateFinishButtonState();
                    }
                });

                mainRecyclerView.setAdapter(adapter);
                setupItemTouchHelper();
                // 初始调用一次，更新按钮颜色
                updateFinishButtonState();
            });
        });
    }

    // =========================================================
    //  逻辑 C：重命名 (主页动作 - 全绿卡片风格)
    //  与计划管理页保持一致：悬浮绿卡、无Dim、键盘直出
    // =========================================================
    private void showRenameDialog(ExerciseEntity exercise) {
        // 1. 加载通用的绿色卡片布局
        View view = getLayoutInflater().inflate(R.layout.dialog_rename_center, null);

        android.widget.TextView tvTitle = view.findViewById(R.id.tvDialogTitle);
        android.widget.EditText etInput = view.findViewById(R.id.etRenameInput);
        View btnCancel = view.findViewById(R.id.btnCancel);
        View btnConfirm = view.findViewById(R.id.btnConfirm);

        // 2. 初始化数据
        tvTitle.setText("修改动作名称"); // 稍微区分一下标题

        boolean isGhost = exercise.name.equals("新动作");
        if (isGhost) {
            etInput.setText(""); // 如果是新动作，清空输入框方便直接输
        } else {
            etInput.setText(exercise.name);
            etInput.setSelection(exercise.name.length()); // 光标移到最后
        }

        // 3. 构建 Dialog
        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this)
                .setView(view)
                .setCancelable(true)
                .create();

        // 4. 设置窗口属性 (透明背景 + 无变暗 + 强制弹键盘)
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setDimAmount(0f); // 关键：背景不闪烁
            dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        }

        // 5. 事件监听
        btnCancel.setOnClickListener(v -> dialog.dismiss());

        // 确认按钮逻辑
        View.OnClickListener confirmAction = v -> {
            String newName = etInput.getText().toString().trim();
            if (!newName.isEmpty()) {
                String oldName = exercise.name;
                exercise.name = newName;

                // 数据库操作 (保留你原有的复杂逻辑)
                executorService.execute(() -> {
                    workoutDao.update(exercise);

                    // 如果是"新动作"且需要加入计划
                    if (isGhost && defaultAddToPlan) {
                        com.example.fitness_plan.data.PlanEntity activePlan = workoutDao.getActivePlan();
                        if (activePlan != null && currentPlanName != null) {
                            com.example.fitness_plan.data.TemplateEntity template = new com.example.fitness_plan.data.TemplateEntity();
                            template.planId = activePlan.planId;
                            template.dayName = currentPlanName;
                            template.exerciseName = newName;
                            template.defaultSets = 4;
                            template.defaultReps = 12;
                            template.sortOrder = exercise.sortOrder;
                            workoutDao.insertTemplate(template);
                        }
                    } else {
                        // 智能重命名：修改同名的其他动作
                        boolean isPermanent = exercise.color == null || exercise.color.equalsIgnoreCase("#FFFFFF");
                        if (isPermanent) {
                            workoutDao.smartRename(oldName, newName);
                        }
                    }
                    // 刷新主页数据
                    loadDataFromDatabase();
                });
                dialog.dismiss();
            }
        };

        btnConfirm.setOnClickListener(confirmAction);

        // 监听软键盘"完成"键
        etInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                confirmAction.onClick(v);
                return true;
            }
            return false;
        });

        // 监听 Dialog 关闭 (处理取消新建的情况)
        dialog.setOnDismissListener(d -> {
            // 如果是新动作且没改名(没点确定)，则删除这个临时卡片
            // 注意：这里需要一个标志位判断是否点击了确定，简单起见，可以检查 exercise.name 是否还是 "新动作"
            // 但因为我们已经 set text 了，最好是在 confirmAction 里设个 flag。
            // 简单处理：如果 exercise.name 还是 "新动作" 或者是 空，则删除
            if (isGhost && (exercise.name.equals("新动作") || exercise.name.isEmpty())) {
                deleteTempCard(exercise);
            }
        });

        dialog.show();

        // 双重保险：请求焦点
        etInput.requestFocus();
    }

    private void deleteTempCard(ExerciseEntity exercise) {
        if (adapter != null) adapter.removeItem(exercise);

        executorService.execute(() -> {
            workoutDao.delete(exercise);
            PlanEntity activePlan = workoutDao.getActivePlan();
            if (activePlan != null && currentPlanName != null) {
                workoutDao.deleteTemplateByName(activePlan.planId, currentPlanName, exercise.name);
            }
            runOnUiThread(() -> Toast.makeText(this, "已取消添加", Toast.LENGTH_SHORT).show());
        });
    }

    // ==========================================
    //  逻辑 D: 结束训练 (防闪退 + 全新UI版)
    // ==========================================

    // 1. 入口方法：先检查状态，再决定弹什么窗
    private void checkAndShowFinishDialog() {
        executorService.execute(() -> {
            // 先在后台查一下有没有没做完的
            List<ExerciseEntity> all = workoutDao.getAllExercises();
            boolean hasUnfinished = false;
            for (ExerciseEntity ex : all) {
                if (!ex.isCompleted) {
                    hasUnfinished = true;
                    break;
                }
            }

            boolean finalHasUnfinished = hasUnfinished;
            runOnUiThread(() -> {
                if (finalHasUnfinished) {
                    // 有未完成的 -> 弹出自定义选择弹窗
                    showCustomFinishDialog();
                } else {
                    // 全部都做完了 -> 直接简单确认，然后归档
                    showSimpleConfirmDialog();
                }
            });
        });
    }

    // 2. 情况A：自定义弹窗 (有未完成动作时)
    private void showCustomFinishDialog() {
        View view = getLayoutInflater().inflate(R.layout.dialog_finish_workout, null);
        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this)
                .setView(view)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setDimAmount(0.3f); // 稍微暗一点点背景，突出弹窗
        }

        // 绑定事件
        view.findViewById(R.id.btnMarkAll).setOnClickListener(v -> {
            performFinishWorkout(true); // 标记全部
            dialog.dismiss();
        });

        view.findViewById(R.id.btnArchiveChecked).setOnClickListener(v -> {
            performFinishWorkout(false); // 仅归档已做
            dialog.dismiss();
        });

        view.findViewById(R.id.btnCancel).setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    // 3. 情况B：简单确认 (全部做完时)
    private void showSimpleConfirmDialog() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("🎉 恭喜完成！")
                .setMessage("所有动作都已搞定，确定要归档并进入下一天吗？")
                .setPositiveButton("确定归档", (dialog, which) -> performFinishWorkout(false)) // 全都做完了，不需要自动标记逻辑，传false即可
                .setNegativeButton("再练练", null)
                .show();
    }

    // 4. 核心后台逻辑 (修复闪退的关键)
    private void performFinishWorkout(boolean autoMarkAll) {
        executorService.execute(() -> {
            try {
                // 第一阶段：处理数据状态
                if (autoMarkAll) {
                    // 1. 先把数据库里没勾的都勾上
                    List<ExerciseEntity> currentList = workoutDao.getAllExercises();
                    for (ExerciseEntity ex : currentList) {
                        if (!ex.isCompleted) {
                            ex.isCompleted = true;
                            workoutDao.update(ex);
                        }
                    }
                    // 【关键防闪退补丁】
                    // 更新完数据库后，必须重新查询一次！
                    // 否则后续逻辑可能操作的是旧对象，或者因并发导致空指针
                }

                // 第二阶段：获取最终数据进行归档
                // 无论是否 autoMarkAll，这里都重新查一遍，保证拿到的是最新、最热乎的数据
                List<ExerciseEntity> finalExercises = workoutDao.getAllExercises();

                if (finalExercises.isEmpty()) {
                    runOnUiThread(() -> android.widget.Toast.makeText(this, "没有数据可归档", android.widget.Toast.LENGTH_SHORT).show());
                    return;
                }

                long now = System.currentTimeMillis();
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy年MM月dd日", java.util.Locale.CHINA);
                String dateStr = sdf.format(new java.util.Date(now));

                com.example.fitness_plan.data.PlanEntity activePlan = workoutDao.getActivePlan();
                String planName = (activePlan != null && activePlan.planName != null) ? activePlan.planName : "自由训练";

                String currentDayName = (currentPlanName != null) ? currentPlanName : "临时训练";

                // 开始搬运数据到 History
                boolean hasFinishedItems = false;
                for (ExerciseEntity ex : finalExercises) {
                    if (ex.isCompleted) {
                        com.example.fitness_plan.data.HistoryEntity history = new com.example.fitness_plan.data.HistoryEntity(
                                now, dateStr, currentDayName, // 使用 训练日名称 (如 推力日)
                                ex.name, ex.weight, ex.reps, ex.sets
                        );
                        // 如果你之前的 HistoryEntity 有 workoutName 字段用来存 "计划-日子"，可以在这里拼装
                        // history.workoutName = planName + " - " + currentDayName;

                        workoutDao.insertHistory(history);
                        hasFinishedItems = true;
                    }
                }

                if (!hasFinishedItems) {
                    runOnUiThread(() -> android.widget.Toast.makeText(this, "没有已完成的动作，取消归档", android.widget.Toast.LENGTH_SHORT).show());
                    return;
                }

                // 第三阶段：清理与进阶
                workoutDao.clearCurrentPlan();

                // 自动加载下一天
                if (activePlan != null) {
                    List<String> days = workoutDao.getDayNamesByPlanId(activePlan.planId);
                    if (days != null && !days.isEmpty()) {
                        android.content.SharedPreferences prefs = getSharedPreferences("fitness_prefs", MODE_PRIVATE);
                        int currentIndex = prefs.getInt("PLAN_INDEX", 0);
                        int nextIndex = (currentIndex + 1) % days.size();

                        prefs.edit().putInt("PLAN_INDEX", nextIndex).apply();

                        String nextDayName = days.get(nextIndex);
                        List<com.example.fitness_plan.data.TemplateEntity> templates =
                                workoutDao.getTemplatesByPlanAndDay(activePlan.planId, nextDayName);

                        for (com.example.fitness_plan.data.TemplateEntity t : templates) {
                            ExerciseEntity e = new ExerciseEntity(t.exerciseName, t.defaultWeight, t.defaultSets, t.defaultReps, false);
                            e.sortOrder = t.sortOrder;
                            workoutDao.insert(e);
                        }
                        currentPlanName = nextDayName;
                    }
                }

                // 第四阶段：刷新 UI
                runOnUiThread(() -> {
                    android.widget.Toast.makeText(this, "✅ 已归档", android.widget.Toast.LENGTH_SHORT).show();
                    loadDataFromDatabase();
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> android.widget.Toast.makeText(this, "归档失败: " + e.getMessage(), android.widget.Toast.LENGTH_LONG).show());
            }
        });
    }

    // 1. 【新增】把这个方法加到 MainActivity 内部（放在 finishWorkout 下面即可）
    private void showAddDialog() {
        android.widget.EditText input = new android.widget.EditText(this);
        input.setHint("输入动作名称 (如: 哑铃弯举)");
        // 自动弹键盘
        input.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                input.post(() -> {
                    android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                    imm.showSoftInput(input, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
                });
            }
        });
        input.requestFocus();

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("添加今日动作")
                .setView(input)
                .setPositiveButton("添加", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty()) {
                        executorService.execute(() -> {
                            // 默认数据: 20kg, 4组, 12次
                            ExerciseEntity exercise = new ExerciseEntity(name, 20.0, 4, 12, false);
                            workoutDao.insert(exercise);
                            loadDataFromDatabase();
                        });
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // =========================================================
    //  其他逻辑 (图钉、菜单等)
    // =========================================================
    private void handleTogglePin(ExerciseEntity exercise) {
        if (exercise.name.equals("新动作")) return;

        executorService.execute(() -> {
            PlanEntity activePlan = workoutDao.getActivePlan();
            if (activePlan == null || currentPlanName == null) {
                runOnUiThread(() -> Toast.makeText(this, "当前无激活计划，无法固定", Toast.LENGTH_SHORT).show());
                return;
            }
            boolean isPermanent = exercise.color != null && exercise.color.equalsIgnoreCase("#FFFFFF");
            if (isPermanent) {
                exercise.color = "#FFF9C4";
                workoutDao.update(exercise);
                workoutDao.deleteTemplateByName(activePlan.planId, currentPlanName, exercise.name);
                runOnUiThread(() -> Toast.makeText(this, "已从计划中移除", Toast.LENGTH_SHORT).show());
            } else {
                exercise.color = "#FFFFFF";
                workoutDao.update(exercise);
                List<TemplateEntity> existing = workoutDao.getTemplatesByPlanAndDay(activePlan.planId, currentPlanName);
                boolean exists = false;
                for (TemplateEntity t : existing) { if (t.exerciseName.equals(exercise.name)) { exists = true; break; } }
                if (!exists) {
                    TemplateEntity template = new TemplateEntity();
                    template.planId = activePlan.planId;
                    template.dayName = currentPlanName;
                    template.exerciseName = exercise.name;
                    template.defaultSets = exercise.sets;
                    template.defaultReps = exercise.reps;
                    template.defaultWeight = exercise.weight;
                    template.sortOrder = existing.size();
                    workoutDao.insertTemplate(template);
                }
                runOnUiThread(() -> Toast.makeText(this, "已加入计划模板", Toast.LENGTH_SHORT).show());
            }
            loadDataFromDatabase();
        });
    }

    private void showPlanMenu(View anchor) {
        executorService.execute(() -> {
            List<PlanEntity> allPlans = workoutDao.getAllPlans();
            runOnUiThread(() -> {
                PopupMenu popup = new PopupMenu(this, anchor);
                Menu menu = popup.getMenu();
                for (PlanEntity plan : allPlans) {
                    MenuItem item = menu.add(1, plan.planId, 0, plan.planName);
                    if (plan.isActive) { item.setCheckable(true); item.setChecked(true); }
                }
                MenuItem manageItem = menu.add(2, 9999, 1, "⚙️ 管理所有计划...");
                popup.setOnMenuItemClickListener(item -> {
                    int id = item.getItemId();
                    if (id == 9999) { startActivity(new Intent(this, PlanListActivity.class)); }
                    else {
                        boolean isCurrent = false;
                        for (PlanEntity p : allPlans) { if (p.planId == id && p.isActive) { isCurrent = true; break; } }
                        if (isCurrent) return true;
                        performSwitchPlan(id);
                    }
                    return true;
                });
                popup.show();
            });
        });
    }

    private void showDayMenu(View anchor) {
        executorService.execute(() -> {
            PlanEntity activePlan = workoutDao.getActivePlan();
            if (activePlan == null) { runOnUiThread(() -> Toast.makeText(this, "当前没有激活的计划", Toast.LENGTH_SHORT).show()); return; }
            List<String> days = workoutDao.getPlanDays(activePlan.planId);
            if (days.isEmpty()) return;
            SharedPreferences prefs = getSharedPreferences("fitness_prefs", MODE_PRIVATE);
            int currentIndex = prefs.getInt("PLAN_INDEX", 0);
            runOnUiThread(() -> {
                PopupMenu popup = new PopupMenu(this, anchor);
                Menu menu = popup.getMenu();
                for (int i = 0; i < days.size(); i++) {
                    MenuItem item = menu.add(0, i, i, days.get(i));
                    if (i == currentIndex) { item.setCheckable(true); item.setChecked(true); }
                }
                popup.setOnMenuItemClickListener(item -> {
                    int index = item.getItemId();
                    if (index == currentIndex) return true;
                    String selectedDay = days.get(index);
                    prefs.edit().putInt("PLAN_INDEX", index).apply();
                    switchDayAndReload(activePlan, selectedDay);
                    return true;
                });
                popup.show();
            });
        });
    }

    private void switchDayAndReload(PlanEntity plan, String dayName) {
        executorService.execute(() -> {
            workoutDao.clearAllExercises();
            List<TemplateEntity> templates = workoutDao.getTemplatesByPlanAndDay(plan.planId, dayName);
            for (TemplateEntity t : templates) {
                ExerciseEntity e = new ExerciseEntity(t.exerciseName, t.defaultWeight, t.defaultSets, t.defaultReps, false);
                e.sortOrder = t.sortOrder;
                e.color = "#FFFFFF";
                workoutDao.insert(e);
            }
            loadDataFromDatabase();
        });
    }

    private void performSwitchPlan(int planId) {
        executorService.execute(() -> {
            workoutDao.deactivateAllPlans();
            workoutDao.activatePlan(planId);
            SharedPreferences prefs = getSharedPreferences("fitness_prefs", MODE_PRIVATE);
            prefs.edit().putInt("PLAN_INDEX", 0).apply();
            PlanEntity newPlan = workoutDao.getActivePlan();
            if (newPlan != null) {
                List<String> days = workoutDao.getPlanDays(newPlan.planId);
                if (!days.isEmpty()) {
                    workoutDao.clearAllExercises();
                    List<TemplateEntity> templates = workoutDao.getTemplatesByPlanAndDay(newPlan.planId, days.get(0));
                    for (TemplateEntity t : templates) {
                        ExerciseEntity e = new ExerciseEntity(t.exerciseName, t.defaultWeight, t.defaultSets, t.defaultReps, false);
                        e.sortOrder = t.sortOrder;
                        e.color = "#FFFFFF";
                        workoutDao.insert(e);
                    }
                }
            }
            loadDataFromDatabase();
            runOnUiThread(() -> Toast.makeText(this, "已切换计划", Toast.LENGTH_SHORT).show());
        });
    }

    private void handleSmartDelete(ExerciseEntity exercise) {
        executorService.execute(() -> {
            PlanEntity activePlan = workoutDao.getActivePlan();
            boolean isInTemplate = false;
            if (activePlan != null && currentPlanName != null) {
                List<TemplateEntity> temps = workoutDao.getTemplatesByPlanAndDay(activePlan.planId, currentPlanName);
                for (TemplateEntity t : temps) { if (t.exerciseName.equals(exercise.name)) { isInTemplate = true; break; } }
            }
            boolean finalIsInTemplate = isInTemplate;
            runOnUiThread(() -> {
                if (finalIsInTemplate) {
                    new AlertDialog.Builder(this).setTitle("删除计划动作").setMessage("该动作属于当前计划模板。").setPositiveButton("仅本次跳过", (d, w) -> performDelete(exercise, false)).setNegativeButton("永久移除", (d, w) -> performDelete(exercise, true)).setNeutralButton("取消", null).show();
                } else { performDelete(exercise, false); }
            });
        });
    }

    private void performDelete(ExerciseEntity exercise, boolean deleteFromTemplate) {
        int pos = workoutPlan.indexOf(exercise);
        if (pos != -1 && adapter != null) adapter.deleteItem(pos);
        executorService.execute(() -> {
            workoutDao.delete(exercise);
            if (deleteFromTemplate) {
                PlanEntity activePlan = workoutDao.getActivePlan();
                if (activePlan != null && currentPlanName != null) { workoutDao.deleteTemplateByName(activePlan.planId, currentPlanName, exercise.name); }
            }
            runOnUiThread(() -> com.google.android.material.snackbar.Snackbar.make(mainRecyclerView, "已删除 " + exercise.name, 4000).setAction("撤销", v -> { if (adapter != null) adapter.restoreItem(exercise, pos); executorService.execute(() -> workoutDao.insert(exercise)); }).setAnchorView(findViewById(R.id.btnFinishWorkout)).show());
        });
    }

    // =========================================================
    //  逻辑 D：完成训练 (改为底部结算面板)
    // =========================================================
    private void finishAndSwitchCycle() {
        // 先清理幽灵卡
        clearGhostCard();

        executorService.execute(() -> {
            List<ExerciseEntity> uncompletedExercises = new ArrayList<>();
            for (ExerciseEntity ex : workoutPlan) {
                if (!ex.isCompleted) uncompletedExercises.add(ex);
            }
            // 无论是否全部完成，都弹窗确认 (防止误触，增加仪式感)
            runOnUiThread(() -> showFinishSummaryDialog(uncompletedExercises));
        });
    }

    private void showFinishSummaryDialog(List<ExerciseEntity> uncompletedList) {
        BottomSheetDialog sheet = new BottomSheetDialog(this, com.google.android.material.R.style.Theme_Design_BottomSheetDialog);
        View view = getLayoutInflater().inflate(R.layout.dialog_bottom_sheet_finish, null);
        sheet.setContentView(view);

        // 保持统一的亮度设计
        if (sheet.getWindow() != null) sheet.getWindow().setDimAmount(0.0f);

        TextView tvTitle = view.findViewById(R.id.tvFinishTitle);
        TextView tvSubtitle = view.findViewById(R.id.tvFinishSubtitle);
        Button btnPrimary = view.findViewById(R.id.btnPrimaryAction);
        Button btnSecondary = view.findViewById(R.id.btnSecondaryAction);

        int count = uncompletedList.size();

        if (count == 0) {
            // >>> 情况 A: 全部完成 (庆祝模式)
            tvTitle.setText("🎉 训练完成！");
            tvTitle.setTextColor(Color.parseColor("#4DB6AC")); // 青色
            tvSubtitle.setText("所有动作均已搞定。\n准备好存档并进入下一个循环了吗？");

            btnPrimary.setText("确定存档");
            btnPrimary.setOnClickListener(v -> {
                checkAndShowFinishDialog();
                sheet.dismiss();
            });

            btnSecondary.setText("取消");
            btnSecondary.setOnClickListener(v -> sheet.dismiss());

        } else {
            // >>> 情况 B: 有未完成动作 (决策模式)
            tvTitle.setText("⚠️ 还有 " + count + " 个动作没做完");
            tvTitle.setTextColor(Color.parseColor("#FFAB91")); // 淡橙色/红色
            tvSubtitle.setText("你想怎么处理剩下的动作？");

            // 选项1: 全部标为完成 (懒人模式)
            btnPrimary.setText("全部标记完成并存档");
            btnPrimary.setOnClickListener(v -> {
                executorService.execute(() -> {
                    for (ExerciseEntity ex : uncompletedList) {
                        ex.isCompleted = true;
                        workoutDao.update(ex);
                    }
                    checkAndShowFinishDialog();
                });
                sheet.dismiss();
            });

            // 选项2: 仅保存已完成 (严格模式) - 这里我们把原来的"放弃"改个好听的名字
            btnSecondary.setText("跳过这些，仅存档已完成");
            btnSecondary.setTextColor(Color.parseColor("#666666"));
            btnSecondary.setOnClickListener(v -> {
                checkAndShowFinishDialog();
                sheet.dismiss();
            });
        }

        sheet.show();
    }

    // =========================================================
    //  【最终版】拖拽逻辑：限制把手拖拽 + 保留特效
    // =========================================================
    private void setupItemTouchHelper() {
        itemTouchHelper = new ItemTouchHelper(new ItemTouchHelper.Callback() {

            // 1. 【核心】禁用全局长按拖拽！
            // 这样长按卡片可以弹出菜单，只有触摸把手才能拖拽
            @Override
            public boolean isLongPressDragEnabled() {
                return false;
            }

            // 2. 动作方向
            @Override
            public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                return makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0);
            }

            // 3. 拖拽时的视觉特效
            @Override
            public void onSelectedChanged(@androidx.annotation.Nullable RecyclerView.ViewHolder viewHolder, int actionState) {
                super.onSelectedChanged(viewHolder, actionState);
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder instanceof ExerciseRecyclerAdapter.ItemViewHolder) {
                    ExerciseRecyclerAdapter.ItemViewHolder holder = (ExerciseRecyclerAdapter.ItemViewHolder) viewHolder;
                    clearGhostCard();
                    holder.cardNormal.setStrokeColor(Color.parseColor("#4DB6AC"));
                    holder.cardNormal.setStrokeWidth(6);
                    holder.itemView.setScaleX(1.02f);
                    holder.itemView.setScaleY(1.02f);
                    holder.itemView.setAlpha(0.9f);
                }
            }

            // 4. 松手恢复
            // 【核心修复】拖拽结束：恢复原本样式
            @Override
            public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);

                if (viewHolder instanceof ExerciseRecyclerAdapter.ItemViewHolder) {
                    ExerciseRecyclerAdapter.ItemViewHolder holder = (ExerciseRecyclerAdapter.ItemViewHolder) viewHolder;

                    // 1. 恢复物理形变 (缩放、透明度)
                    holder.itemView.setScaleX(1.0f);
                    holder.itemView.setScaleY(1.0f);
                    holder.itemView.setAlpha(1.0f);

                    // 2. 【关键】不要在这里手动 setStrokeWidth(0) 了！
                    // 直接通知 Adapter 刷新这一行。
                    // Adapter 会重新运行 bindItem，根据是"计划"还是"临时"自动画出正确的边框。
                    int position = holder.getAdapterPosition();
                    if (position != RecyclerView.NO_POSITION && adapter != null) {
                        adapter.notifyItemChanged(position);
                    }
                }
            }

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder source, @NonNull RecyclerView.ViewHolder target) {
                if (adapter != null) {
                    adapter.onItemMove(source.getAdapterPosition(), target.getAdapterPosition());
                    return true;
                }
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {}
        });
        itemTouchHelper.attachToRecyclerView(mainRecyclerView);
    }

    private void loadChartData(LineChart chart, String exerciseName) { executorService.execute(() -> { List<HistoryEntity> historyList = workoutDao.getHistoryByName(exerciseName); Collections.sort(historyList, (h1, h2) -> Long.compare(h1.date, h2.date)); int start = Math.max(0, historyList.size() - 10); List<HistoryEntity> recentList = historyList.subList(start, historyList.size()); if (recentList.isEmpty()) { runOnUiThread(() -> { chart.setNoDataText("暂无历史数据"); chart.invalidate(); }); return; } List<Entry> entries = new ArrayList<>(); List<String> labels = new ArrayList<>(); SimpleDateFormat sdf = new SimpleDateFormat("MM-dd", Locale.getDefault()); for (int i = 0; i < recentList.size(); i++) { HistoryEntity h = recentList.get(i); entries.add(new Entry(i, (float) h.weight)); labels.add(sdf.format(new Date(h.date))); } runOnUiThread(() -> setupChart(chart, entries, labels)); }); }

    private void setupChart(LineChart chart, List<Entry> entries, List<String> labels) { LineDataSet dataSet = new LineDataSet(entries, "重量趋势"); dataSet.setColor(Color.parseColor("#4DB6AC")); dataSet.setLineWidth(2f); dataSet.setCircleColor(Color.parseColor("#4DB6AC")); dataSet.setCircleRadius(4f); dataSet.setDrawValues(true); dataSet.setValueTextSize(10f); dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER); LineData lineData = new LineData(dataSet); chart.setData(lineData); chart.setDescription(null); chart.getLegend().setEnabled(false); XAxis xAxis = chart.getXAxis(); xAxis.setPosition(XAxis.XAxisPosition.BOTTOM); xAxis.setGranularity(1f); xAxis.setValueFormatter(new IndexAxisValueFormatter(labels)); xAxis.setDrawGridLines(false); chart.getAxisRight().setEnabled(false); chart.getAxisLeft().setDrawGridLines(true); chart.animateX(500); chart.invalidate(); }

    /**
     * 自定义 Span：放大文字并垂直居中
     */
    public static class CenterScaleSpan extends android.text.style.ReplacementSpan {
        private final float scale;

        public CenterScaleSpan(float scale) {
            this.scale = scale;
        }

        @Override
        public int getSize(@NonNull android.graphics.Paint paint, CharSequence text, int start, int end, @androidx.annotation.Nullable android.graphics.Paint.FontMetricsInt fm) {
            android.graphics.Paint newPaint = new android.graphics.Paint(paint);
            newPaint.setTextSize(paint.getTextSize() * scale);
            // 如果需要更新 FontMetrics 来撑开行高，可以在这里操作 fm
            // 但为了简单，我们通常只返回宽度
            return (int) newPaint.measureText(text, start, end);
        }

        @Override
        public void draw(@NonNull android.graphics.Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, @NonNull android.graphics.Paint paint) {
            android.graphics.Paint newPaint = new android.graphics.Paint(paint);
            newPaint.setTextSize(paint.getTextSize() * scale);

            // 计算垂直偏移量
            android.graphics.Paint.FontMetricsInt originalFm = paint.getFontMetricsInt();
            android.graphics.Paint.FontMetricsInt newFm = newPaint.getFontMetricsInt();

            // 原文字（小字）的垂直中心
            float originalCenter = (originalFm.descent + originalFm.ascent) / 2f;
            // 新文字（大字）的垂直中心
            float newCenter = (newFm.descent + newFm.ascent) / 2f;

            // 偏移量 = 原中心 - 新中心
            float dy = originalCenter - newCenter;

            // 绘制
            canvas.drawText(text, start, end, x, y + dy, newPaint);
        }
    }
}