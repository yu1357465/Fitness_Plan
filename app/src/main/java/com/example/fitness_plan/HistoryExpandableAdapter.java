package com.example.fitness_plan;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.TextView;

import com.example.fitness_plan.data.HistoryEntity;

import java.text.SimpleDateFormat;
import java.util.Date; // 【修复】补上了这个导入
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class HistoryExpandableAdapter extends BaseExpandableListAdapter {

    private final Context context;
    private final List<Long> dateKeys;
    private final Map<Long, List<HistoryEntity>> groupedData;
    private final boolean isLbsMode;
    private final OnHistoryActionListener listener;

    public interface OnHistoryActionListener {
        void onEditHistory(HistoryEntity history);
        void onDeleteHistory(HistoryEntity history);
    }

    public HistoryExpandableAdapter(Context context, List<Long> dateKeys, Map<Long, List<HistoryEntity>> groupedData, boolean isLbsMode, OnHistoryActionListener listener) {
        this.context = context;
        this.dateKeys = dateKeys;
        this.groupedData = groupedData;
        this.isLbsMode = isLbsMode;
        this.listener = listener;
    }

    @Override
    public int getGroupCount() {
        return dateKeys.size();
    }

    @Override
    public int getChildrenCount(int groupPosition) {
        long key = dateKeys.get(groupPosition);
        List<HistoryEntity> list = groupedData.get(key);
        return (list != null) ? list.size() : 0;
    }

    @Override
    public Object getGroup(int groupPosition) {
        return dateKeys.get(groupPosition);
    }

    @Override
    public Object getChild(int groupPosition, int childPosition) {
        long key = dateKeys.get(groupPosition);
        return groupedData.get(key).get(childPosition);
    }

    @Override
    public long getGroupId(int groupPosition) {
        return groupPosition;
    }

    @Override
    public long getChildId(int groupPosition, int childPosition) {
        return childPosition;
    }

    @Override
    public boolean hasStableIds() {
        return false;
    }

    // =========================================================
    //  组视图 (Group View) - 只显示日期和统计
    // =========================================================
    @Override
    public View getGroupView(int groupPosition, boolean isExpanded, View convertView, ViewGroup parent) {
        if (convertView == null) {
            LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            // 确保这里引用的 layout 文件名和你创建的一致
            convertView = inflater.inflate(R.layout.item_history_group, null);
        }

        long dateKey = dateKeys.get(groupPosition);
        List<HistoryEntity> dayList = groupedData.get(dateKey);
        int itemCount = (dayList != null) ? dayList.size() : 0;

        TextView tvDate = convertView.findViewById(R.id.tvHistoryGroupDate);
        TextView tvSummary = convertView.findViewById(R.id.tvHistoryGroupSummary);

        // 格式化日期：2026年1月2日 星期五
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy年MM月dd日 EEEE", Locale.getDefault());
        String dateStr = sdf.format(new Date(dateKey)); // 这里现在能找到 Date 类了

        tvDate.setText(dateStr);
        if (tvSummary != null) {
            tvSummary.setText("共完成 " + itemCount + " 个动作");
        }

        return convertView;
    }

    // =========================================================
    //  子视图 (Child View) - 显示动作详情 + 具体计划名
    // =========================================================
    @Override
    public View getChildView(int groupPosition, int childPosition, boolean isLastChild, View convertView, ViewGroup parent) {
        HistoryEntity item = (HistoryEntity) getChild(groupPosition, childPosition);

        if (convertView == null) {
            LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = inflater.inflate(R.layout.item_history_child, null);
        }

        // 1. 动作名称
        TextView tvName = convertView.findViewById(R.id.tvHistoryName);
        tvName.setText(item.name);

        // 2. 数据 (重量/组数)
        TextView tvData = convertView.findViewById(R.id.tvHistoryData);
        String weightStr = isLbsMode ? String.format(Locale.getDefault(), "%.1f lbs", item.weight * 2.20462) : String.format(Locale.getDefault(), "%.1f kg", item.weight);
        tvData.setText(weightStr + " × " + item.sets + "组 × " + item.reps + "次");

        // 3. 计划/训练日标题 (从数据库里的 workoutTitle 获取)
        TextView tvWorkoutTitle = convertView.findViewById(R.id.tvHistoryWorkoutTitle);
        if (item.workoutTitle != null && !item.workoutTitle.isEmpty()) {
            tvWorkoutTitle.setVisibility(View.VISIBLE);
            tvWorkoutTitle.setText(item.workoutTitle);
        } else {
            tvWorkoutTitle.setText("自由训练");
            // 或者 tvWorkoutTitle.setVisibility(View.GONE);
        }

        // 4. 点击事件
        convertView.setOnClickListener(v -> {
            if (listener != null) listener.onEditHistory(item);
        });

        convertView.setOnLongClickListener(v -> {
            if (listener != null) listener.onDeleteHistory(item);
            return true;
        });

        return convertView;
    }

    @Override
    public boolean isChildSelectable(int groupPosition, int childPosition) {
        return true;
    }
}