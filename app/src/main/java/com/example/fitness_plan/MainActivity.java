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
                });

                mainRecyclerView.setAdapter(adapter);
                setupItemTouchHelper();
            });
        });
    }

    // =========================================================
    //  逻辑 C：重命名 (BottomSheetDialog + 亮度调节)
    // =========================================================
    private void showRenameDialog(ExerciseEntity exercise) {
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(this, com.google.android.material.R.style.Theme_Design_BottomSheetDialog);

        View view = getLayoutInflater().inflate(R.layout.dialog_bottom_sheet_rename, null);
        bottomSheetDialog.setContentView(view);

        // 【新增】调节背景变暗程度 (0.0 - 1.0)
        // 0.3f 比较柔和，不会让眼睛不适
        if (bottomSheetDialog.getWindow() != null) {
            bottomSheetDialog.getWindow().setDimAmount(0.0f);
        }

        android.widget.EditText etInput = view.findViewById(R.id.etRenameInput);
        Button btnCancel = view.findViewById(R.id.btnCancel);
        Button btnConfirm = view.findViewById(R.id.btnConfirm);

        boolean isGhost = exercise.name.equals("新动作");
        final boolean[] isConfirmed = {false};

        if (isGhost) {
            etInput.setText("");
        } else {
            etInput.setText(exercise.name);
            etInput.setSelection(exercise.name.length());
        }

        btnCancel.setOnClickListener(v -> bottomSheetDialog.dismiss());

        btnConfirm.setOnClickListener(v -> {
            String newName = etInput.getText().toString().trim();
            if (!newName.isEmpty()) {
                isConfirmed[0] = true;
                String oldName = exercise.name;
                exercise.name = newName;

                executorService.execute(() -> {
                    workoutDao.update(exercise);

                    if (isGhost && defaultAddToPlan) {
                        PlanEntity activePlan = workoutDao.getActivePlan();
                        if (activePlan != null && currentPlanName != null) {
                            TemplateEntity template = new TemplateEntity();
                            template.planId = activePlan.planId;
                            template.dayName = currentPlanName;
                            template.exerciseName = newName;
                            template.defaultSets = 4;
                            template.defaultReps = 12;
                            template.sortOrder = exercise.sortOrder;
                            workoutDao.insertTemplate(template);
                        }
                    }
                    else {
                        boolean isPermanent = exercise.color == null || exercise.color.equalsIgnoreCase("#FFFFFF");
                        if (isPermanent) {
                            workoutDao.smartRename(oldName, newName);
                        }
                    }
                    loadDataFromDatabase();
                });
                bottomSheetDialog.dismiss();
            } else {
                bottomSheetDialog.dismiss();
            }
        });

        bottomSheetDialog.setOnDismissListener(dialog -> {
            if (!isConfirmed[0] && isGhost) {
                deleteTempCard(exercise);
            }
        });

        bottomSheetDialog.setOnShowListener(dialog -> {
            etInput.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(etInput, InputMethodManager.SHOW_IMPLICIT);
            }
        });

        bottomSheetDialog.show();
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
                executorService.execute(this::saveAndFinish);
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
                    saveAndFinish();
                });
                sheet.dismiss();
            });

            // 选项2: 仅保存已完成 (严格模式) - 这里我们把原来的"放弃"改个好听的名字
            btnSecondary.setText("跳过这些，仅存档已完成");
            btnSecondary.setTextColor(Color.parseColor("#666666"));
            btnSecondary.setOnClickListener(v -> {
                executorService.execute(this::saveAndFinish);
                sheet.dismiss();
            });
        }

        sheet.show();
    }

    private void saveAndFinish() {
        PlanEntity activePlan = workoutDao.getActivePlan();
        String planName = (activePlan != null) ? activePlan.planName : "自由训练";
        String dayTitle = (currentPlanName != null) ? currentPlanName : "临时训练";
        String fullTitle = planName + " - " + dayTitle;
        long currentTime = System.currentTimeMillis();
        for (ExerciseEntity ex : workoutPlan) {
            if (ex.isCompleted) {
                HistoryEntity history = new HistoryEntity(currentTime, ex.name, ex.weight, ex.sets, ex.reps, isLbsMode);
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
                    e.color = "#FFFFFF";
                    workoutDao.insert(e);
                }
            }
        }
        loadDataFromDatabase();
        runOnUiThread(() -> Toast.makeText(MainActivity.this, "训练完成！已存档", Toast.LENGTH_SHORT).show());
    }

    private void setupItemTouchHelper() {
        itemTouchHelper = new ItemTouchHelper(new ItemTouchHelper.Callback() {
            @Override public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) { return makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT); }
            @Override public void onSelectedChanged(@androidx.annotation.Nullable RecyclerView.ViewHolder viewHolder, int actionState) { super.onSelectedChanged(viewHolder, actionState); if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder instanceof ExerciseRecyclerAdapter.ItemViewHolder) { ExerciseRecyclerAdapter.ItemViewHolder holder = (ExerciseRecyclerAdapter.ItemViewHolder) viewHolder;
                clearGhostCard();
                holder.cardNormal.setCardElevation(0f); holder.cardNormal.setStrokeColor(Color.parseColor("#4DB6AC")); holder.cardNormal.setStrokeWidth(12); holder.itemView.setScaleX(1.03f); holder.itemView.setScaleY(1.03f); } }
            @Override public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) { super.clearView(recyclerView, viewHolder); if (viewHolder instanceof ExerciseRecyclerAdapter.ItemViewHolder) { ExerciseRecyclerAdapter.ItemViewHolder holder = (ExerciseRecyclerAdapter.ItemViewHolder) viewHolder; holder.cardNormal.setTranslationX(0f); holder.cardDelete.setTranslationX(0f); holder.cardCompleted.setTranslationX(0f); holder.cardNormal.setCardElevation(0f); holder.cardNormal.setStrokeColor(getResources().getColor(R.color.flat_divider)); holder.cardNormal.setStrokeWidth(1); holder.itemView.setScaleX(1.0f); holder.itemView.setScaleY(1.0f); } }
            @Override public float getSwipeThreshold(@NonNull RecyclerView.ViewHolder viewHolder) { return 0.3f; }
            @Override public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder source, @NonNull RecyclerView.ViewHolder target) { if (adapter != null) { adapter.onItemMove(source.getAdapterPosition(), target.getAdapterPosition()); return true; } return false; }
            @Override public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) { int position = viewHolder.getAdapterPosition(); ExerciseEntity item = adapter.getItem(position); if (item == null) return; if (direction == ItemTouchHelper.LEFT) item.isDeleteConfirmMode = true; else if (direction == ItemTouchHelper.RIGHT) { if (item.isDeleteConfirmMode) item.isDeleteConfirmMode = false; else { item.isCompleted = !item.isCompleted; executorService.execute(() -> workoutDao.update(item)); } } adapter.notifyItemChanged(position); }
            @Override public boolean isLongPressDragEnabled() { return false; }
            @Override public void onChildDraw(@NonNull android.graphics.Canvas c, @NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) { if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) { if (viewHolder instanceof ExerciseRecyclerAdapter.ItemViewHolder) { ExerciseRecyclerAdapter.ItemViewHolder holder = (ExerciseRecyclerAdapter.ItemViewHolder) viewHolder; float width = holder.itemView.getWidth(); holder.cardNormal.setTranslationX(dX); if (dX < 0) { holder.cardDelete.setVisibility(View.VISIBLE); holder.cardCompleted.setVisibility(View.INVISIBLE); holder.cardDelete.setTranslationX(width + dX); } else if (dX > 0) { holder.cardCompleted.setVisibility(View.VISIBLE); holder.cardDelete.setVisibility(View.INVISIBLE); holder.cardCompleted.setTranslationX(dX - width); } return; } } super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive); }
        });
        itemTouchHelper.attachToRecyclerView(mainRecyclerView);
    }

    private void loadChartData(LineChart chart, String exerciseName) { executorService.execute(() -> { List<HistoryEntity> historyList = workoutDao.getHistoryByName(exerciseName); Collections.sort(historyList, (h1, h2) -> Long.compare(h1.date, h2.date)); int start = Math.max(0, historyList.size() - 10); List<HistoryEntity> recentList = historyList.subList(start, historyList.size()); if (recentList.isEmpty()) { runOnUiThread(() -> { chart.setNoDataText("暂无历史数据"); chart.invalidate(); }); return; } List<Entry> entries = new ArrayList<>(); List<String> labels = new ArrayList<>(); SimpleDateFormat sdf = new SimpleDateFormat("MM-dd", Locale.getDefault()); for (int i = 0; i < recentList.size(); i++) { HistoryEntity h = recentList.get(i); entries.add(new Entry(i, (float) h.weight)); labels.add(sdf.format(new Date(h.date))); } runOnUiThread(() -> setupChart(chart, entries, labels)); }); }

    private void setupChart(LineChart chart, List<Entry> entries, List<String> labels) { LineDataSet dataSet = new LineDataSet(entries, "重量趋势"); dataSet.setColor(Color.parseColor("#4DB6AC")); dataSet.setLineWidth(2f); dataSet.setCircleColor(Color.parseColor("#4DB6AC")); dataSet.setCircleRadius(4f); dataSet.setDrawValues(true); dataSet.setValueTextSize(10f); dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER); LineData lineData = new LineData(dataSet); chart.setData(lineData); chart.setDescription(null); chart.getLegend().setEnabled(false); XAxis xAxis = chart.getXAxis(); xAxis.setPosition(XAxis.XAxisPosition.BOTTOM); xAxis.setGranularity(1f); xAxis.setValueFormatter(new IndexAxisValueFormatter(labels)); xAxis.setDrawGridLines(false); chart.getAxisRight().setEnabled(false); chart.getAxisLeft().setDrawGridLines(true); chart.animateX(500); chart.invalidate(); }
}