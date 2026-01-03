package com.example.fitness_plan;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.fitness_plan.data.HistoryEntity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class HistoryExpandableAdapter extends BaseExpandableListAdapter {

    private final Context context;
    private final List<Long> dateKeys; // 父级数据 (时间戳)
    private final Map<Long, List<HistoryEntity>> groupedData; // 子级数据
    private final boolean isLbsMode;
    private final OnHistoryActionListener actionListener; // 统一变量名

    // 回调接口：支持编辑、删单条、删整天
    public interface OnHistoryActionListener {
        void onEditHistory(HistoryEntity history);
        void onDeleteHistory(HistoryEntity history); // 删单条
        void onDeleteDayGroup(long dateTimestamp);   // 【新增】删整天
    }

    public HistoryExpandableAdapter(Context context, List<Long> dateKeys,
                                    Map<Long, List<HistoryEntity>> groupedData,
                                    boolean isLbsMode,
                                    OnHistoryActionListener listener) {
        this.context = context;
        this.dateKeys = dateKeys;
        this.groupedData = groupedData;
        this.isLbsMode = isLbsMode;
        this.actionListener = listener;
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
        return dateKeys.get(groupPosition);
    }

    @Override
    public long getChildId(int groupPosition, int childPosition) {
        return groupedData.get(dateKeys.get(groupPosition)).get(childPosition).id;
    }

    @Override
    public boolean hasStableIds() {
        return true; // 建议设为 true，减少列表闪烁
    }

    // =========================================================
    //  【核心修改】组视图 (Group View) - 适配圆角卡片 + 箭头动画
    // =========================================================
    @Override
    public View getGroupView(int groupPosition, boolean isExpanded, View convertView, ViewGroup parent) {
        if (convertView == null) {
            LayoutInflater inflater = LayoutInflater.from(context);
            // 使用新的 item_history_group.xml
            convertView = inflater.inflate(R.layout.item_history_group, parent, false);
        }

        long dateKey = dateKeys.get(groupPosition);

        // 绑定控件 (对应 item_history_group.xml 的 ID)
        TextView tvDate = convertView.findViewById(R.id.tvDateGroup);
        ImageView btnDeleteGroup = convertView.findViewById(R.id.btnDeleteDayGroup);
        ImageView ivArrow = convertView.findViewById(R.id.ivExpandArrow);

        // 1. 格式化日期：2023-10-27 (周五)
        // 这种格式比 "xxxx年xx月" 更适合卡片风格
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd (EEEE)", Locale.getDefault());
        tvDate.setText(sdf.format(new Date(dateKey)));

        // 2. 箭头旋转动画 (与 Plan 列表保持一致)
        if (isExpanded) {
            ivArrow.setRotation(180f); // 向上
        } else {
            ivArrow.setRotation(0f);   // 向下
        }

        // 3. 删除整天按钮逻辑
        btnDeleteGroup.setFocusable(false); // 防止抢占点击焦点
        btnDeleteGroup.setOnClickListener(v -> {
            if (actionListener != null) {
                actionListener.onDeleteDayGroup(dateKey);
            }
        });

        return convertView;
    }

    // =========================================================
    //  子视图 (Child View) - 训练详情
    // =========================================================
    @Override
    public View getChildView(int groupPosition, int childPosition, boolean isLastChild, View convertView, ViewGroup parent) {
        HistoryEntity item = (HistoryEntity) getChild(groupPosition, childPosition);

        if (convertView == null) {
            LayoutInflater inflater = LayoutInflater.from(context);
            // 如果你没有重写子项布局，继续用 item_history_child
            convertView = inflater.inflate(R.layout.item_history_child, parent, false);
        }

        // 1. 动作名称
        TextView tvName = convertView.findViewById(R.id.tvHistoryName);
        tvName.setText(item.name);

        // 2. 数据 (重量/组数)
        TextView tvData = convertView.findViewById(R.id.tvHistoryData);
        // 保留一位小数，更整洁
        String weightStr = isLbsMode
                ? String.format(Locale.getDefault(), "%.1f lbs", item.weight * 2.20462)
                : String.format(Locale.getDefault(), "%.1f kg", item.weight);
        tvData.setText(weightStr + " × " + item.sets + "组 × " + item.reps + "次");

        // 3. 计划/训练日标题 (Context)
        TextView tvWorkoutTitle = convertView.findViewById(R.id.tvHistoryWorkoutTitle); // 确保 xml 里有这个 ID
        // 如果找不到 tvHistoryWorkoutTitle (比如你用了旧布局)，请注释掉下面几行
        if (tvWorkoutTitle != null) {
            if (item.workoutTitle != null && !item.workoutTitle.isEmpty()) {
                tvWorkoutTitle.setVisibility(View.VISIBLE);
                tvWorkoutTitle.setText(item.workoutTitle);
            } else {
                tvWorkoutTitle.setVisibility(View.GONE); // 没有计划名就隐藏，显得干净
            }
        }

        // 4. 交互事件
        // 点击编辑
        convertView.setOnClickListener(v -> {
            if (actionListener != null) actionListener.onEditHistory(item);
        });

        // 长按删除 (保持原有逻辑，或者你可以像 Plan 列表一样加个垃圾桶按钮)
        convertView.setOnLongClickListener(v -> {
            if (actionListener != null) actionListener.onDeleteHistory(item);
            return true;
        });

        return convertView;
    }

    @Override
    public boolean isChildSelectable(int groupPosition, int childPosition) {
        return true;
    }
}