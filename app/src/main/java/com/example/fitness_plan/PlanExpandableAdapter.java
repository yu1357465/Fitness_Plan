package com.example.fitness_plan;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.fitness_plan.data.PlanEntity;

import java.util.List;
import java.util.Map;

public class PlanExpandableAdapter extends BaseExpandableListAdapter {

    private final Context context;
    private final List<PlanEntity> plans; // 父级数据
    private final Map<Integer, List<String>> planDaysMap; // 子级数据 (PlanId -> DayNames)
    private final OnItemActionListener actionListener; // 回调接口

    // 定义回调接口，让 Activity 处理具体的业务逻辑
    public interface OnItemActionListener {
        void onEditDay(int planId, String oldName);
        void onCopyDay(int sourcePlanId, String dayName);
        void onActivatePlan(PlanEntity plan);
        void onDeleteDay(int planId, String dayName);
        void onDeletePlan(PlanEntity plan);
    }

    public PlanExpandableAdapter(Context context, List<PlanEntity> plans, Map<Integer, List<String>> planDaysMap, OnItemActionListener listener) {
        this.context = context;
        this.plans = plans;
        this.planDaysMap = planDaysMap;
        this.actionListener = listener;
    }

    @Override
    public int getGroupCount() {
        return plans.size();
    }

    @Override
    public int getChildrenCount(int groupPosition) {
        int planId = plans.get(groupPosition).planId;
        List<String> days = planDaysMap.get(planId);
        return days != null ? days.size() : 0;
    }

    @Override
    public Object getGroup(int groupPosition) {
        return plans.get(groupPosition);
    }

    @Override
    public Object getChild(int groupPosition, int childPosition) {
        int planId = plans.get(groupPosition).planId;
        return planDaysMap.get(planId).get(childPosition);
    }

    @Override
    public long getGroupId(int groupPosition) {
        return plans.get(groupPosition).planId;
    }

    @Override
    public long getChildId(int groupPosition, int childPosition) {
        return childPosition;
    }

    @Override
    public boolean hasStableIds() {
        return false;
    }

    // ================= 父级视图 (Plan) =================
    @Override
    public View getGroupView(int groupPosition, boolean isExpanded, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_plan_group, parent, false);
        }

        PlanEntity plan = (PlanEntity) getGroup(groupPosition);

        // 绑定控件
        TextView tvName = convertView.findViewById(R.id.tvPlanName);
        // 【修改】ID 变更为 tvActiveBadge
        TextView tvBadge = convertView.findViewById(R.id.tvActiveBadge);
        ImageView btnDelete = convertView.findViewById(R.id.btnDeletePlan);
        // 【新增】箭头图标
        ImageView ivArrow = convertView.findViewById(R.id.ivExpandArrow);

        // 设置文本
        tvName.setText(plan.planName);

        // 【修改】逻辑：不再改变文字颜色，而是控制 Badge 显示
        if (plan.isActive) {
            tvBadge.setVisibility(View.VISIBLE); // 显示绿色胶囊标签
            btnDelete.setVisibility(View.GONE);  // 隐藏删除按钮
            // 保持文字黑色，看起来更整洁
            tvName.setTextColor(0xFF333333);
        } else {
            tvBadge.setVisibility(View.GONE);
            btnDelete.setVisibility(View.VISIBLE);
            tvName.setTextColor(0xFF333333);
        }

        // 【新增】箭头旋转动画
        if (isExpanded) {
            ivArrow.setRotation(180f); // 向上
        } else {
            ivArrow.setRotation(0f);   // 向下
        }

        // 删除按钮点击事件
        btnDelete.setFocusable(false); // 防止抢占列表点击焦点
        btnDelete.setOnClickListener(v -> {
            if (actionListener != null) actionListener.onDeletePlan(plan);
        });

        return convertView;
    }

    // ================= 子级视图 (Day) =================
    @Override
    public View getChildView(int groupPosition, int childPosition, boolean isLastChild, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_plan_child, parent, false);
        }

        String dayName = (String) getChild(groupPosition, childPosition);
        PlanEntity plan = (PlanEntity) getGroup(groupPosition);

        TextView tvDay = convertView.findViewById(R.id.tvDayName);
        ImageView btnEdit = convertView.findViewById(R.id.btnEditDay);
        ImageView btnCopy = convertView.findViewById(R.id.btnCopyDay);
        ImageView btnDelete = convertView.findViewById(R.id.btnDeleteDay);

        tvDay.setText(dayName);

        // 绑定点击事件
        btnEdit.setFocusable(false);
        btnEdit.setOnClickListener(v -> {
            if (actionListener != null) actionListener.onEditDay(plan.planId, dayName);
        });

        btnCopy.setFocusable(false);
        btnCopy.setOnClickListener(v -> {
            if (actionListener != null) actionListener.onCopyDay(plan.planId, dayName);
        });

        btnDelete.setFocusable(false);
        btnDelete.setOnClickListener(v -> {
            if (actionListener != null) actionListener.onDeleteDay(plan.planId, dayName);
        });

        return convertView;
    }

    @Override
    public boolean isChildSelectable(int groupPosition, int childPosition) {
        return true;
    }
}