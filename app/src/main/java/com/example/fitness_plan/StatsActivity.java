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
    private android.view.View barStrength, barHypertrophy, barEndurance;
    private android.widget.TextView tvStrengthPct, tvHypertrophyPct, tvEndurancePct;
    // ✅ 新增：雷达图过滤模式枚举 (0=综合, 1=力量, 2=耐力)
    private int currentRadarMode = 0;
    private android.widget.RadioGroup rgRadarMode;

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

        barStrength = findViewById(R.id.barStrength);
        barHypertrophy = findViewById(R.id.barHypertrophy);
        barEndurance = findViewById(R.id.barEndurance);
        tvStrengthPct = findViewById(R.id.tvStrengthPct);
        tvHypertrophyPct = findViewById(R.id.tvHypertrophyPct);
        tvEndurancePct = findViewById(R.id.tvEndurancePct);

        // ✅ 新增：绑定 RadioGroup 并监听模式切换
        rgRadarMode = findViewById(R.id.rgRadarMode);
        rgRadarMode.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbModeAll) currentRadarMode = 0;
            else if (checkedId == R.id.rbModeStrength) currentRadarMode = 1;
            else if (checkedId == R.id.rbModeEndurance) currentRadarMode = 2;

            // 切换模式后，重新用新滤镜去计算数据和重绘雷达图
            loadAndAnalyzeData();
        });

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

            int countStrength = 0;    // 1-5次
            int countHypertrophy = 0; // 6-12次
            int countEndurance = 0;   // 13次以上
            int totalValidSets = 0;

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

                // 累加能量系统 (⭐ 进度条保留全量统计，不随雷达模式切换而变动)
                if (item.reps > 0) {
                    if (item.reps <= 5) countStrength++;
                    else if (item.reps <= 12) countHypertrophy++;
                    else countEndurance++;
                    totalValidSets++;
                }

                // ==========================================
                // ❌ 原代码注释掉：
                // if (item.reps > 0 && item.reps <= 15) {
                //     double current1Rm = item.weight * (36.0 / (37.0 - item.reps));
                // ==========================================

                // ✅ 改动后的代码：植入数据漏斗与多重算法滤镜
                boolean shouldInclude = false;
                double current1Rm = 0.0;

                if (item.reps > 0) {
                    if (currentRadarMode == 0 && item.reps <= 15) {
                        // 模式 0：综合模式 (全量数据，采用标准 Brzycki 公式)
                        shouldInclude = true;
                        current1Rm = item.weight * (36.0 / (37.0 - item.reps));
                    }
                    else if (currentRadarMode == 1 && item.reps <= 6) {
                        // 模式 1：硬核力量模式 (只看 6 次以内的大重量)
                        shouldInclude = true;
                        current1Rm = item.weight * (36.0 / (37.0 - item.reps));
                    }
                    else if (currentRadarMode == 2 && item.reps >= 10 && item.reps <= 30) {
                        // 模式 2：肌肉耐力模式 (只看 10次以上的记录)
                        shouldInclude = true;
                        // ⭐ 现实补丁：高次数采用 Epley Formula，防止 Brzycki 公式分母崩溃 (Divide by Zero 风险)
                        current1Rm = item.weight * (1.0 + item.reps / 30.0);
                    }
                }

                // 只有通过了上述漏斗的数据，才允许参与雷达图的绘制
                if (shouldInclude) {
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

            // 只在这里定义替身快照
            final int finalTotalValidSets = totalValidSets;
            final int finalCountStrength = countStrength;
            final int finalCountHypertrophy = countHypertrophy;
            final int finalCountEndurance = countEndurance;

            runOnUiThread(() -> {
                // 内部全面使用 final 替身变量
                if (finalTotalValidSets > 0) {
                    float pctStrength = (finalCountStrength * 100f) / finalTotalValidSets;
                    float pctHypertrophy = (finalCountHypertrophy * 100f) / finalTotalValidSets;
                    float pctEndurance = (finalCountEndurance * 100f) / finalTotalValidSets;

                    // 动态修改 LinearLayout 的 layout_weight
                    ((android.widget.LinearLayout.LayoutParams) barStrength.getLayoutParams()).weight = Math.max(0.01f, pctStrength);
                    ((android.widget.LinearLayout.LayoutParams) barHypertrophy.getLayoutParams()).weight = Math.max(0.01f, pctHypertrophy);
                    ((android.widget.LinearLayout.LayoutParams) barEndurance.getLayoutParams()).weight = Math.max(0.01f, pctEndurance);

                    // 请求重绘
                    barStrength.requestLayout();

                    // 更新底部文字
                    tvStrengthPct.setText(String.format("绝对力量\n%.0f%%", pctStrength));
                    tvHypertrophyPct.setText(String.format("肌肥大\n%.0f%%", pctHypertrophy));
                    tvEndurancePct.setText(String.format("肌肉耐力\n%.0f%%", pctEndurance));
                }

                ArrayList<RadarEntry> entries = new ArrayList<>();
                entries.add(new RadarEntry(fChest));
                entries.add(new RadarEntry(fBack));
                entries.add(new RadarEntry(fLegs));
                entries.add(new RadarEntry(fShoulders));
                entries.add(new RadarEntry(fArms));
                entries.add(new RadarEntry(fCore));

                // ⭐ 极客细节：强制锁定 Y 轴的物理极值
                com.github.mikephil.charting.components.YAxis yAxis = radarChart.getYAxis();
                yAxis.setAxisMinimum(0f);
                yAxis.setAxisMaximum(100f);

                // ⭐ 核心架构升级：内存复用模式 (In-place Update)
                if (radarChart.getData() != null && radarChart.getData().getDataSetCount() > 0) {
                    // 如果图表已经存在，直接提取现有图层，只替换数据
                    RadarDataSet set = (RadarDataSet) radarChart.getData().getDataSetByIndex(0);
                    set.setValues(entries);

                    // 动态变色
                    if (currentRadarMode == 1) {
                        set.setColor(Color.parseColor("#E53935"));
                        set.setFillColor(Color.parseColor("#EF9A9A"));
                    } else if (currentRadarMode == 2) {
                        set.setColor(Color.parseColor("#43A047"));
                        set.setFillColor(Color.parseColor("#A5D6A7"));
                    } else {
                        set.setColor(Color.parseColor("#00897B"));
                        set.setFillColor(Color.parseColor("#80CBC4"));
                    }

                    radarChart.getData().notifyDataChanged();
                    radarChart.notifyDataSetChanged();
                    // ⭐ 新增：压缩动画时间到 400 毫秒 (snappy 模式)
                    radarChart.animateXY(400, 400);
                } else {
                    // 只有第一次打开页面时，才会完整创建图表
                    RadarDataSet set = new RadarDataSet(entries, "肌肉均衡分数");
                    if (currentRadarMode == 1) {
                        set.setColor(Color.parseColor("#E53935"));
                        set.setFillColor(Color.parseColor("#EF9A9A"));
                    } else if (currentRadarMode == 2) {
                        set.setColor(Color.parseColor("#43A047"));
                        set.setFillColor(Color.parseColor("#A5D6A7"));
                    } else {
                        set.setColor(Color.parseColor("#00897B"));
                        set.setFillColor(Color.parseColor("#80CBC4"));
                    }
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
                    // ⭐ 新增：第一张图表也使用 400 毫秒动画
                    radarChart.animateXY(400, 400);
                }

                StatsAdapter adapter = new StatsAdapter(nameList, groupedMap, max1RmMap, corrected1RmMap, apeIndex);
                recyclerView.setAdapter(adapter);
            });
        });
    }
}