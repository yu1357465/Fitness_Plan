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

    // 数据源：动作名称列表
    private final List<String> exerciseNames;
    // 数据源：所有历史记录 (Key: 动作名, Value: 历史列表)
    private final Map<String, List<HistoryEntity>> historyMap;

    // 记录每个 Item 是否展开
    private final boolean[] expandedStates;

    public StatsAdapter(List<String> exerciseNames, Map<String, List<HistoryEntity>> historyMap) {
        this.exerciseNames = exerciseNames;
        this.historyMap = historyMap;
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

        // 1. 处理展开/折叠状态
        boolean isExpanded = expandedStates[position];
        holder.chartContainer.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
        holder.arrow.setRotation(isExpanded ? 180f : 0f); // 箭头旋转动画

        // 2. 点击事件
        holder.itemView.setOnClickListener(v -> {
            expandedStates[position] = !isExpanded; // 状态取反
            notifyItemChanged(position); // 刷新当前项
        });

        // 3. 只有在展开时才去画图 (优化性能)
        if (isExpanded && historyList != null && !historyList.isEmpty()) {
            setupChart(holder.chart, historyList);
            holder.tvSummary.setText("累计训练: " + historyList.size() + " 次");
        }
    }

    /**
     * 【核心】配置图表
     */
    private void setupChart(LineChart chart, List<HistoryEntity> historyList) {
        List<Entry> entries = new ArrayList<>();

        // 【关键修改】X轴使用索引 (Index) 而不是时间戳
        // i = 0 (第1次), i = 1 (第2次)...
        for (int i = 0; i < historyList.size(); i++) {
            HistoryEntity item = historyList.get(i);
            entries.add(new Entry(i, (float) item.weight));
        }

        LineDataSet dataSet = new LineDataSet(entries, "重量趋势 (kg)");
        dataSet.setColor(Color.parseColor("#4CAF50")); // 绿色线条
        dataSet.setLineWidth(2f);
        dataSet.setCircleColor(Color.parseColor("#388E3C"));
        dataSet.setCircleRadius(4f);
        dataSet.setDrawValues(true);
        dataSet.setValueTextSize(10f);
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER); // 平滑曲线
        dataSet.setDrawFilled(true);
        dataSet.setFillColor(Color.parseColor("#C8E6C9")); // 浅绿填充

        LineData lineData = new LineData(dataSet);
        chart.setData(lineData);

        // 配置 X 轴显示
        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);

        // 【关键修改】自定义 X 轴标签：显示 "T1", "T2", "T3"
        xAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                // value 是 float (0.0, 1.0)，转成整数
                int index = (int) value;
                return "T" + (index + 1); // 显示 T1, T2...
            }
        });

        chart.getAxisRight().setEnabled(false);
        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(false);
        chart.invalidate(); // 重绘
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