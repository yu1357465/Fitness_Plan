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

import androidx.appcompat.widget.PopupMenu; // 关键组件
import android.view.MenuItem;
import android.view.Menu;

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

        loadDataFromDatabase();

        findViewById(R.id.btnSettings).setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        findViewById(R.id.btnHistory).setOnClickListener(v -> startActivity(new Intent(this, HistoryActivity.class)));

        // 标题点击 -> 弹出下拉菜单 (传入 v 作为锚点)
        findViewById(R.id.planSelectionContainer).setOnClickListener(v -> showPlanMenu(v));
        findViewById(R.id.daySelectionContainer).setOnClickListener(v -> showDayMenu(v));

        findViewById(R.id.fabAdd).setOnClickListener(v -> showSmartAddDialog());
        btnFinishWorkout.setOnClickListener(v -> finishAndSwitchCycle());
    }

    @Override
    protected void onResume() {
        super.onResume();
        SharedPreferences prefs = getSharedPreferences("fitness_prefs", MODE_PRIVATE);
        boolean currentIsLbs = prefs.getBoolean("DEFAULT_IS_LBS", false);
        if (currentIsLbs != lastIsLbs) {
            lastIsLbs = currentIsLbs;
            isLbsMode = currentIsLbs;
            loadDataFromDatabase();
        }
    }

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
            Collections.sort(workoutPlan, (o1, o2) -> Integer.compare(o1.sortOrder, o2.sortOrder));

            runOnUiThread(() -> {
                if (tvPageTitle != null) tvPageTitle.setText(titleForUI);
                if (tvActivePlanName != null) tvActivePlanName.setText(planNameForUI);

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
                    public void onItemLongClick(ExerciseEntity exercise) {}
                    @Override
                    public void onStartDrag(RecyclerView.ViewHolder holder) {
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
                    public void onRename(ExerciseEntity exercise) { showRenameDialog(exercise); }
                    @Override
                    public void onDelete(ExerciseEntity exercise) { handleSmartDelete(exercise); }

                    // 【关键实现】点击图钉
                    @Override
                    public void onTogglePin(ExerciseEntity exercise) { handleTogglePin(exercise); }
                });

                mainRecyclerView.setAdapter(adapter);
                setupItemTouchHelper();
            });
        });
    }

    // =========================================================
    //  逻辑 A：极简添加动作 (回归丝滑，默认是临时/淡黄色)
    // =========================================================
    private void showSmartAddDialog() {
        android.widget.EditText input = new android.widget.EditText(this);
        input.setHint("输入动作名称 (如: 侧平举)");

        // 不需要复选框了，直接弹输入框
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("添加动作")
                .setView(input)
                .setPositiveButton("添加", (d, which) -> {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty()) {
                        performAddExercise(name);
                    }
                })
                .setNegativeButton("取消", null)
                .create();

        // 自动弹出键盘
        dialog.setOnShowListener(d -> {
            input.requestFocus();
            // 加点 Margin 让输入框好看点
            if (input.getLayoutParams() instanceof android.view.ViewGroup.MarginLayoutParams) {
                android.view.ViewGroup.MarginLayoutParams params = (android.view.ViewGroup.MarginLayoutParams) input.getLayoutParams();
                params.setMargins(60, 0, 60, 0);
                input.requestLayout();
            }
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        });
        dialog.show();
    }

    private void performAddExercise(String name) {
        executorService.execute(() -> {
            int sortOrder = workoutDao.getAllExercises().size();

            ExerciseEntity newExercise = new ExerciseEntity(name, 0, 0, 0, false);
            newExercise.sortOrder = sortOrder;
            newExercise.isLbs = isLbsMode;
            // 【默认颜色】淡黄色 = 临时
            newExercise.color = "#FFF9C4";

            workoutDao.insert(newExercise);

            loadDataFromDatabase();
            runOnUiThread(() -> Toast.makeText(MainActivity.this, "已添加 " + name, Toast.LENGTH_SHORT).show());
        });
    }

    // =========================================================
    //  逻辑 B：处理图钉点击 (加入/移除计划)
    // =========================================================
    private void handleTogglePin(ExerciseEntity exercise) {
        executorService.execute(() -> {
            PlanEntity activePlan = workoutDao.getActivePlan();
            if (activePlan == null || currentPlanName == null) {
                runOnUiThread(() -> Toast.makeText(this, "当前无激活计划，无法固定", Toast.LENGTH_SHORT).show());
                return;
            }

            // 判断当前是不是“计划内动作” (白色)
            boolean isPermanent = exercise.color != null && exercise.color.equalsIgnoreCase("#FFFFFF");

            if (isPermanent) {
                // >>> 已经在计划里 -> 变为临时 (移除计划)
                exercise.color = "#FFF9C4"; // 变黄
                workoutDao.update(exercise);

                // 从模板中删除
                workoutDao.deleteTemplateByName(activePlan.planId, currentPlanName, exercise.name);

                runOnUiThread(() -> Toast.makeText(this, "已从计划中移除 (变更为临时)", Toast.LENGTH_SHORT).show());

            } else {
                // >>> 是临时动作 -> 加入计划
                exercise.color = "#FFFFFF"; // 变白
                workoutDao.update(exercise);

                // 加入到模板
                List<TemplateEntity> existing = workoutDao.getTemplatesByPlanAndDay(activePlan.planId, currentPlanName);
                boolean exists = false;
                for (TemplateEntity t : existing) {
                    if (t.exerciseName.equals(exercise.name)) {
                        exists = true; break;
                    }
                }

                if (!exists) {
                    TemplateEntity template = new TemplateEntity();
                    template.planId = activePlan.planId;
                    template.dayName = currentPlanName;
                    template.exerciseName = exercise.name;
                    template.defaultSets = exercise.sets; // 继承当前组数
                    template.defaultReps = exercise.reps; // 继承当前次数
                    template.defaultWeight = exercise.weight; // 继承当前重量
                    template.sortOrder = existing.size();
                    workoutDao.insertTemplate(template);
                }

                runOnUiThread(() -> Toast.makeText(this, "已加入计划模板", Toast.LENGTH_SHORT).show());
            }

            // 刷新列表
            loadDataFromDatabase();
        });
    }

    // =========================================================
    //  逻辑 C：智能删除动作 (保持不变)
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
                        });
                    })
                    .setAnchorView(findViewById(R.id.fabAdd))
                    .show());
        });
    }

    // =========================================================
    //  逻辑 D：完成训练 (保持不变)
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

        if (activePlan != null) {
            List<String> days = workoutDao.getPlanDays(activePlan.planId);
            if (!days.isEmpty()) {
                SharedPreferences prefs = getSharedPreferences("fitness_prefs", MODE_PRIVATE);
                int currentIndex = prefs.getInt("PLAN_INDEX", 0);
                int nextIndex = (currentIndex + 1) % days.size();
                prefs.edit().putInt("PLAN_INDEX", nextIndex).apply();
            }
        }

        workoutDao.clearAllExercises();

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
                    // 【核心】从模板加载的动作，颜色设为白色
                    e.color = "#FFFFFF";
                    workoutDao.insert(e);
                }
            }
        }

        loadDataFromDatabase();
        runOnUiThread(() -> Toast.makeText(MainActivity.this, "训练完成！已存档", Toast.LENGTH_SHORT).show());
    }

    // =========================================================
    //  UI 辅助方法：设置侧滑/拖拽 (去阴影，加粗边框版)
    // =========================================================
    private void setupItemTouchHelper() {
        itemTouchHelper = new ItemTouchHelper(new ItemTouchHelper.Callback() {
            @Override
            public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                return makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN,
                        ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT);
            }

            // 【核心修改】当状态改变（开始拖拽）时触发
            @Override
            public void onSelectedChanged(@androidx.annotation.Nullable RecyclerView.ViewHolder viewHolder, int actionState) {
                super.onSelectedChanged(viewHolder, actionState);
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    if (viewHolder instanceof ExerciseRecyclerAdapter.ViewHolder) {
                        ExerciseRecyclerAdapter.ViewHolder holder = (ExerciseRecyclerAdapter.ViewHolder) viewHolder;

                        // 1. 【移除】不设置阴影，避免矩形黑影问题
                        holder.cardNormal.setCardElevation(0f);

                        // 2. 【加粗】边框变色 + 显著变粗 (4 -> 12)
                        holder.cardNormal.setStrokeColor(Color.parseColor("#4DB6AC"));
                        holder.cardNormal.setStrokeWidth(12);

                        // 3. 缩放保持不变，提供浮起感
                        holder.itemView.setScaleX(1.03f);
                        holder.itemView.setScaleY(1.03f);
                    }
                }
            }

            // 【核心修改】当拖拽结束复位时触发
            @Override
            public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);
                if (viewHolder instanceof ExerciseRecyclerAdapter.ViewHolder) {
                    ExerciseRecyclerAdapter.ViewHolder holder = (ExerciseRecyclerAdapter.ViewHolder) viewHolder;
                    // 复位位置
                    holder.cardNormal.setTranslationX(0f);
                    holder.cardDelete.setTranslationX(0f);
                    holder.cardCompleted.setTranslationX(0f);

                    // 复位样式
                    holder.cardNormal.setCardElevation(0f);
                    // 恢复灰色细边框
                    holder.cardNormal.setStrokeColor(getResources().getColor(R.color.flat_divider));
                    holder.cardNormal.setStrokeWidth(1);

                    holder.itemView.setScaleX(1.0f);
                    holder.itemView.setScaleY(1.0f);
                }
            }

            @Override
            public float getSwipeThreshold(@NonNull RecyclerView.ViewHolder viewHolder) { return 0.3f; }

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
                if (direction == ItemTouchHelper.LEFT) item.isDeleteConfirmMode = true;
                else if (direction == ItemTouchHelper.RIGHT) {
                    if (item.isDeleteConfirmMode) item.isDeleteConfirmMode = false;
                    else {
                        item.isCompleted = !item.isCompleted;
                        executorService.execute(() -> workoutDao.update(item));
                    }
                }
                adapter.notifyItemChanged(position);
            }

            @Override
            public boolean isLongPressDragEnabled() { return false; }

            @Override
            public void onChildDraw(@NonNull android.graphics.Canvas c, @NonNull RecyclerView recyclerView,
                                    @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY,
                                    int actionState, boolean isCurrentlyActive) {
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                    if (viewHolder instanceof ExerciseRecyclerAdapter.ViewHolder) {
                        ExerciseRecyclerAdapter.ViewHolder holder = (ExerciseRecyclerAdapter.ViewHolder) viewHolder;
                        float width = holder.itemView.getWidth();
                        holder.cardNormal.setTranslationX(dX);
                        if (dX < 0) {
                            holder.cardDelete.setVisibility(View.VISIBLE);
                            holder.cardCompleted.setVisibility(View.INVISIBLE);
                            holder.cardDelete.setTranslationX(width + dX);
                        } else if (dX > 0) {
                            holder.cardCompleted.setVisibility(View.VISIBLE);
                            holder.cardDelete.setVisibility(View.INVISIBLE);
                            holder.cardCompleted.setTranslationX(dX - width);
                        }
                        return;
                    }
                }
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
            }
        });
        itemTouchHelper.attachToRecyclerView(mainRecyclerView);
    }

    private void showRenameDialog(ExerciseEntity exercise) {
        android.widget.EditText input = new android.widget.EditText(this);
        input.setText(exercise.name);
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
                            workoutDao.smartRename(oldName, newName);
                            loadDataFromDatabase();
                        });
                    }
                })
                .setNegativeButton("取消", null).show();
    }

    // =========================================================
    //  交互优化：统一的下拉菜单 (支持选中状态 ✔)
    // =========================================================

    // 1. 显示训练日下拉菜单 (已添加打钩逻辑)
    private void showDayMenu(View anchor) {
        executorService.execute(() -> {
            PlanEntity activePlan = workoutDao.getActivePlan();
            if (activePlan == null) {
                runOnUiThread(() -> Toast.makeText(this, "当前没有激活的计划", Toast.LENGTH_SHORT).show());
                return;
            }

            // 获取所有训练日
            List<String> days = workoutDao.getPlanDays(activePlan.planId);
            if (days.isEmpty()) return;

            // 【关键】获取当前选中的索引，用于打钩
            SharedPreferences prefs = getSharedPreferences("fitness_prefs", MODE_PRIVATE);
            int currentIndex = prefs.getInt("PLAN_INDEX", 0);

            runOnUiThread(() -> {
                PopupMenu popup = new PopupMenu(this, anchor);
                Menu menu = popup.getMenu();

                // 动态添加菜单项
                for (int i = 0; i < days.size(); i++) {
                    // add(groupId, itemId, order, title)
                    MenuItem item = menu.add(0, i, i, days.get(i));

                    // 【统一设计语言】如果下标匹配，设为选中状态
                    if (i == currentIndex) {
                        item.setCheckable(true);
                        item.setChecked(true);
                    }
                }

                popup.setOnMenuItemClickListener(item -> {
                    int index = item.getItemId();

                    // 如果点击的是已经选中的，直接返回，不刷新
                    if (index == currentIndex) return true;

                    String selectedDay = days.get(index);

                    // 保存选择
                    prefs.edit().putInt("PLAN_INDEX", index).apply();

                    // 切换数据
                    switchDayAndReload(activePlan, selectedDay);
                    return true;
                });

                popup.show();
            });
        });
    }

    // 2. 显示计划下拉菜单 (保持逻辑一致)
    private void showPlanMenu(View anchor) {
        executorService.execute(() -> {
            List<PlanEntity> allPlans = workoutDao.getAllPlans();

            runOnUiThread(() -> {
                PopupMenu popup = new PopupMenu(this, anchor);
                Menu menu = popup.getMenu();

                // A. 添加所有计划
                for (PlanEntity plan : allPlans) {
                    MenuItem item = menu.add(1, plan.planId, 0, plan.planName);
                    // 【统一设计语言】如果是激活计划，打钩
                    if (plan.isActive) {
                        item.setCheckable(true);
                        item.setChecked(true);
                    }
                }

                // B. 添加管理入口
                // 使用分割线逻辑或单独的 Group 区分
                MenuItem manageItem = menu.add(2, 9999, 1, "⚙️ 管理所有计划...");

                popup.setOnMenuItemClickListener(item -> {
                    int id = item.getItemId();

                    if (id == 9999) {
                        startActivity(new Intent(this, PlanListActivity.class));
                    } else {
                        // 检查是否重复点击当前计划
                        boolean isCurrent = false;
                        for(PlanEntity p : allPlans) {
                            if(p.planId == id && p.isActive) {
                                isCurrent = true;
                                break;
                            }
                        }
                        if (isCurrent) return true;

                        performSwitchPlan(id);
                    }
                    return true;
                });

                popup.show();
            });
        });
    }

    // 辅助：执行切换计划的数据库操作
    private void performSwitchPlan(int planId) {
        executorService.execute(() -> {
            // 1. 归档当前计划状态
            workoutDao.deactivateAllPlans();

            // 2. 激活新计划
            workoutDao.activatePlan(planId);

            // 3. 重置索引 (默认从第一天开始)
            SharedPreferences prefs = getSharedPreferences("fitness_prefs", MODE_PRIVATE);
            prefs.edit().putInt("PLAN_INDEX", 0).apply();

            // 4. 加载新计划的第一天数据
            PlanEntity newPlan = workoutDao.getActivePlan();
            if (newPlan != null) {
                List<String> days = workoutDao.getPlanDays(newPlan.planId);
                if (!days.isEmpty()) {
                    // 清空当前列表
                    workoutDao.clearAllExercises();
                    // 加载新模板
                    List<TemplateEntity> templates = workoutDao.getTemplatesByPlanAndDay(newPlan.planId, days.get(0));
                    for (TemplateEntity t : templates) {
                        ExerciseEntity e = new ExerciseEntity(t.exerciseName, t.defaultWeight, t.defaultSets, t.defaultReps, false);
                        e.sortOrder = t.sortOrder;
                        e.color = "#FFFFFF";
                        workoutDao.insert(e);
                    }
                }
            }

            // 5. 刷新 UI
            loadDataFromDatabase();

            runOnUiThread(() -> Toast.makeText(this, "已切换计划", Toast.LENGTH_SHORT).show());
        });
    }

    private void switchDayAndReload(PlanEntity plan, String dayName) {
        executorService.execute(() -> {
            workoutDao.clearAllExercises();
            List<TemplateEntity> templates = workoutDao.getTemplatesByPlanAndDay(plan.planId, dayName);
            for (TemplateEntity t : templates) {
                ExerciseEntity e = new ExerciseEntity(t.exerciseName, t.defaultWeight, t.defaultSets, t.defaultReps, false);
                e.sortOrder = t.sortOrder;
                // 从计划加载的 -> 白色
                e.color = "#FFFFFF";
                workoutDao.insert(e);
            }
            loadDataFromDatabase();
        });
    }

    private void loadChartData(LineChart chart, String exerciseName) {
        executorService.execute(() -> {
            List<HistoryEntity> historyList = workoutDao.getHistoryByName(exerciseName);
            Collections.sort(historyList, (h1, h2) -> Long.compare(h1.date, h2.date));
            int start = Math.max(0, historyList.size() - 10);
            List<HistoryEntity> recentList = historyList.subList(start, historyList.size());
            if (recentList.isEmpty()) {
                runOnUiThread(() -> { chart.setNoDataText("暂无历史数据"); chart.invalidate(); });
                return;
            }
            List<Entry> entries = new ArrayList<>();
            List<String> labels = new ArrayList<>();
            SimpleDateFormat sdf = new SimpleDateFormat("MM-dd", Locale.getDefault());
            for (int i = 0; i < recentList.size(); i++) {
                HistoryEntity h = recentList.get(i);
                entries.add(new Entry(i, (float) h.weight));
                labels.add(sdf.format(new Date(h.date)));
            }
            runOnUiThread(() -> setupChart(chart, entries, labels));
        });
    }

    private void setupChart(LineChart chart, List<Entry> entries, List<String> labels) {
        LineDataSet dataSet = new LineDataSet(entries, "重量趋势");
        dataSet.setColor(Color.parseColor("#4DB6AC"));
        dataSet.setLineWidth(2f);
        dataSet.setCircleColor(Color.parseColor("#4DB6AC"));
        dataSet.setCircleRadius(4f);
        dataSet.setDrawValues(true);
        dataSet.setValueTextSize(10f);
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        LineData lineData = new LineData(dataSet);
        chart.setData(lineData);
        chart.setDescription(null);
        chart.getLegend().setEnabled(false);
        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));
        xAxis.setDrawGridLines(false);
        chart.getAxisRight().setEnabled(false);
        chart.getAxisLeft().setDrawGridLines(true);
        chart.animateX(500);
        chart.invalidate();
    }
}