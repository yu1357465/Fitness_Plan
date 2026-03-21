package com.example.fitness_plan.ui.library;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.fitness_plan.R;
import com.example.fitness_plan.data.AppDatabase;
import com.example.fitness_plan.data.EntityNameCache;
import com.example.fitness_plan.data.ExerciseBaseEntity;
import com.example.fitness_plan.data.WorkoutDao;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LibraryFragment extends Fragment {

    private RecyclerView recyclerView;
    private EditText etSearch;
    private LibraryAdapter adapter;

    private WorkoutDao workoutDao;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    private List<ExerciseBaseEntity> allExercises = new ArrayList<>();

    // ⭐ 雷达图标准肌群字典
    private final String[] MUSCLE_GROUPS = {"胸部 (Chest)", "背部 (Back)", "腿臀 (Legs&Glutes)", "肩部 (Shoulders)", "手臂 (Arms)", "核心 (Core)", "其他 (Other)"};

    public LibraryFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_library, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        workoutDao = AppDatabase.getDatabase(requireContext()).workoutDao();

        recyclerView = view.findViewById(R.id.libraryRecyclerView);
        etSearch = view.findViewById(R.id.etSearch);
        View fab = view.findViewById(R.id.fabAddBase);

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        adapter = new LibraryAdapter(new LibraryAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(ExerciseBaseEntity entity) {
                showEditDialog(entity);
            }

            @Override
            public void onItemLongClick(ExerciseBaseEntity entity) {
                showEditDialog(entity);
            }
        });
        recyclerView.setAdapter(adapter);

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterList(s.toString());
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        fab.setOnClickListener(v -> showAddDialog());

        loadData();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadData();
    }

    private void loadData() {
        executorService.execute(() -> {
            allExercises = workoutDao.getAllActiveExerciseBases();
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> filterList(etSearch.getText().toString()));
            }
        });
    }

    private void filterList(String query) {
        if (query.isEmpty()) {
            adapter.setData(allExercises);
            return;
        }

        List<ExerciseBaseEntity> filtered = new ArrayList<>();
        for (ExerciseBaseEntity item : allExercises) {
            // 兼容防呆：防止老数据 category 为空导致崩溃
            String cat = item.category != null ? item.category : "";
            if (item.name.toLowerCase().contains(query.toLowerCase()) ||
                    cat.toLowerCase().contains(query.toLowerCase())) {
                filtered.add(item);
            }
        }
        adapter.setData(filtered);
    }

    // ==========================================
    //  核心逻辑：编辑动作 (已升级下拉框)
    // ==========================================
    private void showEditDialog(ExerciseBaseEntity entity) {
        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 40);

        final EditText etName = new EditText(requireContext());
        etName.setHint("动作名称");
        etName.setText(entity.name);
        layout.addView(etName);

        TextView tvLabel = new TextView(requireContext());
        tvLabel.setText("目标肌群 (用于雷达图):");
        tvLabel.setPadding(0, 30, 0, 10);
        tvLabel.setTextColor(android.graphics.Color.parseColor("#757575"));
        layout.addView(tvLabel);

        // ⭐ 替换为 Spinner 下拉框
        final Spinner spinner = new Spinner(requireContext());
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, MUSCLE_GROUPS);
        spinner.setAdapter(spinnerAdapter);
        layout.addView(spinner);

        // 逆向匹配：根据数据库里的纯英文，在下拉菜单中找到对应的中文并选中
        String currentCat = entity.category != null ? entity.category : "Other";
        int selectedIndex = MUSCLE_GROUPS.length - 1; // 默认选中 Other
        for (int i = 0; i < MUSCLE_GROUPS.length; i++) {
            if (MUSCLE_GROUPS[i].contains("(" + currentCat + ")") ||
                    (currentCat.equals("Other") && MUSCLE_GROUPS[i].contains("其他"))) {
                selectedIndex = i;
                break;
            }
        }
        spinner.setSelection(selectedIndex);

        new AlertDialog.Builder(requireContext())
                .setTitle("编辑动作")
                .setView(layout)
                .setPositiveButton("保存", (dialog, which) -> {
                    String newName = etName.getText().toString().trim();
                    // 正向提取：截取括号里的纯英文存入数据库
                    String selectedText = spinner.getSelectedItem().toString();
                    String newCat = selectedText.contains("(") ?
                            selectedText.substring(selectedText.indexOf("(") + 1, selectedText.indexOf(")")) : "Other";

                    if (!newName.isEmpty()) {
                        updateExercise(entity, newName, newCat);
                    }
                })
                .setNegativeButton("删除", (dialog, which) -> {
                    new AlertDialog.Builder(requireContext())
                            .setTitle("确认删除 " + entity.name + "?")
                            .setMessage("历史打卡数据将保留，但该动作将从备选库中永久移除。")
                            .setPositiveButton("确认删除", (d, w) -> deleteExercise(entity))
                            .setNegativeButton("取消", null)
                            .show();
                })
                .setNeutralButton("取消", null)
                .show();
    }

    private void updateExercise(ExerciseBaseEntity entity, String newName, String newCat) {
        executorService.execute(() -> {
            entity.name = newName;
            entity.category = newCat;
            workoutDao.updateExerciseBase(entity);

            EntityNameCache.getInstance().updateCache(entity.baseId, newName);

            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    Toast.makeText(requireContext(), "更新成功", Toast.LENGTH_SHORT).show();
                    loadData();
                });
            }
        });
    }

    private void deleteExercise(ExerciseBaseEntity entity) {
        executorService.execute(() -> {
            workoutDao.softDeleteExercise(entity.baseId);
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    Toast.makeText(requireContext(), "已删除", Toast.LENGTH_SHORT).show();
                    loadData();
                });
            }
        });
    }

    // ==========================================
    //  核心逻辑：新建动作 (已升级下拉框)
    // ==========================================
    private void showAddDialog() {
        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 40);

        final EditText input = new EditText(requireContext());
        input.setHint("动作名称 (如: 杠铃划船)");
        layout.addView(input);

        TextView tvLabel = new TextView(requireContext());
        tvLabel.setText("目标肌群 (用于雷达图):");
        tvLabel.setPadding(0, 30, 0, 10);
        tvLabel.setTextColor(android.graphics.Color.parseColor("#757575"));
        layout.addView(tvLabel);

        final Spinner spinner = new Spinner(requireContext());
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, MUSCLE_GROUPS);
        spinner.setAdapter(spinnerAdapter);
        layout.addView(spinner);

        // 自动弹出软键盘
        input.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                input.post(() -> {
                    InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                    if (imm != null) imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
                });
            }
        });

        new AlertDialog.Builder(requireContext())
                .setTitle("新建标准动作")
                .setView(layout)
                .setPositiveButton("添加", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    String selectedText = spinner.getSelectedItem().toString();
                    String dbCategory = selectedText.contains("(") ?
                            selectedText.substring(selectedText.indexOf("(") + 1, selectedText.indexOf(")")) : "Other";

                    if (!name.isEmpty()) {
                        executorService.execute(() -> {
                            if (workoutDao.getExerciseBaseByName(name) != null) {
                                if(getActivity()!=null) getActivity().runOnUiThread(() -> Toast.makeText(requireContext(), "动作已存在，请勿重复添加", Toast.LENGTH_SHORT).show());
                                return;
                            }

                            // ⭐ 创建实体时，写入标准的英文 Category
                            ExerciseBaseEntity newBase = new ExerciseBaseEntity(name, "kg", dbCategory);
                            workoutDao.insertExerciseBase(newBase);

                            if (getActivity() != null) {
                                getActivity().runOnUiThread(() -> {
                                    Toast.makeText(requireContext(), "添加成功", Toast.LENGTH_SHORT).show();
                                    loadData();
                                    etSearch.setText("");
                                });
                            }
                        });
                    }
                })
                .setNegativeButton("取消", null)
                .show();

        input.requestFocus();
    }
}