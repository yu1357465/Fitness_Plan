package com.example.fitness_plan;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.fitness_plan.data.AppDatabase;
import com.example.fitness_plan.data.EntityNameCache;
import com.example.fitness_plan.data.ExerciseBaseEntity;
import com.example.fitness_plan.data.HistoryEntity;
import com.example.fitness_plan.data.WorkoutDao;
import com.github.mikephil.charting.charts.RadarChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.RadarData;
import com.github.mikephil.charting.data.RadarDataSet;
import com.github.mikephil.charting.data.RadarEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;

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
    private RadarChart radarChart;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    // 身体维度
    private float userHeight, userWingspan, userWeight;
    private float apeIndex = 1.0f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stats);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            getWindow().getDecorView().setSystemUiVisibility(
                    android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                            android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
            getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        }

        workoutDao = AppDatabase.getDatabase(this).workoutDao();
        recyclerView = findViewById(R.id.statsRecyclerView);
        radarChart = findViewById(R.id.radarChart);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        loadBodyMetrics();
        setupRadarChartUI();
        loadAndAnalyzeData();
    }

    private void loadBodyMetrics() {
        SharedPreferences prefs = getSharedPreferences("fitness_prefs", MODE_PRIVATE);
        userHeight = prefs.getFloat("USER_HEIGHT_CM", 0f);
        userWingspan = prefs.getFloat("USER_WINGSPAN_CM", 0f);
        userWeight = prefs.getFloat("USER_BODY_WEIGHT_KG", 0f);
        if (userWeight == 0) userWeight = 70f;
        if (userHeight > 0 && userWingspan > 0) {
            apeIndex = userWingspan / userHeight;
        }
    }

    private void setupRadarChartUI() {
        radarChart.getDescription().setEnabled(false);
        radarChart.getLegend().setEnabled(false);
        radarChart.setWebLineWidth(1f);
        radarChart.setWebColor(Color.LTGRAY);
        radarChart.setWebLineWidthInner(1f);
        radarChart.setWebColorInner(Color.LTGRAY);
        radarChart.setWebAlpha(100);

        XAxis xAxis = radarChart.getXAxis();
        xAxis.setTextSize(12f);
        xAxis.setYOffset(-15f);
        xAxis.setXOffset(0f);
        xAxis.setValueFormatter(new IndexAxisValueFormatter(new String[]{"胸 Chest", "背 Back", "腿 Legs", "肩 Shoulders", "臂 Arms", "核心 Core"}));
        xAxis.setTextColor(Color.parseColor("#424242"));

        YAxis yAxis = radarChart.getYAxis();
        yAxis.setLabelCount(5, false);
        yAxis.setTextSize(9f);
        yAxis.setAxisMinimum(0f);
        yAxis.setAxisMaximum(100f);
        yAxis.setDrawLabels(false);
    }

    private void loadAndAnalyzeData() {
        executorService.execute(() -> {
            List<HistoryEntity> allHistory = workoutDao.getAllHistory();
            List<ExerciseBaseEntity> allBases = workoutDao.getAllExerciseBases();

            // 极简字典：BaseId -> Category，不搞任何修改保存操作
            Map<Long, String> baseCategoryMap = new HashMap<>();
            for (ExerciseBaseEntity base : allBases) {
                baseCategoryMap.put(base.baseId, base.category);
            }

            EntityNameCache nameCache = EntityNameCache.getInstance();
            nameCache.setDao(workoutDao);

            Map<String, List<HistoryEntity>> groupedMap = new HashMap<>();
            List<String> nameList = new ArrayList<>();
            Map<String, Double> max1RmMap = new HashMap<>();
            Map<String, Double> corrected1RmMap = new HashMap<>();

            // 6 大维度的最高得分记录器
            double maxChestScore = 0, maxBackScore = 0, maxLegsScore = 0;
            double maxShouldersScore = 0, maxArmsScore = 0, maxCoreScore = 0;

            for (HistoryEntity item : allHistory) {
                String name = nameCache.getExerciseName(item.baseId);
                String category = baseCategoryMap.get(item.baseId);

                // 防呆：如果是空或者是老数据，直接算作 Other
                if (category == null || category.trim().isEmpty() || category.equals("未分类")) {
                    category = "Other";
                }

                if (!groupedMap.containsKey(name)) {
                    groupedMap.put(name, new ArrayList<>());
                    nameList.add(name);
                }
                groupedMap.get(name).add(item);

                if (item.reps > 0 && item.reps <= 15) {
                    double current1Rm = item.weight * (36.0 / (37.0 - item.reps));
                    double previousMax = max1RmMap.containsKey(name) ? max1RmMap.get(name) : 0.0;

                    if (current1Rm > previousMax) {
                        max1RmMap.put(name, current1Rm);
                        double correctedRm = current1Rm;

                        // 物理修正 Ape Index
                        if (category.equals("Chest") || category.equals("Shoulders")) correctedRm = current1Rm * apeIndex;
                        else if (category.equals("Back") || category.equals("Legs&Glutes")) correctedRm = current1Rm / apeIndex;

                        corrected1RmMap.put(name, correctedRm);

                        // 标准化换算 (0-100分)
                        double score = 0;
                        switch (category) {
                            case "Chest": score = (correctedRm / (userWeight * 1.5)) * 100; maxChestScore = Math.max(maxChestScore, score); break;
                            case "Back": score = (correctedRm / (userWeight * 1.2)) * 100; maxBackScore = Math.max(maxBackScore, score); break;
                            case "Legs&Glutes": score = (correctedRm / (userWeight * 2.0)) * 100; maxLegsScore = Math.max(maxLegsScore, score); break;
                            case "Shoulders": score = (correctedRm / (userWeight * 1.0)) * 100; maxShouldersScore = Math.max(maxShouldersScore, score); break;
                            case "Arms": score = (correctedRm / (userWeight * 0.5)) * 100; maxArmsScore = Math.max(maxArmsScore, score); break;
                            case "Core": score = (correctedRm / (userWeight * 0.4)) * 100; maxCoreScore = Math.max(maxCoreScore, score); break;
                        }
                    }
                }
            }

            // 核心复合溢出效应补偿
            double coreBonus = (maxLegsScore * 0.15) + (maxBackScore * 0.15);
            maxCoreScore = Math.min(100, maxCoreScore + coreBonus);

            Collections.sort(nameList);

            // 限制满分 100
            float fChest = Math.min(100f, (float)maxChestScore);
            float fBack = Math.min(100f, (float)maxBackScore);
            float fLegs = Math.min(100f, (float)maxLegsScore);
            float fShoulders = Math.min(100f, (float)maxShouldersScore);
            float fArms = Math.min(100f, (float)maxArmsScore);
            float fCore = Math.min(100f, (float)maxCoreScore);

            runOnUiThread(() -> {
                ArrayList<RadarEntry> entries = new ArrayList<>();
                entries.add(new RadarEntry(fChest));
                entries.add(new RadarEntry(fBack));
                entries.add(new RadarEntry(fLegs));
                entries.add(new RadarEntry(fShoulders));
                entries.add(new RadarEntry(fArms));
                entries.add(new RadarEntry(fCore));

                RadarDataSet set = new RadarDataSet(entries, "肌肉均衡分数");
                set.setColor(Color.parseColor("#00897B"));
                set.setFillColor(Color.parseColor("#80CBC4"));
                set.setDrawFilled(true);
                set.setFillAlpha(120);
                set.setLineWidth(2f);
                set.setDrawHighlightCircleEnabled(true);
                set.setDrawHighlightIndicators(false);

                RadarData data = new RadarData(set);
                data.setValueTextSize(8f);
                data.setDrawValues(true);
                data.setValueTextColor(Color.parseColor("#212121"));
                radarChart.setData(data);
                radarChart.animateXY(1000, 1000);
                radarChart.invalidate();

                StatsAdapter adapter = new StatsAdapter(nameList, groupedMap, max1RmMap, corrected1RmMap, apeIndex);
                recyclerView.setAdapter(adapter);
            });
        });
    }
}