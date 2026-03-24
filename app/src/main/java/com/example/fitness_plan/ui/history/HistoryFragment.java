package com.example.fitness_plan.ui.history;

import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.fitness_plan.HistoryAdapter;
import com.example.fitness_plan.R;
import com.example.fitness_plan.data.AppDatabase;
import com.example.fitness_plan.data.EntityNameCache;
import com.example.fitness_plan.data.HistoryEntity;
import com.example.fitness_plan.data.WorkoutDao;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HistoryFragment extends Fragment {

    private RecyclerView recyclerView;
    private android.widget.TextView tvEmpty;
    private HistoryAdapter adapter;
    private List<HistoryEntity> historyList = new ArrayList<>();

    private WorkoutDao workoutDao;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    public HistoryFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_history, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 1. 初始化视图
        recyclerView = view.findViewById(R.id.historyRecyclerView);
        tvEmpty = view.findViewById(R.id.tvEmptyHistory);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        // ==========================================
        // V2.1 新增：绑定右上角的克制加号 (Icon Button)
        // ==========================================
        android.widget.ImageView ivAddHistoryTop = view.findViewById(R.id.ivAddHistoryTop);
        if (ivAddHistoryTop != null) {
            ivAddHistoryTop.setOnClickListener(v -> {
                // 点击唤醒手动补录的高级抽屉 (Creator)
                showAddHistoryDialog();
            });
        }

        // 2. 初始化数据库和缓存
        workoutDao = AppDatabase.getDatabase(requireContext()).workoutDao();
        EntityNameCache.getInstance().setDao(workoutDao);

        // 3. 加载数据
        loadHistoryData();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadHistoryData();
    }

    private void loadHistoryData() {
        executorService.execute(() -> {
            List<HistoryEntity> list = workoutDao.getAllHistory();
            EntityNameCache.getInstance().preloadAll(workoutDao);

            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    historyList = list;
                    updateUI();
                });
            }
        });
    }

    private void updateUI() {
        if (historyList == null || historyList.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            tvEmpty.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);

            if (adapter == null) {
                adapter = new HistoryAdapter(requireContext(), historyList, entity -> {
                    // 接收到长按事件，弹出编辑高级抽屉 (Modifier)
                    showEditHistoryDialog(entity);
                });
                recyclerView.setAdapter(adapter);
            } else {
                adapter.setData(historyList);
            }
        }
    }

    // ==========================================
    // V2.1 修复版：高级底部抽屉 - 编辑与删除
    // ==========================================
    private void showEditHistoryDialog(HistoryEntity entity) {
        if (getContext() == null) return;

        com.google.android.material.bottomsheet.BottomSheetDialog sheet =
                new com.google.android.material.bottomsheet.BottomSheetDialog(requireContext(), com.google.android.material.R.style.Theme_Design_BottomSheetDialog);
        View view = getLayoutInflater().inflate(R.layout.dialog_bottom_sheet_history_edit, null);
        sheet.setContentView(view);

        com.google.android.material.textfield.TextInputEditText etPlanName = view.findViewById(R.id.etEditPlanName);
        com.google.android.material.textfield.TextInputEditText etDayTitle = view.findViewById(R.id.etEditDayTitle);
        com.google.android.material.textfield.TextInputEditText etName = view.findViewById(R.id.etEditName);
        com.google.android.material.textfield.TextInputEditText etWeight = view.findViewById(R.id.etEditWeight);
        com.google.android.material.textfield.TextInputEditText etSets = view.findViewById(R.id.etEditSets);
        com.google.android.material.textfield.TextInputEditText etReps = view.findViewById(R.id.etEditReps);
        android.widget.Button btnSave = view.findViewById(R.id.btnSaveHistory);
        // ⭐ 绑定我们刚加的删除按钮
        android.widget.Button btnDelete = view.findViewById(R.id.btnDeleteHistory);

        // ⭐ 核心修复：读取底层表里刚刚存入的真实 planName
        etPlanName.setText(entity.planName != null ? entity.planName : "自由训练");
        etPlanName.setEnabled(false); // 锁死
        etDayTitle.setText(entity.workoutName != null ? entity.workoutName : "");

        String exerciseName = EntityNameCache.getInstance().getExerciseName(entity.baseId);
        etName.setText(exerciseName);
        etName.setEnabled(false); // 动作名称锁死

        String weightStr = (entity.weight % 1 == 0) ? String.valueOf((int)entity.weight) : String.valueOf(entity.weight);
        etWeight.setText(weightStr);
        etSets.setText(String.valueOf(entity.sets));
        etReps.setText(String.valueOf(entity.reps));

        btnSave.setOnClickListener(v -> {
            try {
                String wInput = etWeight.getText().toString().trim();
                String sInput = etSets.getText().toString().trim();
                String rInput = etReps.getText().toString().trim();
                String dayInput = etDayTitle.getText().toString().trim(); // 获取修改后的训练日

                if (wInput.isEmpty() || rInput.isEmpty()) return;

                entity.weight = Double.parseDouble(wInput);
                entity.sets = sInput.isEmpty() ? 1 : Integer.parseInt(sInput);
                entity.reps = Integer.parseInt(rInput);
                entity.workoutName = dayInput;

                executorService.execute(() -> {
                    workoutDao.updateHistory(entity);
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            sheet.dismiss();
                            loadHistoryData();
                        });
                    }
                });
            } catch (Exception e) {
                Toast.makeText(requireContext(), "输入格式错误", Toast.LENGTH_SHORT).show();
            }
        });

        // ⭐ 明明白白的删除逻辑
        if (btnDelete != null) {
            btnDelete.setOnClickListener(v -> {
                new AlertDialog.Builder(requireContext())
                        .setTitle("⚠️ 确认删除")
                        .setMessage("确定要彻底删除这条记录吗？数据不可恢复。")
                        .setPositiveButton("确认删除", (d, w) -> {
                            executorService.execute(() -> {
                                workoutDao.deleteHistory(entity);
                                if (getActivity() != null) {
                                    getActivity().runOnUiThread(() -> {
                                        sheet.dismiss();
                                        loadHistoryData();
                                    });
                                }
                            });
                        })
                        .setNegativeButton("取消", null)
                        .show();
            });
        }

        // ⭐ 核心修复：解决键盘遮挡问题 (Soft Input Mode)
        if (sheet.getWindow() != null) {
            sheet.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        sheet.setOnShowListener(dialog -> {
            com.google.android.material.bottomsheet.BottomSheetDialog d = (com.google.android.material.bottomsheet.BottomSheetDialog) dialog;
            android.widget.FrameLayout bottomSheet = d.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                // 强制展开到底部，不被键盘顶碎
                com.google.android.material.bottomsheet.BottomSheetBehavior.from(bottomSheet).setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
            }
        });

        sheet.show();
    }

    // ==========================================
    // V2.1 修复版：高级底部抽屉 - 手动补录
    // ==========================================
    private void showAddHistoryDialog() {
        if (getContext() == null) return;

        com.google.android.material.bottomsheet.BottomSheetDialog sheet =
                new com.google.android.material.bottomsheet.BottomSheetDialog(requireContext(), com.google.android.material.R.style.Theme_Design_BottomSheetDialog);
        View view = getLayoutInflater().inflate(R.layout.dialog_bottom_sheet_history_add, null);
        sheet.setContentView(view);

        android.widget.Button btnAddDate = view.findViewById(R.id.btnAddDate);
        com.google.android.material.textfield.TextInputEditText etPlanName = view.findViewById(R.id.etAddPlanName);
        com.google.android.material.textfield.TextInputEditText etDayTitle = view.findViewById(R.id.etAddDayTitle);
        com.google.android.material.textfield.TextInputEditText etName = view.findViewById(R.id.etAddName);
        com.google.android.material.textfield.TextInputEditText etWeight = view.findViewById(R.id.etAddWeight);
        com.google.android.material.textfield.TextInputEditText etSets = view.findViewById(R.id.etAddSets);
        com.google.android.material.textfield.TextInputEditText etReps = view.findViewById(R.id.etAddReps);
        android.widget.Button btnConfirm = view.findViewById(R.id.btnAddConfirm);

        // 向上寻找两层父级（TextInputEditText -> FrameLayout -> TextInputLayout），将其整体隐藏
        if (etPlanName.getParent() != null && etPlanName.getParent().getParent() instanceof android.view.View) {
            ((android.view.View) etPlanName.getParent().getParent()).setVisibility(android.view.View.GONE);
        }

        final long[] selectedDate = {System.currentTimeMillis()};
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy年MM月dd日", java.util.Locale.CHINA);
        btnAddDate.setText("日期: " + sdf.format(new java.util.Date(selectedDate[0])));

        btnAddDate.setOnClickListener(v -> {
            java.util.Calendar calendar = java.util.Calendar.getInstance();
            new android.app.DatePickerDialog(requireContext(), (view1, year, month, dayOfMonth) -> {
                calendar.set(year, month, dayOfMonth);
                selectedDate[0] = calendar.getTimeInMillis();
                btnAddDate.setText("日期: " + sdf.format(calendar.getTime()));
            }, calendar.get(java.util.Calendar.YEAR), calendar.get(java.util.Calendar.MONTH), calendar.get(java.util.Calendar.DAY_OF_MONTH)).show();
        });

        btnConfirm.setOnClickListener(v -> {
            String nameInput = etName.getText().toString().trim();
            String weightInput = etWeight.getText().toString().trim();
            String setsInput = etSets.getText().toString().trim();
            String repsInput = etReps.getText().toString().trim();
            String dayTitleInput = etDayTitle.getText().toString().trim(); // 获取填写的训练日

            if (nameInput.isEmpty() || weightInput.isEmpty() || repsInput.isEmpty()) {
                Toast.makeText(requireContext(), "动作、重量和次数必须填！", Toast.LENGTH_SHORT).show();
                return;
            }

            executorService.execute(() -> {
                com.example.fitness_plan.data.ExerciseBaseEntity base = workoutDao.getExerciseBaseByName(nameInput);
                long baseId;
                if (base == null) {
                    base = new com.example.fitness_plan.data.ExerciseBaseEntity(nameInput, "kg", "Other");
                    baseId = workoutDao.insertExerciseBase(base);
                } else {
                    baseId = base.baseId;
                }

                try {
                    // Mechanism: 这里的第三个参数就是 workoutName
                    HistoryEntity newHistory = new HistoryEntity(
                            selectedDate[0],
                            sdf.format(new java.util.Date(selectedDate[0])),
                            "手工补录", // ⭐ 这里就是我们刚刚加的 planName (第3个参数)
                            dayTitleInput.isEmpty() ? "未分类" : dayTitleInput, // 这是 workoutName (第4个参数)
                            baseId,
                            Double.parseDouble(weightInput),
                            Integer.parseInt(repsInput),
                            setsInput.isEmpty() ? 1 : Integer.parseInt(setsInput)
                    );
                    workoutDao.insertHistory(newHistory);
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            Toast.makeText(requireContext(), "✅ 补录成功", Toast.LENGTH_SHORT).show();
                            sheet.dismiss();
                            loadHistoryData();
                        });
                    }
                } catch (Exception e) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> Toast.makeText(requireContext(), "数字格式有误", Toast.LENGTH_SHORT).show());
                    }
                }
            });
        });

        // ⭐ 核心修复：解决键盘遮挡问题
        if (sheet.getWindow() != null) {
            sheet.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        sheet.setOnShowListener(dialog -> {
            com.google.android.material.bottomsheet.BottomSheetDialog d = (com.google.android.material.bottomsheet.BottomSheetDialog) dialog;
            android.widget.FrameLayout bottomSheet = d.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                com.google.android.material.bottomsheet.BottomSheetBehavior.from(bottomSheet).setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
            }
        });

        sheet.show();
    }
}