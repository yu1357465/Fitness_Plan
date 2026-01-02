package com.example.fitness_plan;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    // 数据库与工具
    private WorkoutDao workoutDao;
    private AppDatabase db;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    // UI 控件
    private TextView tvPageTitle, tvActivePlanName;
    private RecyclerView mainRecyclerView;
    private Button btnFinishWorkout;

    // 数据源与适配器
    private ExerciseRecyclerAdapter adapter;
    private List<ExerciseEntity> workoutPlan = new ArrayList<>();

    // 状态变量
    private boolean isLbsMode = false;
    private boolean lastIsLbs = false;
    private String currentPlanName = "自由训练"; // 当前训练日名称 (如: 推力日)

    // 滑动帮助类
    private ItemTouchHelper itemTouchHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 1. 初始化数据库
        db = AppDatabase.getDatabase(this);
        workoutDao = db.workoutDao();

        // 2. 初始化视图控件
        tvPageTitle = findViewById(R.id.tvPageTitle);
        tvActivePlanName = findViewById(R.id.tvActivePlanName);
        mainRecyclerView = findViewById(R.id.mainRecyclerView);
        btnFinishWorkout = findViewById(R.id.btnFinishWorkout);

        // 设置 RecyclerView
        mainRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        // 关闭默认动画，防止刷新时闪烁
        if (mainRecyclerView.getItemAnimator() instanceof androidx.recyclerview.widget.SimpleItemAnimator) {
            ((androidx.recyclerview.widget.SimpleItemAnimator) mainRecyclerView.getItemAnimator()).setSupportsChangeAnimations(false);
        }

        // 3. 读取全局设置 (单位)
        SharedPreferences prefs = getSharedPreferences("fitness_prefs", MODE_PRIVATE);
        lastIsLbs = prefs.getBoolean("DEFAULT_IS_LBS", false);
        isLbsMode = lastIsLbs;

        // 4. 加载数据
        loadDataFromDatabase();

        // 5. 绑定点击事件
        findViewById(R.id.btnSettings).setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        findViewById(R.id.btnHistory).setOnClickListener(v -> startActivity(new Intent(this, HistoryActivity.class)));

        // 标题点击跳转
        findViewById(R.id.planSelectionContainer).setOnClickListener(v -> showPlanSelectionDialog());
        findViewById(R.id.daySelectionContainer).setOnClickListener(v -> showDaySelectionDialog());

        // 核心功能点击
        findViewById(R.id.fabAdd).setOnClickListener(v -> showSmartAddDialog());
        btnFinishWorkout.setOnClickListener(v -> finishAndSwitchCycle());
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 检查设置是否改变 (比如单位切换)
        SharedPreferences prefs = getSharedPreferences("fitness_prefs", MODE_PRIVATE);
        boolean currentIsLbs = prefs.getBoolean("DEFAULT_IS_LBS", false);
        if (currentIsLbs != lastIsLbs) {
            lastIsLbs = currentIsLbs;
            isLbsMode = currentIsLbs;
            loadDataFromDatabase();
        }
    }

    // =========================================================
    //  核心逻辑：加载数据
    // =========================================================
    public void loadDataFromDatabase() {
        executorService.execute(() -> {
            // 1. 获取当前计划和训练日信息
            PlanEntity activePlan = workoutDao.getActivePlan();
            SharedPreferences prefs = getSharedPreferences("fitness_prefs", MODE_PRIVATE);
            int savedIndex = prefs.getInt("PLAN_INDEX", 0);

            String finalDayTitle = "自由训练";
            String finalPlanName = "暂无计划";

            if (activePlan != null) {
                finalPlanName = activePlan.planName;
                List<String> days = workoutDao.getPlanDays(activePlan.planId);
                // 校验索引防止越界
                if (!days.isEmpty()) {
                    if (savedIndex >= days.size()) savedIndex = 0; // 越界归零
                    finalDayTitle = days.get(savedIndex);
                }
            }

            // 临时变量用于 UI 线程更新
            String titleForUI = finalDayTitle;
            String planNameForUI = finalPlanName;
            currentPlanName = finalDayTitle;

            // 2. 加载今日动作列表
            workoutPlan = workoutDao.getAllExercises();
            // 按 sort_order 排序
            Collections.sort(workoutPlan, (o1, o2) -> Integer.compare(o1.sortOrder, o2.sortOrder));

            // 3. 回到主线程更新 UI
            runOnUiThread(() -> {
                if (tvPageTitle != null) tvPageTitle.setText(titleForUI);
                if (tvActivePlanName != null) tvActivePlanName.setText(planNameForUI);

                // 初始化 Adapter
                adapter = new ExerciseRecyclerAdapter(this, workoutPlan, isLbsMode, new ExerciseRecyclerAdapter.OnItemActionListener() {
                    @Override
                    public void onUpdate(ExerciseEntity exercise) {
                        executorService.execute(() -> workoutDao.update(exercise));
                    }
                    @Override
                    public void onShowChart(LineChart chart, ExerciseEntity exercise) {
                        loadChartData(chart, exercise.name);
                    }
                    @Override
                    public void onItemLongClick(ExerciseEntity exercise) {
                        // 可以留空或加震动反馈
                    }
                    @Override
                    public void onStartDrag(RecyclerView.ViewHolder holder) {
                        if (itemTouchHelper != null) itemTouchHelper.startDrag(holder);
                    }
                    @Override
                    public void onOrderChanged() {
                        // 拖拽排序后保存顺序
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
                });

                mainRecyclerView.setAdapter(adapter);
                // 设置滑动/拖拽逻辑
                setupItemTouchHelper();
            });
        });
    }

// ... 请继续拼接第二部分 ...
// =========================================================
//  逻辑 A：智能添加动作 (输入框 + 永久复选框)
// =========================================================
private void showSmartAddDialog() {
    // 先在后台查计划，避免主线程卡顿
    executorService.execute(() -> {
        PlanEntity activePlan = workoutDao.getActivePlan();

        runOnUiThread(() -> {
            android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
            layout.setOrientation(android.widget.LinearLayout.VERTICAL);
            layout.setPadding(60, 40, 60, 20);

            final android.widget.EditText input = new android.widget.EditText(this);
            input.setHint("输入动作名称 (如: 侧平举)");
            layout.addView(input);

            final android.widget.CheckBox cbPermanent = new android.widget.CheckBox(this);
            cbPermanent.setText("同时加入当前计划模板 (永久)");
            cbPermanent.setTextSize(14);
            cbPermanent.setTextColor(Color.GRAY);

            android.widget.LinearLayout.LayoutParams params = new android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.topMargin = 30;
            cbPermanent.setLayoutParams(params);

            // 有计划才显示复选框
            if (activePlan != null) {
                layout.addView(cbPermanent);
            }

            AlertDialog dialog = new AlertDialog.Builder(this)
                    .setTitle("添加动作")
                    .setView(layout)
                    .setPositiveButton("添加", (d, which) -> {
                        String name = input.getText().toString().trim();
                        boolean isPermanent = cbPermanent.isChecked();
                        if (!name.isEmpty()) {
                            performAddExercise(name, isPermanent);
                        }
                    })
                    .setNegativeButton("取消", null)
                    .create();

            // 自动弹出键盘
            dialog.setOnShowListener(d -> {
                input.requestFocus();
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
            });
            dialog.show();
        });
    });
}

    private void performAddExercise(String name, boolean isPermanent) {
        executorService.execute(() -> {
            int sortOrder = workoutDao.getAllExercises().size();

            ExerciseEntity newExercise = new ExerciseEntity(name, 0, 0, 0, false);
            newExercise.sortOrder = sortOrder;
            newExercise.isLbs = isLbsMode;
            workoutDao.insert(newExercise);

            if (isPermanent) {
                PlanEntity activePlan = workoutDao.getActivePlan();
                if (activePlan != null && currentPlanName != null) {
                    // 防止重复添加
                    List<TemplateEntity> existing = workoutDao.getTemplatesByPlanAndDay(activePlan.planId, currentPlanName);
                    boolean exists = false;
                    for (TemplateEntity t : existing) {
                        if (t.exerciseName.equals(name)) {
                            exists = true;
                            break;
                        }
                    }

                    if (!exists) {
                        TemplateEntity template = new TemplateEntity();
                        template.planId = activePlan.planId;
                        template.dayName = currentPlanName;
                        template.exerciseName = name;
                        template.defaultSets = 4;
                        template.defaultReps = 12;
                        template.defaultWeight = 0;
                        template.sortOrder = existing.size(); // 放在末尾
                        workoutDao.insertTemplate(template);
                    }
                }
            }

            loadDataFromDatabase();
            runOnUiThread(() -> Toast.makeText(MainActivity.this, isPermanent ? "已加入计划" : "已添加临时动作", Toast.LENGTH_SHORT).show());
        });
    }

    // =========================================================
    //  逻辑 B：智能删除动作 (区分临时/永久)
    // =========================================================
    private void handleSmartDelete(ExerciseEntity exercise) {
        executorService.execute(() -> {
            PlanEntity activePlan = workoutDao.getActivePlan();
            boolean isInTemplate = false;

            if (activePlan != null && currentPlanName != null) {
                List<TemplateEntity> temps = workoutDao.getTemplatesByPlanAndDay(activePlan.planId, currentPlanName);
                for (TemplateEntity t : temps) {
                    if (t.exerciseName.equals(exercise.name)) {
                        isInTemplate = true;
                        break;
                    }
                }
            }

            boolean finalIsInTemplate = isInTemplate;
            runOnUiThread(() -> {
                if (finalIsInTemplate) {
                    new AlertDialog.Builder(this)
                            .setTitle("删除计划动作")
                            .setMessage("该动作属于当前计划模板。")
                            .setPositiveButton("仅本次跳过", (d, w) -> performDelete(exercise, false))
                            .setNegativeButton("永久移除", (d, w) -> performDelete(exercise, true))
                            .setNeutralButton("取消", null)
                            .show();
                } else {
                    performDelete(exercise, false);
                }
            });
        });
    }

    private void performDelete(ExerciseEntity exercise, boolean deleteFromTemplate) {
        // 先从 UI 移除，感觉更快
        int pos = workoutPlan.indexOf(exercise);
        if (pos != -1 && adapter != null) {
            adapter.deleteItem(pos);
        }

        executorService.execute(() -> {
            workoutDao.delete(exercise);
            if (deleteFromTemplate) {
                PlanEntity activePlan = workoutDao.getActivePlan();
                if (activePlan != null && currentPlanName != null) {
                    workoutDao.deleteTemplateByName(activePlan.planId, currentPlanName, exercise.name);
                }
            }

            runOnUiThread(() -> com.google.android.material.snackbar.Snackbar.make(mainRecyclerView, "已删除 " + exercise.name, 4000)
                    .setAction("撤销", v -> {
                        if (adapter != null) adapter.restoreItem(exercise, pos);
                        executorService.execute(() -> {
                            workoutDao.insert(exercise);
                            // 注意：撤销不恢复模板删除，这是为了逻辑简化
                        });
                    })
                    .setAnchorView(findViewById(R.id.fabAdd))
                    .show());
        });
    }

    // =========================================================
    //  逻辑 C：完成训练 (包含自动切换下一天)
    // =========================================================
    private void finishAndSwitchCycle() {
        executorService.execute(() -> {
            List<ExerciseEntity> uncompletedExercises = new ArrayList<>();
            for (ExerciseEntity ex : workoutPlan) {
                if (!ex.isCompleted) uncompletedExercises.add(ex);
            }

            if (!uncompletedExercises.isEmpty()) {
                runOnUiThread(() -> showUncompletedDialog(uncompletedExercises));
            } else {
                saveAndFinish();
            }
        });
    }

    private void showUncompletedDialog(List<ExerciseEntity> uncompletedList) {
        new AlertDialog.Builder(this)
                .setTitle("还有 " + uncompletedList.size() + " 个动作未完成")
                .setMessage("你想如何处理？")
                .setPositiveButton("全部标记完成", (dialog, which) -> {
                    executorService.execute(() -> {
                        for (ExerciseEntity ex : uncompletedList) {
                            ex.isCompleted = true;
                            workoutDao.update(ex);
                        }
                        saveAndFinish();
                    });
                })
                .setNegativeButton("放弃这些动作", (dialog, which) -> {
                    executorService.execute(this::saveAndFinish);
                })
                .setNeutralButton("取消", null)
                .show();
    }

    private void saveAndFinish() {
        PlanEntity activePlan = workoutDao.getActivePlan();
        String planName = (activePlan != null) ? activePlan.planName : "自由训练";
        String dayTitle = (currentPlanName != null) ? currentPlanName : "临时训练";
        String fullTitle = planName + " - " + dayTitle;

        long currentTime = System.currentTimeMillis();

        // 1. 保存历史
        for (ExerciseEntity ex : workoutPlan) {
            if (ex.isCompleted) {
                HistoryEntity history = new HistoryEntity(
                        currentTime,
                        ex.name,
                        ex.weight,
                        ex.sets,
                        ex.reps,
                        isLbsMode
                );
                history.workoutTitle = fullTitle;
                workoutDao.insertHistory(history);
            }
        }

        // 2. 切换到下一天 (如果有计划)
        if (activePlan != null) {
            List<String> days = workoutDao.getPlanDays(activePlan.planId);
            if (!days.isEmpty()) {
                SharedPreferences prefs = getSharedPreferences("fitness_prefs", MODE_PRIVATE);
                int currentIndex = prefs.getInt("PLAN_INDEX", 0);
                int nextIndex = (currentIndex + 1) % days.size();
                prefs.edit().putInt("PLAN_INDEX", nextIndex).apply();
            }
        }

        // 3. 清空今日列表
        workoutDao.clearAllExercises();

        // 4. 加载下一天的模板到今日列表
        if (activePlan != null) {
            SharedPreferences prefs = getSharedPreferences("fitness_prefs", MODE_PRIVATE);
            int nextIndex = prefs.getInt("PLAN_INDEX", 0);
            List<String> days = workoutDao.getPlanDays(activePlan.planId);

            if (!days.isEmpty()) {
                String nextDayName = days.get(nextIndex);
                List<TemplateEntity> templates = workoutDao.getTemplatesByPlanAndDay(activePlan.planId, nextDayName);
                for (TemplateEntity t : templates) {
                    ExerciseEntity e = new ExerciseEntity(t.exerciseName, t.defaultWeight, t.defaultSets, t.defaultReps, false);
                    e.sortOrder = t.sortOrder;
                    workoutDao.insert(e);
                }
            }
        }

        // 5. 刷新界面
        loadDataFromDatabase();
        runOnUiThread(() -> Toast.makeText(MainActivity.this, "训练完成！已存档", Toast.LENGTH_SHORT).show());
    }

// ... 请继续拼接第三部分 ...
// =========================================================
//  逻辑 D：ItemTouchHelper 实现胶卷式滑动
// =========================================================
private void setupItemTouchHelper() {
    itemTouchHelper = new ItemTouchHelper(new ItemTouchHelper.Callback() {

        @Override
        public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
            // 上下拖拽排序，左右滑动操作
            return makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN,
                    ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT);
        }

        @Override
        public float getSwipeThreshold(@NonNull RecyclerView.ViewHolder viewHolder) {
            return 0.3f; // 30% 触发阈值
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
        public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
            int position = viewHolder.getAdapterPosition();
            ExerciseEntity item = adapter.getItem(position);
            if (item == null) return;

            if (direction == ItemTouchHelper.LEFT) {
                // 左滑 -> 切换删除模式 (露出右侧红卡)
                item.isDeleteConfirmMode = true;
            } else if (direction == ItemTouchHelper.RIGHT) {
                // 右滑 -> 切换完成状态 / 取消删除 (露出左侧灰卡)
                if (item.isDeleteConfirmMode) {
                    item.isDeleteConfirmMode = false;
                } else {
                    item.isCompleted = !item.isCompleted;
                    executorService.execute(() -> workoutDao.update(item));
                }
            }
            // 刷新 UI
            adapter.notifyItemChanged(position);
        }

        @Override
        public boolean isLongPressDragEnabled() {
            return false; // 禁用长按，只用把手拖拽
        }

        @Override
        public void onChildDraw(@NonNull android.graphics.Canvas c, @NonNull RecyclerView recyclerView,
                                @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY,
                                int actionState, boolean isCurrentlyActive) {

            if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                if (viewHolder instanceof ExerciseRecyclerAdapter.ViewHolder) {
                    ExerciseRecyclerAdapter.ViewHolder holder = (ExerciseRecyclerAdapter.ViewHolder) viewHolder;
                    float width = holder.itemView.getWidth();

                    // 核心：手动移动 View，实现胶卷连带效果
                    holder.cardNormal.setTranslationX(dX);

                    if (dX < 0) {
                        // 左滑：拉出右边的红卡
                        holder.cardDelete.setVisibility(View.VISIBLE);
                        holder.cardCompleted.setVisibility(View.INVISIBLE);
                        holder.cardDelete.setTranslationX(width + dX);
                    } else if (dX > 0) {
                        // 右滑：拉出左边的灰卡
                        holder.cardCompleted.setVisibility(View.VISIBLE);
                        holder.cardDelete.setVisibility(View.INVISIBLE);
                        holder.cardCompleted.setTranslationX(dX - width);
                    }
                    return; // 接管绘制，不调 super
                }
            }
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
        }

        @Override
        public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
            super.clearView(recyclerView, viewHolder);
            // 强制复位
            if (viewHolder instanceof ExerciseRecyclerAdapter.ViewHolder) {
                ExerciseRecyclerAdapter.ViewHolder holder = (ExerciseRecyclerAdapter.ViewHolder) viewHolder;
                holder.cardNormal.setTranslationX(0f);
                holder.cardDelete.setTranslationX(0f);
                holder.cardCompleted.setTranslationX(0f);
            }
        }
    });

    itemTouchHelper.attachToRecyclerView(mainRecyclerView);
}

    // =========================================================
    //  UI 辅助方法 (图表、重命名、导航)
    // =========================================================

    private void showRenameDialog(ExerciseEntity exercise) {
        android.widget.EditText input = new android.widget.EditText(this);
        input.setText(exercise.name);
        // 光标置于末尾
        input.setSelection(exercise.name.length());

        new AlertDialog.Builder(this)
                .setTitle("重命名动作")
                .setView(input)
                .setPositiveButton("确定", (d, w) -> {
                    String newName = input.getText().toString().trim();
                    if (!newName.isEmpty()) {
                        String oldName = exercise.name;
                        exercise.name = newName;
                        executorService.execute(() -> {
                            workoutDao.update(exercise);
                            // 智能重命名：同时更新模板和历史记录
                            workoutDao.smartRename(oldName, newName);
                            loadDataFromDatabase();
                        });
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showPlanSelectionDialog() {
        // 跳转到计划列表页
        startActivity(new Intent(this, PlanListActivity.class));
    }

    private void showDaySelectionDialog() {
        // 弹窗切换当前计划内的训练日
        executorService.execute(() -> {
            PlanEntity activePlan = workoutDao.getActivePlan();
            if (activePlan == null) {
                runOnUiThread(() -> Toast.makeText(this, "当前没有激活的计划", Toast.LENGTH_SHORT).show());
                return;
            }
            List<String> days = workoutDao.getPlanDays(activePlan.planId);
            if (days.isEmpty()) return;

            String[] dayArray = days.toArray(new String[0]);
            runOnUiThread(() -> {
                new AlertDialog.Builder(this)
                        .setTitle("切换训练日")
                        .setItems(dayArray, (dialog, which) -> {
                            // 保存选择
                            SharedPreferences prefs = getSharedPreferences("fitness_prefs", MODE_PRIVATE);
                            prefs.edit().putInt("PLAN_INDEX", which).apply();

                            // 此时需要清空 Exercise 表，加载新的一天
                            // 为了用户体验，先弹个确认或者直接切换
                            switchDayAndReload(activePlan, dayArray[which]);
                        })
                        .show();
            });
        });
    }

    private void switchDayAndReload(PlanEntity plan, String dayName) {
        executorService.execute(() -> {
            // 清空当前列表
            workoutDao.clearAllExercises();
            // 加载新一天的模板
            List<TemplateEntity> templates = workoutDao.getTemplatesByPlanAndDay(plan.planId, dayName);
            for (TemplateEntity t : templates) {
                ExerciseEntity e = new ExerciseEntity(t.exerciseName, t.defaultWeight, t.defaultSets, t.defaultReps, false);
                e.sortOrder = t.sortOrder;
                workoutDao.insert(e);
            }
            loadDataFromDatabase();
        });
    }

    // 加载折线图数据
    private void loadChartData(LineChart chart, String exerciseName) {
        executorService.execute(() -> {
            List<HistoryEntity> historyList = workoutDao.getHistoryByName(exerciseName);

            // 按时间排序
            Collections.sort(historyList, (h1, h2) -> Long.compare(h1.date, h2.date));

            // 只取最近 10 次记录，避免图表拥挤
            int start = Math.max(0, historyList.size() - 10);
            List<HistoryEntity> recentList = historyList.subList(start, historyList.size());

            if (recentList.isEmpty()) {
                runOnUiThread(() -> {
                    chart.setNoDataText("暂无历史数据");
                    chart.invalidate();
                });
                return;
            }

            List<Entry> entries = new ArrayList<>();
            List<String> labels = new ArrayList<>();
            SimpleDateFormat sdf = new SimpleDateFormat("MM-dd", Locale.getDefault());

            for (int i = 0; i < recentList.size(); i++) {
                HistoryEntity h = recentList.get(i);
                // X轴用索引 i
                entries.add(new Entry(i, (float) h.weight));
                labels.add(sdf.format(new Date(h.date)));
            }

            runOnUiThread(() -> {
                setupChart(chart, entries, labels);
            });
        });
    }

    private void setupChart(LineChart chart, List<Entry> entries, List<String> labels) {
        LineDataSet dataSet = new LineDataSet(entries, "重量趋势");
        dataSet.setColor(Color.parseColor("#4DB6AC")); // 蓝绿色
        dataSet.setLineWidth(2f);
        dataSet.setCircleColor(Color.parseColor("#4DB6AC"));
        dataSet.setCircleRadius(4f);
        dataSet.setDrawValues(true);
        dataSet.setValueTextSize(10f);
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER); // 平滑曲线

        LineData lineData = new LineData(dataSet);
        chart.setData(lineData);
        chart.setDescription(null);
        chart.getLegend().setEnabled(false); // 隐藏图例

        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));
        xAxis.setDrawGridLines(false);

        chart.getAxisRight().setEnabled(false); // 隐藏右Y轴
        chart.getAxisLeft().setDrawGridLines(true);
        chart.animateX(500);
        chart.invalidate(); // 刷新
    }
}