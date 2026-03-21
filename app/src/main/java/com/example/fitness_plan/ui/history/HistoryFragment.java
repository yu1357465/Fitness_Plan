package com.example.fitness_plan.ui.history;

import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
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
    private TextView tvEmpty;
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

        // 2. 初始化数据库和缓存
        workoutDao = AppDatabase.getDatabase(requireContext()).workoutDao();

        // 确保缓存已连接 DAO，以便在 Adapter 里查名字
        EntityNameCache.getInstance().setDao(workoutDao);

        // 3. 加载数据
        loadHistoryData();
    }

    @Override
    public void onResume() {
        super.onResume();
        // 每次滑回来时刷新，以防在主页刚归档了新数据
        loadHistoryData();
    }

    private void loadHistoryData() {
        executorService.execute(() -> {
            List<HistoryEntity> list = workoutDao.getAllHistory();

            // 预热缓存：把所有用到的 baseId 的名字一次性查出来放入内存
            // 这样 Adapter 滑动时就不会卡顿
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

            // ✅ 核心修改：在这里把长按监听器传给 Adapter
            if (adapter == null) {
                adapter = new HistoryAdapter(requireContext(), historyList, entity -> {
                    // 接收到长按事件，弹出编辑框
                    showEditHistoryDialog(entity);
                });
                recyclerView.setAdapter(adapter);
            } else {
                adapter.setData(historyList);
            }
        }
    }

    // ==========================================
    // 新增：历史记录编辑与删除弹窗逻辑
    // ==========================================
    private void showEditHistoryDialog(HistoryEntity entity) {
        if (getContext() == null) return;

        // 动态构建一个简单的输入布局
        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 40);

        final EditText etWeight = new EditText(requireContext());
        etWeight.setHint("重量 (kg)");
        // 如果重量是整数（比如 50.0），去掉小数点显示更美观
        String weightStr = (entity.weight % 1 == 0) ? String.valueOf((int)entity.weight) : String.valueOf(entity.weight);
        etWeight.setText(weightStr);
        etWeight.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        layout.addView(etWeight);

        final EditText etReps = new EditText(requireContext());
        etReps.setHint("次数");
        etReps.setText(String.valueOf(entity.reps));
        etReps.setInputType(InputType.TYPE_CLASS_NUMBER);
        layout.addView(etReps);

        String exerciseName = EntityNameCache.getInstance().getExerciseName(entity.baseId);

        new AlertDialog.Builder(requireContext())
                .setTitle("修改历史记录: " + exerciseName)
                .setView(layout)
                .setPositiveButton("保存修改", (dialog, which) -> {
                    try {
                        String weightInput = etWeight.getText().toString().trim();
                        String repsInput = etReps.getText().toString().trim();

                        if(weightInput.isEmpty() || repsInput.isEmpty()) {
                            Toast.makeText(requireContext(), "重量和次数不能为空", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        double newWeight = Double.parseDouble(weightInput);
                        int newReps = Integer.parseInt(repsInput);

                        entity.weight = newWeight;
                        entity.reps = newReps;

                        // 后台更新数据库并刷新列表
                        executorService.execute(() -> {
                            workoutDao.updateHistory(entity);
                            if (getActivity() != null) {
                                getActivity().runOnUiThread(this::loadHistoryData);
                            }
                        });
                    } catch (NumberFormatException e) {
                        Toast.makeText(requireContext(), "输入格式有误", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("删除记录", (dialog, which) -> {
                    // 增加一次二次确认，防止误删
                    new AlertDialog.Builder(requireContext())
                            .setTitle("确认删除")
                            .setMessage("确定要删除这条训练记录吗？此操作无法撤销。")
                            .setPositiveButton("确认删除", (d, w) -> {
                                executorService.execute(() -> {
                                    workoutDao.deleteHistory(entity);
                                    if (getActivity() != null) {
                                        getActivity().runOnUiThread(this::loadHistoryData);
                                    }
                                });
                            })
                            .setNegativeButton("取消", null)
                            .show();
                })
                .setNeutralButton("取消", null)
                .show();
    }
}