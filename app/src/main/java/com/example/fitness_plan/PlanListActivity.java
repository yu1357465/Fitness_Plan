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
            return false;
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
                        showDeleteDayDialog(planId, dayName); // 复用你之前写的删除逻辑
                    }

                    @Override
                    public void onDeletePlan(PlanEntity plan) {
                        showDeletePlanDialog(plan); // 复用你之前写的删除逻辑
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
                // finish(); // 如果你想激活后不关闭，就注释掉这行；想关闭就留着
            });
        });
    }

    // ================= 业务逻辑：修改天名 =================
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
                            loadData(); // 刷新
                        });
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ================= 业务逻辑：复制到... =================
    private void showCopyDayDialog(int sourcePlanId, String dayName) {
        // 1. 过滤出除了当前计划以外的其他计划
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

        // 2. 显示选择列表
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
            // 1. 获取源模板动作
            List<TemplateEntity> sourceTemplates = workoutDao.getTemplatesByPlanAndDay(sourcePlanId, dayName);

            // 2. 插入到目标计划 (DayName 保持不变，或者你可以让用户重命名)
            for (TemplateEntity temp : sourceTemplates) {
                TemplateEntity newTemp = new TemplateEntity(
                        targetPlan.planId, // 目标 PlanID
                        dayName,           // 保持原名
                        temp.dayIndex,     // 保持原序
                        temp.exerciseName,
                        temp.defaultWeight,
                        temp.defaultSets,
                        temp.defaultReps
                );
                workoutDao.insertTemplate(newTemp);
            }

            runOnUiThread(() ->
                    Toast.makeText(this, "已复制到 " + targetPlan.planName, Toast.LENGTH_SHORT).show()
            );
        });
    }

    // ================= 业务逻辑：添加新计划 (UI部分) =================
    private void showAddPlanDialog() {
        EditText input = new EditText(this);
        input.setHint("计划名称 (如: 五分化)");
        // 【新增】让输入框默认全选，方便修改
        input.setSelectAllOnFocus(true);

        new AlertDialog.Builder(this)
                .setTitle("创建新计划")
                .setView(input)
                .setPositiveButton("创建", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty()) {
                        createNewPlan(name);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ================= 业务逻辑：执行创建 (逻辑部分) =================
    private void createNewPlan(String planName) {
        executorService.execute(() -> {
            // 1. 插入新计划
            PlanEntity plan = new PlanEntity(planName, false);
            long newId = workoutDao.insertPlan(plan);

            // 2. 【核心优化】使用“智能默认值”代替全是 0 的数据
            TemplateEntity placeholder = new TemplateEntity(
                    (int)newId,
                    "Day 1",
                    0,
                    "点击修改动作名称", // 提示语更明确
                    20.0,  // 默认给个空杆重量 (20kg)
                    3,     // 默认 3 组
                    12     // 默认 12 次 (常用训练容量)
            );
            workoutDao.insertTemplate(placeholder);

            // 3. 刷新界面
            loadData();
        });
    }

    // 删除计划组
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
                        // 先删子项 (模板)，再删父项 (计划)
                        workoutDao.deleteTemplatesByPlanId(plan.planId);
                        workoutDao.deletePlan(plan);
                        loadData(); // 刷新
                    });
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // 删除某一天
    private void showDeleteDayDialog(int planId, String dayName) {
        new AlertDialog.Builder(this)
                .setTitle("删除日子")
                .setMessage("确定删除 \"" + dayName + "\" 吗？")
                .setPositiveButton("删除", (dialog, which) -> {
                    executorService.execute(() -> {
                        workoutDao.deleteTemplatesByPlanAndDay(planId, dayName);
                        loadData(); // 刷新
                    });
                })
                .setNegativeButton("取消", null)
                .show();
    }

}