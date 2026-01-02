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
        // 【新增】删除回调
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
        ImageView btnDelete = convertView.findViewById(R.id.btnDeletePlan);

        PlanEntity plan = (PlanEntity) getGroup(groupPosition);

        TextView tvName = convertView.findViewById(R.id.tvPlanName);
        TextView tvStatus = convertView.findViewById(R.id.tvActiveStatus);

        tvName.setText(plan.planName);

        // 显示当前激活状态
        if (plan.isActive) {
            tvStatus.setVisibility(View.VISIBLE);
            tvName.setTextColor(0xFF4CAF50); // 绿色
        } else {
            tvStatus.setVisibility(View.GONE);
            tvName.setTextColor(0xFF333333); // 黑色
        }

        // 如果是当前正在用的计划，隐藏删除按钮 (防止误删)
        if (plan.isActive) {
            btnDelete.setVisibility(View.GONE);
        } else {
            btnDelete.setVisibility(View.VISIBLE);
            btnDelete.setOnClickListener(v -> {
                if (actionListener != null) actionListener.onDeletePlan(plan);
            });
        }
        return convertView;
    }

    // ================= 子级视图 (Day) =================
    @Override
    public View getChildView(int groupPosition, int childPosition, boolean isLastChild, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_plan_child, parent, false);
        }
        ImageView btnDelete = convertView.findViewById(R.id.btnDeleteDay);

        String dayName = (String) getChild(groupPosition, childPosition);
        PlanEntity plan = (PlanEntity) getGroup(groupPosition);

        TextView tvDay = convertView.findViewById(R.id.tvDayName);
        ImageView btnEdit = convertView.findViewById(R.id.btnEditDay);
        ImageView btnCopy = convertView.findViewById(R.id.btnCopyDay);

        tvDay.setText(dayName);

        // 绑定编辑点击事件
        btnEdit.setOnClickListener(v -> {
            if (actionListener != null) {
                actionListener.onEditDay(plan.planId, dayName);
            }
        });

        // 绑定复制点击事件
        btnCopy.setOnClickListener(v -> {
            if (actionListener != null) {
                actionListener.onCopyDay(plan.planId, dayName);
            }
        });

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