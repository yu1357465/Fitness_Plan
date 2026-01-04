package com.example.fitness_plan;

import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.fitness_plan.data.AppDatabase;
import com.example.fitness_plan.data.ExerciseEntity;
import com.example.fitness_plan.data.HistoryEntity;
import com.example.fitness_plan.data.HistorySession;
import com.example.fitness_plan.data.WorkoutDao;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HistoryActivity extends AppCompatActivity {

    private RecyclerView historyRecyclerView;
    private HistoryAdapter adapter;
    private WorkoutDao workoutDao;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
            getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        }

        historyRecyclerView = findViewById(R.id.historyRecyclerView);
        historyRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        workoutDao = AppDatabase.getDatabase(this).workoutDao();

        loadHistoryData();
    }

    private void loadHistoryData() {
        executorService.execute(() -> {
            // 1. 读取历史表
            List<HistoryEntity> rawList = workoutDao.getAllHistory();

            // 2. 分组逻辑
            Map<String, HistorySession> sessionMap = new LinkedHashMap<>();

            for (HistoryEntity record : rawList) {
                String key = record.dateStr + "_" + record.workoutName;
                if (!sessionMap.containsKey(key)) {
                    sessionMap.put(key, new HistorySession(record.dateStr, record.workoutName, new ArrayList<>()));
                }

                ExerciseEntity displayItem = new ExerciseEntity(
                        record.exerciseName, record.weight, record.reps, record.sets, true
                );
                sessionMap.get(key).exercises.add(displayItem);
            }

            List<HistorySession> sessions = new ArrayList<>(sessionMap.values());

            runOnUiThread(() -> {
                adapter = new HistoryAdapter(this, sessions);
                historyRecyclerView.setAdapter(adapter);
            });
        });
    }
}