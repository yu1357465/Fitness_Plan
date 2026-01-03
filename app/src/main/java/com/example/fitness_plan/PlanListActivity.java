package com.example.fitness_plan;

import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ExpandableListView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.fitness_plan.data.AppDatabase;
import com.example.fitness_plan.data.ExerciseEntity;
import com.example.fitness_plan.data.PlanEntity;
import com.example.fitness_plan.data.TemplateEntity;
import com.example.fitness_plan.data.WorkoutDao;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PlanListActivity extends AppCompatActivity {

    private WorkoutDao workoutDao;
    private ExpandableListView expandableListView;
    private PlanExpandableAdapter adapter;

    // 数据源
    private final List<PlanEntity> planList = new ArrayList<>();
    private final Map<Integer, List<String>> planDaysMap = new HashMap<>();

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_plan_list);

        // ============ 【新增】沉浸式状态栏逻辑 Start ============
        // 确保顶部状态栏透明，且图标为深色（适配浅色背景）
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
            getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        }
        // ============ 【新增】沉浸式状态栏逻辑 End ============

        // ============ 【新增】返回按钮逻辑 Start ============
        // 必须绑定 XML 中的返回按钮，否则用户点不回去
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        // ============ 【新增】返回按钮逻辑 End ============

        workoutDao = AppDatabase.getDatabase(this).workoutDao();
        expandableListView = findViewById(R.id.planExpandableListView);

        FloatingActionButton fab = findViewById(R.id.fabAddPlan);
        fab.setOnClickListener(v -> showAddPlanDialog());

        // 只保留点击父级 (展开/折叠) 的逻辑，长按逻辑删掉
        expandableListView.setOnGroupClickListener((parent, v, groupPosition, id) -> {
            PlanEntity plan = planList.get(groupPosition);
            if (!plan.isActive) {
                activatePlan(plan);
            }
            return false; // 返回 false 允许系统继续处理（即展开/折叠）
        });

        loadData();
    }

    private void loadData() {
        executorService.execute(() -> {
            // 1. 获取所有计划
            List<PlanEntity> plans = workoutDao.getAllPlans();

            // 2. 获取每个计划下的日子
            planDaysMap.clear();
            int activeGroupIndex = -1;

            for (int i = 0; i < plans.size(); i++) {
                PlanEntity plan = plans.get(i);
                List<String> days = workoutDao.getDayNamesByPlanId(plan.planId);
                planDaysMap.put(plan.planId, days);

                if (plan.isActive) {
                    activeGroupIndex = i;
                }
            }

            // 3. 更新 UI
            int finalActiveGroupIndex = activeGroupIndex;
            runOnUiThread(() -> {
                planList.clear();
                planList.addAll(plans);

                adapter = new PlanExpandableAdapter(this, planList, planDaysMap, new PlanExpandableAdapter.OnItemActionListener() {
                    @Override
                    public void onEditDay(int planId, String oldName) {
                        showEditDayDialog(planId, oldName);
                    }

                    @Override
                    public void onCopyDay(int sourcePlanId, String dayName) {
                        showCopyDayDialog(sourcePlanId, dayName);
                    }

                    @Override
                    public void onActivatePlan(PlanEntity plan) {
                        activatePlan(plan);
                    }

                    @Override
                    public void onDeleteDay(int planId, String dayName) {
                        showDeleteDayDialog(planId, dayName);
                    }

                    @Override
                    public void onDeletePlan(PlanEntity plan) {
                        showDeletePlanDialog(plan);
                    }

                });
                expandableListView.setAdapter(adapter);

                // 4. 默认展开当前计划，折叠其他
                if (finalActiveGroupIndex != -1) {
                    expandableListView.expandGroup(finalActiveGroupIndex);
                }
            });
        });
    }

    // ================= 业务逻辑：激活计划 =================
    private void activatePlan(PlanEntity selectedPlan) {
        executorService.execute(() -> {
            workoutDao.deactivateAllPlans();
            workoutDao.activatePlan(selectedPlan.planId);

            // 重置状态
            getSharedPreferences("fitness_prefs", MODE_PRIVATE).edit().putInt("PLAN_INDEX", 0).apply();
            workoutDao.clearCurrentPlan();

            // 加载第一天
            List<String> days = workoutDao.getDayNamesByPlanId(selectedPlan.planId);
            if (!days.isEmpty()) {
                String firstDay = days.get(0);
                List<TemplateEntity> templates = workoutDao.getTemplatesByPlanAndDay(selectedPlan.planId, firstDay);
                for (TemplateEntity temp : templates) {
                    ExerciseEntity ex = new ExerciseEntity(temp.exerciseName, temp.defaultWeight, temp.defaultReps, temp.defaultSets, false);
                    workoutDao.insert(ex);
                }
            }

            runOnUiThread(() -> {
                Toast.makeText(this, "已启用: " + selectedPlan.planName, Toast.LENGTH_SHORT).show();
                loadData(); // 刷新界面显示绿色状态
            });
        });
    }

    // ... (以下 Dialog 逻辑与您原代码完全一致，无需改动，此处省略以节省篇幅) ...
    // showEditDayDialog, showCopyDayDialog, copyDayToPlan,
    // showAddPlanDialog, createNewPlan, showDeletePlanDialog, showDeleteDayDialog
    // 请保留您原有的这些方法实现

    // 为了完整性，这里补充一下 showEditDayDialog，防止你复制漏了
    private void showEditDayDialog(int planId, String oldName) {
        EditText input = new EditText(this);
        input.setText(oldName);
        new AlertDialog.Builder(this)
                .setTitle("修改名称")
                .setView(input)
                .setPositiveButton("保存", (dialog, which) -> {
                    String newName = input.getText().toString().trim();
                    if (!newName.isEmpty()) {
                        executorService.execute(() -> {
                            workoutDao.updateDayName(planId, oldName, newName);
                            loadData();
                        });
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showCopyDayDialog(int sourcePlanId, String dayName) {
        // ... (保持原样)
        List<PlanEntity> targetPlans = new ArrayList<>();
        List<String> targetPlanNames = new ArrayList<>();
        for (PlanEntity plan : planList) {
            if (plan.planId != sourcePlanId) {
                targetPlans.add(plan);
                targetPlanNames.add(plan.planName);
            }
        }
        if (targetPlans.isEmpty()) {
            Toast.makeText(this, "没有其他计划可复制", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("复制 " + dayName + " 到...")
                .setItems(targetPlanNames.toArray(new String[0]), (dialog, which) -> {
                    PlanEntity targetPlan = targetPlans.get(which);
                    copyDayToPlan(sourcePlanId, dayName, targetPlan);
                })
                .show();
    }

    private void copyDayToPlan(int sourcePlanId, String dayName, PlanEntity targetPlan) {
        executorService.execute(() -> {
            List<TemplateEntity> sourceTemplates = workoutDao.getTemplatesByPlanAndDay(sourcePlanId, dayName);
            for (TemplateEntity temp : sourceTemplates) {
                TemplateEntity newTemp = new TemplateEntity(
                        targetPlan.planId, dayName, temp.dayIndex,
                        temp.exerciseName, temp.defaultWeight, temp.defaultSets, temp.defaultReps
                );
                workoutDao.insertTemplate(newTemp);
            }
            runOnUiThread(() -> Toast.makeText(this, "已复制到 " + targetPlan.planName, Toast.LENGTH_SHORT).show());
        });
    }

    private void showAddPlanDialog() {
        EditText input = new EditText(this);
        input.setHint("计划名称 (如: 五分化)");
        input.setSelectAllOnFocus(true);
        new AlertDialog.Builder(this)
                .setTitle("创建新计划")
                .setView(input)
                .setPositiveButton("创建", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty()) createNewPlan(name);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void createNewPlan(String planName) {
        executorService.execute(() -> {
            PlanEntity plan = new PlanEntity(planName, false);
            long newId = workoutDao.insertPlan(plan);
            TemplateEntity placeholder = new TemplateEntity(
                    (int)newId, "Day 1", 0, "点击修改动作名称", 20.0, 3, 12
            );
            workoutDao.insertTemplate(placeholder);
            loadData();
        });
    }

    private void showDeletePlanDialog(PlanEntity plan) {
        if (plan.isActive) {
            Toast.makeText(this, "无法删除当前正在使用的计划，请先切换其他计划", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("删除计划")
                .setMessage("确定删除 \"" + plan.planName + "\" 及其所有内容吗？")
                .setPositiveButton("删除", (dialog, which) -> {
                    executorService.execute(() -> {
                        workoutDao.deleteTemplatesByPlanId(plan.planId);
                        workoutDao.deletePlan(plan);
                        loadData();
                    });
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showDeleteDayDialog(int planId, String dayName) {
        new AlertDialog.Builder(this)
                .setTitle("删除日子")
                .setMessage("确定删除 \"" + dayName + "\" 吗？")
                .setPositiveButton("删除", (dialog, which) -> {
                    executorService.execute(() -> {
                        workoutDao.deleteTemplatesByPlanAndDay(planId, dayName);
                        loadData();
                    });
                })
                .setNegativeButton("取消", null)
                .show();
    }
}