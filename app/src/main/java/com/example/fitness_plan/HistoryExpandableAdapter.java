package com.example.fitness_plan;

import android.content.Context;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.TextView;

import androidx.core.content.ContextCompat; // 【新增】引入兼容库

import com.example.fitness_plan.data.HistoryEntity;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class HistoryExpandableAdapter extends BaseExpandableListAdapter {

    private Context context;
    private List<Long> dateKeys;
    private Map<Long, List<HistoryEntity>> groupedData;
    private boolean isLbsMode;
    private OnHistoryActionListener listener;

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
        return groupedData.get(dateKeys.get(groupPosition)).size();
    }

    @Override
    public Object getGroup(int groupPosition) {
        return dateKeys.get(groupPosition);
    }

    @Override
    public Object getChild(int groupPosition, int childPosition) {
        return groupedData.get(dateKeys.get(groupPosition)).get(childPosition);
    }

    @Override
    public long getGroupId(int groupPosition) {
        return dateKeys.get(groupPosition);
    }

    @Override
    public long getChildId(int groupPosition, int childPosition) {
        return groupedData.get(dateKeys.get(groupPosition)).get(childPosition).id;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    @Override
    public View getGroupView(int groupPosition, boolean isExpanded, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(android.R.layout.simple_expandable_list_item_1, parent, false);
            convertView.setPadding(60, 48, 40, 24);
            // 【修复 1】使用 ContextCompat 获取颜色，消除过时警告
            convertView.setBackgroundColor(ContextCompat.getColor(context, R.color.flat_background));
        }

        TextView tvTitle = convertView.findViewById(android.R.id.text1);
        tvTitle.setTypeface(null, Typeface.BOLD);
        tvTitle.setTextSize(18);

        // 【修复 2】使用 ContextCompat 获取颜色
        tvTitle.setTextColor(ContextCompat.getColor(context, R.color.flat_primary));

        long dateMillis = (long) getGroup(groupPosition);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault());
        String dateStr = sdf.format(dateMillis);

        String sessionTitle = "";
        List<HistoryEntity> items = groupedData.get(dateMillis);
        if (items != null && !items.isEmpty()) {
            String title = items.get(0).workoutTitle;
            if (title != null && !title.isEmpty()) {
                sessionTitle = " - " + title;
            }
        }

        tvTitle.setText(dateStr + sessionTitle);

        return convertView;
    }

    @Override
    public View getChildView(int groupPosition, int childPosition, boolean isLastChild, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_history_child, parent, false);
        }

        HistoryEntity item = (HistoryEntity) getChild(groupPosition, childPosition);

        TextView tvName = convertView.findViewById(R.id.tvHistoryName);
        TextView tvDetails = convertView.findViewById(R.id.tvHistoryDetails);
        android.widget.ImageView btnEdit = convertView.findViewById(R.id.btnEditHistory);
        android.widget.ImageView btnDelete = convertView.findViewById(R.id.btnDeleteHistory);

        tvName.setText(item.name);

        double displayWeight = isLbsMode ? (item.weight * 2.20462) : item.weight;
        String wStr = (displayWeight % 1 == 0) ? String.valueOf((int) displayWeight) : String.format(Locale.getDefault(), "%.1f", displayWeight);
        String unit = isLbsMode ? "lbs" : "kg";

        tvDetails.setText(String.format(Locale.getDefault(), "%s %s x %d 组 x %d 次", wStr, unit, item.sets, item.reps));

        btnEdit.setOnClickListener(v -> listener.onEditHistory(item));
        btnDelete.setOnClickListener(v -> listener.onDeleteHistory(item));

        return convertView;
    }

    @Override
    public boolean isChildSelectable(int groupPosition, int childPosition) {
        return true;
    }
}