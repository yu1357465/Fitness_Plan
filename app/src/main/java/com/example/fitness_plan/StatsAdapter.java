package com.example.fitness_plan;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.fitness_plan.data.HistoryEntity;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class StatsAdapter extends RecyclerView.Adapter<StatsAdapter.ViewHolder> {

    private final List<String> exerciseNames;
    private final Map<String, List<HistoryEntity>> historyMap;
    private final Map<String, Double> max1RmMap;
    private final Map<String, Double> corrected1RmMap;

    // ⭐ 新增：接收用户的身体物理天赋系数
    private final float apeIndex;

    private final boolean[] expandedStates;

    public StatsAdapter(List<String> exerciseNames,
                        Map<String, List<HistoryEntity>> historyMap,
                        Map<String, Double> max1RmMap,
                        Map<String, Double> corrected1RmMap,
                        float apeIndex) {
        this.exerciseNames = exerciseNames;
        this.historyMap = historyMap;
        this.max1RmMap = max1RmMap;
        this.corrected1RmMap = corrected1RmMap;
        this.apeIndex = apeIndex;
        this.expandedStates = new boolean[exerciseNames.size()];
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_stats_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String name = exerciseNames.get(position);
        List<HistoryEntity> historyList = historyMap.get(name);

        holder.tvName.setText(name);

        boolean isExpanded = expandedStates[position];
        holder.chartContainer.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
        holder.arrow.setRotation(isExpanded ? 180f : 0f);

        holder.itemView.setOnClickListener(v -> {
            expandedStates[position] = !isExpanded;
            notifyItemChanged(position);
        });

        if (isExpanded && historyList != null && !historyList.isEmpty()) {
            setupChart(holder.chart, historyList);

            Double maxRm = max1RmMap.get(name);
            StringBuilder summary = new StringBuilder("累计打卡: " + historyList.size() + " 次");

            if (maxRm != null && maxRm > 0) {
                summary.append("\n物理极限 1RM: ").append(String.format("%.1f", maxRm)).append(" kg");

                // ==========================================
                // ⭐ 核心逻辑：AI 物理天赋诊断与指导
                // ==========================================
                if (apeIndex > 1.03) {
                    // 霸王龙的反面：长臂猿 (臂展远大于身高)
                    if (name.contains("推") || name.contains("卧推")) {
                        summary.append("\n\n💡 天赋洞察：你的长臂导致推力行程极长，胸部极易借力或拉伤。如果杠铃卧推遇到瓶颈，建议果断替换为【哑铃推举】或【夹胸】。");
                    } else if (name.contains("拉") || name.contains("硬拉") || name.contains("划船")) {
                        summary.append("\n\n🔥 顶级天赋：长臂是拉力动作的绝对霸主！你的硬拉启动位置更高，行程更短，请尽情在这里享受突破重量的快感！");
                    }
                } else if (apeIndex < 0.97 && apeIndex > 0) {
                    // 短臂霸王龙 (臂展小于身高)
                    if (name.contains("推") || name.contains("卧推")) {
                        summary.append("\n\n🔥 顶级天赋：短臂推力霸主！极短的物理行程让你在卧推中拥有绝对的力学优势，非常适合冲击大重量！");
                    } else if (name.contains("拉") || name.contains("硬拉")) {
                        summary.append("\n\n💡 天赋洞察：短臂导致你硬拉时必须蹲得极低，下背部物理剪切力极大。建议在深蹲架上【垫高杠铃】进行半程硬拉，或替换为【罗马尼亚硬拉】。");
                    }
                } else if (apeIndex >= 0.97 && apeIndex <= 1.03) {
                    // 标准人类比例
                    summary.append("\n\n💡 天赋洞察：极其均衡的黄金比例，在各项经典复合动作中均不存在明显的物理短板，保持均衡发展！");
                }
            }
            holder.tvSummary.setText(summary.toString());
        }
    }

    private void setupChart(LineChart chart, List<HistoryEntity> historyList) {
        List<Entry> entries = new ArrayList<>();
        int validIndex = 0;
        for (int i = historyList.size() - 1; i >= 0; i--) {
            HistoryEntity item = historyList.get(i);
            if (item.reps > 0 && item.reps <= 15) {
                float oneRm = (float) (item.weight * (36.0 / (37.0 - item.reps)));
                entries.add(new Entry(validIndex++, oneRm));
            }
        }

        LineDataSet dataSet = new LineDataSet(entries, "极限力量趋势 1RM (kg)");
        dataSet.setColor(Color.parseColor("#4CAF50"));
        dataSet.setLineWidth(2f);
        dataSet.setCircleColor(Color.parseColor("#388E3C"));
        dataSet.setCircleRadius(4f);
        dataSet.setDrawValues(true);
        dataSet.setValueTextSize(10f);
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        dataSet.setDrawFilled(true);
        dataSet.setFillColor(Color.parseColor("#C8E6C9"));

        LineData lineData = new LineData(dataSet);
        chart.setData(lineData);

        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);

        xAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                int index = (int) value;
                return "T" + (index + 1);
            }
        });

        chart.getAxisRight().setEnabled(false);
        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(false);
        chart.setVisibleXRangeMaximum(10);
        chart.setDragEnabled(true);
        chart.invalidate();
    }

    @Override
    public int getItemCount() {
        return exerciseNames.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvSummary;
        ImageView arrow;
        LinearLayout chartContainer;
        LineChart chart;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvExerciseName);
            arrow = itemView.findViewById(R.id.ivArrow);
            chartContainer = itemView.findViewById(R.id.chartContainer);
            chart = itemView.findViewById(R.id.itemLineChart);
            tvSummary = itemView.findViewById(R.id.tvSummary);
        }
    }
}