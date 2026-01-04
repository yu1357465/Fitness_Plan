package com.example.fitness_plan;

import android.content.Context;
import android.graphics.Color;
import android.util.SparseBooleanArray;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.fitness_plan.data.PlanEntity;
import com.google.android.material.card.MaterialCardView;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class PlanAdapter extends RecyclerView.Adapter<PlanAdapter.PlanViewHolder> {

    private final Context context;
    private final List<PlanEntity> plans;
    private final Map<Integer, List<String>> planDaysMap;
    private final OnPlanActionListener listener;
    private ItemTouchHelper itemTouchHelper;

    private final SparseBooleanArray expandedState = new SparseBooleanArray();

    public interface OnPlanActionListener {
        void onActivatePlan(PlanEntity plan);
        void onDeletePlan(PlanEntity plan);

        // 【新增】计划重命名
        void onRenamePlan(PlanEntity plan);

        void onPlanOrderChanged();
        void onAddDay(PlanEntity plan);
        void onEditDay(int planId, String oldName);
        void onCopyDay(int planId, String dayName);
        void onDeleteDay(int planId, String dayName);
        void onDayOrderChanged(int planId);
    }

    public PlanAdapter(Context context, List<PlanEntity> plans, Map<Integer, List<String>> planDaysMap, OnPlanActionListener listener) {
        this.context = context;
        this.plans = plans;
        this.planDaysMap = planDaysMap;
        this.listener = listener;

        for (int i = 0; i < plans.size(); i++) {
            if (plans.get(i).isActive) expandedState.put(i, true);
            else expandedState.put(i, false);
        }
    }

    public void setItemTouchHelper(ItemTouchHelper helper) {
        this.itemTouchHelper = helper;
    }

    @NonNull
    @Override
    public PlanViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_plan_group, parent, false);
        return new PlanViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PlanViewHolder holder, int position) {
        PlanEntity plan = plans.get(position);
        holder.tvPlanName.setText(plan.planName);

        boolean isExpanded = expandedState.get(position, false);

        // ============================================================
        // 【新增】全局强制去阴影 (为了彻底的扁平化)
        // ============================================================
        holder.cardGroup.setCardElevation(0f);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            holder.cardGroup.setElevation(0f);
            holder.cardGroup.setStateListAnimator(null); // 去除点击时的浮起阴影
        }
        // ============================================================

        if (plan.isActive) {
            // >>> 激活状态：保留绿色边框，作为指示
            holder.ivStatus.setVisibility(View.VISIBLE);
            holder.cardGroup.setStrokeColor(Color.parseColor("#4DB6AC"));
            holder.cardGroup.setStrokeWidth(4);
            holder.tvPlanName.setTextColor(Color.parseColor("#4DB6AC"));
        } else {
            // >>> 普通状态：强制无边框 (修改处)
            holder.ivStatus.setVisibility(View.GONE);
            holder.cardGroup.setStrokeColor(Color.TRANSPARENT); // 颜色透明
            holder.cardGroup.setStrokeWidth(0);                 // 宽度为0
            holder.tvPlanName.setTextColor(Color.parseColor("#333333"));
        }

        // --- 内部列表逻辑 (保持不变) ---
        List<String> days = planDaysMap.get(plan.planId);
        if (isExpanded && days != null && !days.isEmpty()) {
            holder.rvDays.setVisibility(View.VISIBLE);
            holder.divider.setVisibility(View.VISIBLE);
            holder.ivArrow.setRotation(180f);

            DayAdapter dayAdapter = new DayAdapter(context, plan.planId, days, new DayAdapter.OnDayActionListener() {
                @Override public void onEditDay(int pid, String old) { listener.onEditDay(pid, old); }
                @Override public void onCopyDay(int pid, String day) { listener.onCopyDay(pid, day); }
                @Override public void onDeleteDay(int pid, String day) { listener.onDeleteDay(pid, day); }
                @Override public void onDayOrderChanged(int pid) { listener.onDayOrderChanged(pid); }
            });
            holder.rvDays.setLayoutManager(new LinearLayoutManager(context));
            holder.rvDays.setAdapter(dayAdapter);

            ItemTouchHelper.Callback callback = new ItemTouchHelper.Callback() {
                @Override public int getMovementFlags(@NonNull RecyclerView r, @NonNull RecyclerView.ViewHolder v) {
                    return makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0);
                }
                @Override public boolean onMove(@NonNull RecyclerView r, @NonNull RecyclerView.ViewHolder src, @NonNull RecyclerView.ViewHolder tgt) {
                    dayAdapter.onItemMove(src.getAdapterPosition(), tgt.getAdapterPosition());
                    return true;
                }
                @Override public void onSwiped(@NonNull RecyclerView.ViewHolder v, int d) {}
                @Override public boolean isLongPressDragEnabled() { return false; }
            };
            ItemTouchHelper dayHelper = new ItemTouchHelper(callback);
            dayHelper.attachToRecyclerView(holder.rvDays);
            dayAdapter.setItemTouchHelper(dayHelper);

        } else {
            holder.rvDays.setVisibility(View.GONE);
            holder.divider.setVisibility(View.GONE);
            holder.ivArrow.setRotation(0f);
        }

        // 交互逻辑 (保持不变)
        holder.layoutHeader.setOnClickListener(v -> {
            boolean current = expandedState.get(position, false);
            expandedState.put(position, !current);
            notifyItemChanged(position);
        });

        holder.layoutHeader.setOnLongClickListener(v -> {
            holder.popupAnchor.setX(holder.lastTouchX);
            holder.popupAnchor.setY(holder.lastTouchY);
            showPopupMenu(holder.popupAnchor, plan);
            return true;
        });

        holder.ivAddDay.setOnClickListener(v -> listener.onAddDay(plan));

        holder.layoutHeader.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                holder.lastTouchX = event.getX();
                holder.lastTouchY = event.getY();
            }
            return false;
        });

        holder.ivDragHandle.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN && itemTouchHelper != null) {
                itemTouchHelper.startDrag(holder);
            }
            return false;
        });
    }

    private void showPopupMenu(View anchor, PlanEntity plan) {
        PopupMenu popup = new PopupMenu(context, anchor, Gravity.NO_GRAVITY);

        // 【新增】修改名称选项
        popup.getMenu().add("修改名称");
        popup.getMenu().add("启用此计划");
        popup.getMenu().add("删除计划");

        popup.setOnMenuItemClickListener(item -> {
            String title = item.getTitle().toString();
            if (title.equals("修改名称")) {
                listener.onRenamePlan(plan);
            } else if (title.equals("删除计划")) {
                listener.onDeletePlan(plan);
            } else if (title.equals("启用此计划")) {
                listener.onActivatePlan(plan);
            }
            return true;
        });
        popup.show();
    }

    @Override
    public int getItemCount() { return plans.size(); }

    public void onItemMove(int from, int to) {
        if (from < to) {
            for (int i = from; i < to; i++) Collections.swap(plans, i, i + 1);
        } else {
            for (int i = from; i > to; i--) Collections.swap(plans, i, i - 1);
        }
        boolean fromExpanded = expandedState.get(from);
        boolean toExpanded = expandedState.get(to);
        expandedState.put(from, toExpanded);
        expandedState.put(to, fromExpanded);
        notifyItemMoved(from, to);
        listener.onPlanOrderChanged();
    }

    static class PlanViewHolder extends RecyclerView.ViewHolder {
        TextView tvPlanName;
        ImageView ivStatus, ivDragHandle, ivArrow, ivAddDay;
        MaterialCardView cardGroup;
        RecyclerView rvDays;
        View divider, popupAnchor, layoutHeader;
        float lastTouchX, lastTouchY;

        PlanViewHolder(View itemView) {
            super(itemView);
            tvPlanName = itemView.findViewById(R.id.tvPlanName);
            ivStatus = itemView.findViewById(R.id.ivPlanStatus);
            ivDragHandle = itemView.findViewById(R.id.ivPlanDragHandle);
            ivArrow = itemView.findViewById(R.id.ivExpandArrow);
            ivAddDay = itemView.findViewById(R.id.ivAddDay);
            cardGroup = itemView.findViewById(R.id.cardPlanGroup);
            rvDays = itemView.findViewById(R.id.rvPlanDays);
            divider = itemView.findViewById(R.id.dividerLine);
            popupAnchor = itemView.findViewById(R.id.popupAnchor);
            layoutHeader = itemView.findViewById(R.id.layoutPlanHeader);
        }
    }
}