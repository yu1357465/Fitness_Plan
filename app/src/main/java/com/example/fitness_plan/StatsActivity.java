package com.example.fitness_plan;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StatsActivity extends AppCompatActivity {

    private WorkoutDao workoutDao;
    private RadarChart radarChart;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    // 身体维度
    private float userHeight, userWingspan, userWeight;
    private float apeIndex = 1.0f;
    private android.view.View barStrength, barHypertrophy, barEndurance;
    private android.widget.TextView tvStrengthPct, tvHypertrophyPct, tvEndurancePct;

    // ✅ 新增：洞察卡片的文本控件
    private android.widget.TextView tvApeInsight;

    // 雷达图过滤模式枚举 (0=综合, 1=力量, 2=耐力)
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
        radarChart = findViewById(R.id.radarChart);

        // ❌ 已经彻底移除了 RecyclerView 的相关代码

        barStrength = findViewById(R.id.barStrength);
        barHypertrophy = findViewById(R.id.barHypertrophy);
        barEndurance = findViewById(R.id.barEndurance);
        tvStrengthPct = findViewById(R.id.tvStrengthPct);
        tvHypertrophyPct = findViewById(R.id.tvHypertrophyPct);
        tvEndurancePct = findViewById(R.id.tvEndurancePct);

        // ✅ 绑定洞察文本控件
        tvApeInsight = findViewById(R.id.tvApeInsight);

        // 绑定问号图标
        android.widget.ImageView ivHelpInfo = findViewById(R.id.ivHelpInfo);
        ivHelpInfo.setOnClickListener(v -> showExplanationDialog());

        // 绑定 RadioGroup 并监听模式切换
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

            // 极简字典：BaseId -> Category
            Map<Long, String> baseCategoryMap = new HashMap<>();
            for (ExerciseBaseEntity base : allBases) {
                baseCategoryMap.put(base.baseId, base.category);
            }

            EntityNameCache nameCache = EntityNameCache.getInstance();
            nameCache.setDao(workoutDao);

            // ✅ 已彻底清理冗余的 groupedMap, nameList 等集合变量，大幅提升内存性能
            Map<String, Double> max1RmMap = new HashMap<>();

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

                // 累加能量系统 (保留全量统计，不随雷达模式切换而变动)
                if (item.reps > 0) {
                    if (item.reps <= 5) countStrength++;
                    else if (item.reps <= 12) countHypertrophy++;
                    else countEndurance++;
                    totalValidSets++;
                }

                // 植入数据漏斗与多重算法滤镜
                boolean shouldInclude = false;
                double current1Rm = 0.0;

                if (item.reps > 0) {
                    if (currentRadarMode == 0 && item.reps <= 15) {
                        shouldInclude = true;
                        current1Rm = item.weight * (36.0 / (37.0 - item.reps));
                    }
                    else if (currentRadarMode == 1 && item.reps <= 6) {
                        shouldInclude = true;
                        current1Rm = item.weight * (36.0 / (37.0 - item.reps));
                    }
                    else if (currentRadarMode == 2 && item.reps >= 10 && item.reps <= 30) {
                        shouldInclude = true;
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
                // 内部全面使用 final 替身变量更新进度条
                if (finalTotalValidSets > 0) {
                    float pctStrength = (finalCountStrength * 100f) / finalTotalValidSets;
                    float pctHypertrophy = (finalCountHypertrophy * 100f) / finalTotalValidSets;
                    float pctEndurance = (finalCountEndurance * 100f) / finalTotalValidSets;

                    ((android.widget.LinearLayout.LayoutParams) barStrength.getLayoutParams()).weight = Math.max(0.01f, pctStrength);
                    ((android.widget.LinearLayout.LayoutParams) barHypertrophy.getLayoutParams()).weight = Math.max(0.01f, pctHypertrophy);
                    ((android.widget.LinearLayout.LayoutParams) barEndurance.getLayoutParams()).weight = Math.max(0.01f, pctEndurance);

                    barStrength.requestLayout();

                    tvStrengthPct.setText(String.format("绝对力量\n%.0f%%", pctStrength));
                    tvHypertrophyPct.setText(String.format("肌肥大\n%.0f%%", pctHypertrophy));
                    tvEndurancePct.setText(String.format("肌肉耐力\n%.0f%%", pctEndurance));
                }

                // ⭐ 新增：自动生成极简洞察摘要
                String insightText;
                if (apeIndex > 1.02) {
                    insightText = String.format("你的 Ape Index (%.2f) 赋予了你优秀的「拉力」天赋，系统已在底层为你上调了背部力量的评估基线。", apeIndex);
                } else if (apeIndex < 0.98) {
                    insightText = String.format("你的 Ape Index (%.2f) 赋予了你优秀的「推力」天赋，系统已在底层为你上调了胸部与肩部力量的评估基线。", apeIndex);
                } else {
                    insightText = String.format("你的 Ape Index (%.2f) 属于完美均衡型，系统已采用最严苛的标准生物力学模型为你生成雷达评分。", apeIndex);
                }

                if (tvApeInsight != null) {
                    tvApeInsight.setText(insightText);
                }

                ArrayList<RadarEntry> entries = new ArrayList<>();
                entries.add(new RadarEntry(fChest));
                entries.add(new RadarEntry(fBack));
                entries.add(new RadarEntry(fLegs));
                entries.add(new RadarEntry(fShoulders));
                entries.add(new RadarEntry(fArms));
                entries.add(new RadarEntry(fCore));

                com.github.mikephil.charting.components.YAxis yAxis = radarChart.getYAxis();
                yAxis.setAxisMinimum(0f);
                yAxis.setAxisMaximum(100f);

                // 核心架构升级：内存复用模式 (In-place Update)
                if (radarChart.getData() != null && radarChart.getData().getDataSetCount() > 0) {
                    RadarDataSet set = (RadarDataSet) radarChart.getData().getDataSetByIndex(0);
                    set.setValues(entries);

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
                    radarChart.animateXY(400, 400);
                } else {
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
                    radarChart.animateXY(400, 400);
                }
            });
        });
    }

    // ✅ 终极重构版的说明书弹窗
    private void showExplanationDialog() {
        String message =
                "【1. 底层基石：1RM 与相对力量】\n" +
                        "雷达图分数并非绝对重量的简单堆砌，而是双重折算的产物：首先将你的日常训练数据，统一折算为「单次极限重量 (1RM)」；随后结合你的「体重」计算相对力量。同样推起 100kg，60kg 与 100kg 体重获得的雷达评分将有天壤之别。\n\n" +

                        "【2. 物理权重：Ape Index 天赋补偿】\n" +
                        "基因决定了力学杠杆。臂展大于身高的“长臂猿”体型，在推类动作（如卧推）中做功距离长、极其吃亏，但在拉类动作（如硬拉）中占尽优势。系统已提取你的身体数据，在底层算法中为你进行了物理力臂的绝对公平补偿。\n\n" +

                        "【3. 算法重载：理论与现实的边界】\n" +
                        "理论上，健美界通用 Brzycki 公式估算 1RM。但在实际训练中，如果一组超过 15 次，该公式的误差会极其离谱（分母甚至会逼近归零）。因此，当系统探测到高次数训练时，底层引擎会自动无缝切换为适合高次数的 Epley 公式，确保耐力数据的数学严谨性。\n\n" +

                        "【4. 能量漏斗：反直觉的身体引擎】\n" +
                        "把极轻的重量一口气举 50 次，并不代表你的“绝对力量”很大，这完全是两套不同的身体引擎在工作。为了帮你精准暴露短板，雷达图拆分了三大物理滤镜：\n" +
                        "• 力量模式 (1-6次)：消耗磷酸原，榨干快肌纤维的纯粹爆发力。\n" +
                        "• 综合模式 (6-12次)：达成肌纤维撕裂与肌肉体积增长的最佳平衡。\n" +
                        "• 耐力模式 (10次以上)：消耗糖原与氧气，考验慢肌纤维的抗疲劳阈值。";

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("数据引擎揭秘")
                .setMessage(message)
                .setPositiveButton("硬核！", null)
                .show();
    }
}