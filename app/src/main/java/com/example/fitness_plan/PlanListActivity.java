package com.example.fitness_plan;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.fitness_plan.data.AppDatabase;
import com.example.fitness_plan.data.EntityNameCache;
import com.example.fitness_plan.data.ExerciseEntity;
import com.example.fitness_plan.data.PlanEntity;
import com.example.fitness_plan.data.TemplateEntity;
import com.example.fitness_plan.data.WorkoutDao;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PlanListActivity extends AppCompatActivity {

    private WorkoutDao workoutDao;
    private RecyclerView planRecyclerView;
    private PlanAdapter adapter;

    // 数据源
    private final List<PlanEntity> planList = new ArrayList<>();
    private final Map<Integer, List<String>> planDaysMap = new HashMap<>();

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_plan_list);

        // ============ 沉浸式状态栏 ============
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
            getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        }

        workoutDao = AppDatabase.getDatabase(this).workoutDao();

        FloatingActionButton fab = findViewById(R.id.fabAddPlan);
        fab.setOnClickListener(v -> showAddPlanDialog());

        planRecyclerView = findViewById(R.id.planRecyclerView);
        planRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        loadData();
    }

    private void loadData() {
        executorService.execute(() -> {
            // 1. 获取所有计划
            List<PlanEntity> plans = workoutDao.getAllPlans();

            // 2. 获取每个计划下的日子
            planDaysMap.clear();
            for (PlanEntity plan : plans) {
                List<String> days = workoutDao.getDayNamesByPlanId(plan.planId);
                planDaysMap.put(plan.planId, days);
            }

            // 3. 更新 UI
            runOnUiThread(() -> {
                planList.clear();
                planList.addAll(plans);

                adapter = new PlanAdapter(this, planList, planDaysMap, new PlanAdapter.OnPlanActionListener() {
                    @Override public void onActivatePlan(PlanEntity plan) { activatePlan(plan); }
                    @Override public void onDeletePlan(PlanEntity plan) { showDeletePlanDialog(plan); }
                    @Override public void onPlanOrderChanged() { /* Save Order Logic */ }
                    @Override public void onAddDay(PlanEntity plan) { showAddDayDialog(plan); }

                    // 【新增】计划重命名 (使用底部弹窗)
                    @Override
                    public void onRenamePlan(PlanEntity plan) {
                        showRenameSheet("修改计划名称", plan.planName, newName -> {
                            executorService.execute(() -> {
                                plan.planName = newName;
                                workoutDao.updatePlan(plan);
                                loadData();
                            });
                        });
                    }

                    // 【修改】日子重命名 (使用底部弹窗)
                    @Override
                    public void onEditDay(int planId, String oldName) {
                        showRenameSheet("修改训练日名称", oldName, newName -> {
                            executorService.execute(() -> {
                                workoutDao.updateDayName(planId, oldName, newName);
                                loadData();
                            });
                        });
                    }

                    @Override public void onCopyDay(int planId, String dayName) { showCopyDayDialog(planId, dayName); }
                    @Override public void onDeleteDay(int planId, String dayName) { showDeleteDayDialog(planId, dayName); }
                    @Override public void onDayOrderChanged(int planId) { /* Save Day Order Logic */ }
                });

                planRecyclerView.setAdapter(adapter);

                // 配置 ItemTouchHelper (外层拖拽)
                ItemTouchHelper.Callback callback = new ItemTouchHelper.Callback() {
                    @Override public int getMovementFlags(RecyclerView r, RecyclerView.ViewHolder v) {
                        return makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0);
                    }
                    @Override public boolean onMove(RecyclerView r, RecyclerView.ViewHolder src, RecyclerView.ViewHolder tgt) {
                        if (adapter != null) {
                            adapter.onItemMove(src.getAdapterPosition(), tgt.getAdapterPosition());
                            return true;
                        }
                        return false;
                    }
                    @Override public void onSwiped(RecyclerView.ViewHolder v, int d) {}
                    @Override public boolean isLongPressDragEnabled() { return false; }
                };
                ItemTouchHelper touchHelper = new ItemTouchHelper(callback);
                touchHelper.attachToRecyclerView(planRecyclerView);
                adapter.setItemTouchHelper(touchHelper);
            });
        });
    }

    // =========================================================
    //  【终极优化版】通用重命名弹窗
    //  特点：居中悬浮、背景不闪烁(无Dim)、键盘秒弹
    // =========================================================
    private void showRenameSheet(String title, String currentName, OnRenameConfirmListener listener) {
        View view = getLayoutInflater().inflate(R.layout.dialog_rename_center, null);

        android.widget.TextView tvTitle = view.findViewById(R.id.tvDialogTitle);
        EditText etInput = view.findViewById(R.id.etRenameInput);
        View btnCancel = view.findViewById(R.id.btnCancel);
        View btnConfirm = view.findViewById(R.id.btnConfirm);

        tvTitle.setText(title);
        etInput.setText(currentName);
        etInput.setSelection(currentName.length());

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(view)
                .setCancelable(true)
                .create();

        if (dialog.getWindow() != null) {
            // 1. 背景全透明 (只显示卡片圆角)
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

            // 2. 【核心】背景亮度不变 (DimAmount = 0)，避免闪烁
            dialog.getWindow().setDimAmount(0f);

            // 3. 【核心】强制弹出键盘
            // 使用 STATE_ALWAYS_VISIBLE 确保只要窗口一显示，键盘就出来
            dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        }

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnConfirm.setOnClickListener(v -> {
            String newName = etInput.getText().toString().trim();
            if (!newName.isEmpty()) {
                listener.onConfirm(newName);
                dialog.dismiss();
            }
        });

        // 监听软键盘的“完成”按钮
        etInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                btnConfirm.performClick();
                return true;
            }
            return false;
        });

        dialog.show();

        // 双重保险：显示后再次请求焦点，确保键盘弹起
        etInput.requestFocus();
    }

    interface OnRenameConfirmListener {
        void onConfirm(String newName);
    }

    // ================= 业务逻辑 =================

    private void activatePlan(PlanEntity selectedPlan) {
        executorService.execute(() -> {
            workoutDao.deactivateAllPlans();
            workoutDao.activatePlan(selectedPlan.planId);
            // 重置状态
            getSharedPreferences("fitness_prefs", MODE_PRIVATE).edit().putInt("PLAN_INDEX", 0).apply();
            workoutDao.clearCurrentPlan();
            // 加载第一天数据
            List<String> days = workoutDao.getDayNamesByPlanId(selectedPlan.planId);
            if (!days.isEmpty()) {
                String firstDay = days.get(0);
                List<TemplateEntity> templates = workoutDao.getTemplatesByPlanAndDay(selectedPlan.planId, firstDay);
                for (TemplateEntity temp : templates) {
                    ExerciseEntity ex = new ExerciseEntity(temp.baseId, temp.defaultWeight, temp.defaultReps, temp.defaultSets, false);
                    workoutDao.insert(ex);
                }
            }
            runOnUiThread(() -> {
                Toast.makeText(this, "已启用: " + selectedPlan.planName, Toast.LENGTH_SHORT).show();
                loadData();
            });
        });
    }

    private void showCopyDayDialog(int sourcePlanId, String dayName) {
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

            // 检查目标计划是否已存在该日子 (可选优化: 自动重命名 dayName + "(副本)")
            String targetDayName = dayName;

            for (TemplateEntity temp : sourceTemplates) {
                TemplateEntity newTemp = new TemplateEntity(
                        targetPlan.planId, targetDayName, temp.dayIndex,
                        temp.baseId, temp.defaultWeight, temp.defaultSets, temp.defaultReps
                );
                workoutDao.insertTemplate(newTemp);
            }

            runOnUiThread(() -> {
                Toast.makeText(this, "已复制到 " + targetPlan.planName, Toast.LENGTH_SHORT).show();
                // 【修复】复制完成后，立即刷新数据
                loadData();
            });
        });
    }

    private void showAddPlanDialog() {
        EditText input = new EditText(this);
        input.setHint("计划名称 (如: 五分化)");
        // 简单弹窗也可以加上自动键盘
        input.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                input.post(() -> {
                    InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                    imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
                });
            }
        });
        input.requestFocus();

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

            // 使用系统占位符（固定 ID=1）
            TemplateEntity placeholder = new TemplateEntity(
                    (int)newId, "Day 1", 0, com.example.fitness_plan.data.AppDatabase.SYSTEM_PLACEHOLDER_ID, 20.0, 3, 12
            );
            workoutDao.insertTemplate(placeholder);
            loadData();
        });
    }

    private void showAddDayDialog(PlanEntity plan) {
        EditText input = new EditText(this);
        input.setHint("例如: 肩部训练日");
        input.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                input.post(() -> {
                    InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                    imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
                });
            }
        });
        input.requestFocus();

        new AlertDialog.Builder(this)
                .setTitle("添加训练日")
                .setView(input)
                .setPositiveButton("添加", (dialog, which) -> {
                    String dayName = input.getText().toString().trim();
                    if (!dayName.isEmpty()) {
                        executorService.execute(() -> {
                            // 使用系统占位符（固定 ID=1）
                            com.example.fitness_plan.data.TemplateEntity placeholder = new com.example.fitness_plan.data.TemplateEntity(
                                    plan.planId, dayName, 0, com.example.fitness_plan.data.AppDatabase.SYSTEM_PLACEHOLDER_ID, 20.0, 3, 12
                            );
                            workoutDao.insertTemplate(placeholder);
                            loadData();
                        });
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showDeletePlanDialog(PlanEntity plan) {
        String warningMsg = "确定删除 \"" + plan.planName + "\" 及其所有内容吗？";
        if (plan.isActive) {
            warningMsg += "\n(注意：这是当前正在使用的计划，删除后将重置为无计划状态)";
        }
        new AlertDialog.Builder(this)
                .setTitle("删除计划")
                .setMessage(warningMsg)
                .setPositiveButton("删除", (dialog, which) -> {
                    executorService.execute(() -> {
                        workoutDao.deleteTemplatesByPlanId(plan.planId);
                        if (plan.isActive) {
                            workoutDao.deactivateAllPlans();
                            workoutDao.clearCurrentPlan();
                            getSharedPreferences("fitness_prefs", MODE_PRIVATE).edit().putInt("PLAN_INDEX", 0).apply();
                        }
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