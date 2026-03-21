package com.example.fitness_plan.ui.library;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
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

    // 保存完整数据用于搜索过滤
    private List<ExerciseBaseEntity> allExercises = new ArrayList<>();

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
                // 点击动作，可以弹窗显示详细信息，或者留空
                showEditDialog(entity);
            }

            @Override
            public void onItemLongClick(ExerciseBaseEntity entity) {
                showEditDialog(entity);
            }
        });
        recyclerView.setAdapter(adapter);

        // 监听搜索框
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
            // 获取所有未删除的动作 (排除占位符)
            allExercises = workoutDao.getAllActiveExerciseBases();

            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    // 初始显示全部，或者应用当前的搜索词
                    filterList(etSearch.getText().toString());
                });
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
            if (item.name.toLowerCase().contains(query.toLowerCase()) ||
                    item.category.contains(query)) {
                filtered.add(item);
            }
        }
        adapter.setData(filtered);
    }

    // ==========================================
    //  核心逻辑：编辑与重命名 (影响全局)
    // ==========================================
    private void showEditDialog(ExerciseBaseEntity entity) {
        // 简单复用之前的 Dialog 逻辑，或者新建一个更详细的编辑框
        // 这里允许修改 Name, Category, DefaultUnit

        // 1. 构造简单的布局 (实际开发建议写个 dialog_edit_library.xml)
        android.widget.LinearLayout layout = new android.widget.LinearLayout(requireContext());
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 40);

        final EditText etName = new EditText(requireContext());
        etName.setHint("动作名称");
        etName.setText(entity.name);
        layout.addView(etName);

        final EditText etCategory = new EditText(requireContext());
        etCategory.setHint("分类 (如: 胸部)");
        etCategory.setText(entity.category);
        layout.addView(etCategory);

        new AlertDialog.Builder(requireContext())
                .setTitle("编辑动作")
                .setView(layout)
                .setPositiveButton("保存", (dialog, which) -> {
                    String newName = etName.getText().toString().trim();
                    String newCat = etCategory.getText().toString().trim();
                    if (!newName.isEmpty()) {
                        updateExercise(entity, newName, newCat);
                    }
                })
                .setNegativeButton("删除", (dialog, which) -> {
                    // 再次确认删除
                    new AlertDialog.Builder(requireContext())
                            .setTitle("确认删除 " + entity.name + "?")
                            .setMessage("历史数据将保留，但该动作将不再出现在选择列表中。")
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

            // ⭐ 关键：更新缓存，否则主页显示旧名字
            EntityNameCache.getInstance().updateCache(entity.baseId, newName);

            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    Toast.makeText(requireContext(), "更新成功", Toast.LENGTH_SHORT).show();
                    loadData(); // 刷新列表
                });
            }
        });
    }

    private void deleteExercise(ExerciseBaseEntity entity) {
        executorService.execute(() -> {
            workoutDao.softDeleteExercise(entity.baseId);

            // 缓存中移除 (或者保留但标记，这里直接移除即可)
            // EntityNameCache.getInstance().remove(entity.baseId);

            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    Toast.makeText(requireContext(), "已删除", Toast.LENGTH_SHORT).show();
                    loadData(); // 刷新列表
                });
            }
        });
    }

    private void showAddDialog() {
        final EditText input = new EditText(requireContext());
        input.setHint("动作名称");

        new AlertDialog.Builder(requireContext())
                .setTitle("新建标准动作")
                .setView(input)
                .setPositiveButton("添加", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty()) {
                        executorService.execute(() -> {
                            // 查重
                            if (workoutDao.getExerciseBaseByName(name) != null) {
                                if(getActivity()!=null) getActivity().runOnUiThread(() -> Toast.makeText(requireContext(), "动作已存在", Toast.LENGTH_SHORT).show());
                                return;
                            }

                            ExerciseBaseEntity newBase = new ExerciseBaseEntity(name, "kg", "未分类");
                            workoutDao.insertExerciseBase(newBase);

                            if (getActivity() != null) {
                                getActivity().runOnUiThread(() -> {
                                    Toast.makeText(requireContext(), "添加成功", Toast.LENGTH_SHORT).show();
                                    loadData();
                                    etSearch.setText(""); // 清空搜索以便看到新动作
                                });
                            }
                        });
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }
}