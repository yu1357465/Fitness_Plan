package com.example.fitness_plan;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.fitness_plan.data.AppDatabase;
import com.example.fitness_plan.data.HistoryEntity;
import com.example.fitness_plan.data.WorkoutDao;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StatsActivity extends AppCompatActivity {

    private WorkoutDao workoutDao;
    private RecyclerView recyclerView;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stats);

        workoutDao = AppDatabase.getDatabase(this).workoutDao();
        recyclerView = findViewById(R.id.statsRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        loadAllData();
    }

    private void loadAllData() {
        executorService.execute(() -> {
            // 1. 获取所有历史记录 (按时间排序)
            // 如果 DAO 里没有 getAllHistory()，请加上: @Query("SELECT * FROM history_table ORDER BY date ASC")
            List<HistoryEntity> allHistory = workoutDao.getAllHistory();

            // 2. 数据分组逻辑 (List -> Map)
            Map<String, List<HistoryEntity>> groupedMap = new HashMap<>();
            List<String> nameList = new ArrayList<>();

            for (HistoryEntity item : allHistory) {
                String name = item.name;

                // 如果这是新动作，加入列表
                if (!groupedMap.containsKey(name)) {
                    groupedMap.put(name, new ArrayList<>());
                    nameList.add(name);
                }
                // 加入对应的历史列表
                groupedMap.get(name).add(item);
            }

            // 3. 排序动作名称 (可选)
            Collections.sort(nameList);

            // 4. 设置适配器
            runOnUiThread(() -> {
                StatsAdapter adapter = new StatsAdapter(nameList, groupedMap);
                recyclerView.setAdapter(adapter);
            });
        });
    }
}