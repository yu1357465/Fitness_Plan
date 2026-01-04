package com.example.fitness_plan;

import android.content.Context;
import android.graphics.Color;
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
import androidx.recyclerview.widget.RecyclerView;
import java.util.Collections;
import java.util.List;

public class DayAdapter extends RecyclerView.Adapter<DayAdapter.DayViewHolder> {

    private final Context context;
    private final List<String> days;
    private final int planId;
    private final OnDayActionListener listener;
    private ItemTouchHelper itemTouchHelper; // 持有 helper 以便触发拖拽

    public interface OnDayActionListener {
        void onEditDay(int planId, String oldName);
        void onCopyDay(int planId, String dayName);
        void onDeleteDay(int planId, String dayName);
        void onDayOrderChanged(int planId);
    }

    public DayAdapter(Context context, int planId, List<String> days, OnDayActionListener listener) {
        this.context = context;
        this.planId = planId;
        this.days = days;
        this.listener = listener;
    }

    public void setItemTouchHelper(ItemTouchHelper helper) {
        this.itemTouchHelper = helper;
    }

    @NonNull
    @Override
    public DayViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_plan_child, parent, false);
        return new DayViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DayViewHolder holder, int position) {
        String dayName = days.get(position);
        holder.tvDayName.setText(dayName);

        // 1. 触摸手掌 -> 开始拖拽
        holder.ivDragHandle.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN && itemTouchHelper != null) {
                itemTouchHelper.startDrag(holder);
            }
            return false;
        });

        // 2. 长按卡片 -> 弹出菜单 (删除/重命名/复制)
        holder.itemView.setOnLongClickListener(v -> {
            showPopupMenu(holder.popupAnchor, dayName); // 在隐形锚点处弹出
            return true;
        });

        // 3. 触摸监听：记录手指位置
        holder.itemView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                holder.popupAnchor.setX(event.getX());
                holder.popupAnchor.setY(event.getY());
            }
            return false;
        });
    }

    private void showPopupMenu(View anchor, String dayName) {
        PopupMenu popup = new PopupMenu(context, anchor, Gravity.NO_GRAVITY);

        // 【修改】统一文案为 "修改名称"
        popup.getMenu().add("修改名称");
        popup.getMenu().add("复制到其他计划");
        popup.getMenu().add("删除此日");

        popup.setOnMenuItemClickListener(item -> {
            String title = item.getTitle().toString();
            // 【修改】这里判断条件也要改成 "修改名称"
            if (title.equals("修改名称")) listener.onEditDay(planId, dayName);
            else if (title.equals("复制到其他计划")) listener.onCopyDay(planId, dayName);
            else if (title.equals("删除此日")) listener.onDeleteDay(planId, dayName);
            return true;
        });
        popup.show();
    }

    @Override
    public int getItemCount() { return days.size(); }

    // 拖拽交换数据
    public void onItemMove(int fromPosition, int toPosition) {
        if (fromPosition < toPosition) {
            for (int i = fromPosition; i < toPosition; i++) Collections.swap(days, i, i + 1);
        } else {
            for (int i = fromPosition; i > toPosition; i--) Collections.swap(days, i, i - 1);
        }
        notifyItemMoved(fromPosition, toPosition);
        listener.onDayOrderChanged(planId); // 通知 Activity 更新数据库
    }

    static class DayViewHolder extends RecyclerView.ViewHolder {
        TextView tvDayName;
        ImageView ivDragHandle;
        View popupAnchor;

        DayViewHolder(View itemView) {
            super(itemView);
            tvDayName = itemView.findViewById(R.id.tvDayName);
            ivDragHandle = itemView.findViewById(R.id.ivDayDragHandle);
            popupAnchor = itemView.findViewById(R.id.popupAnchor);
        }
    }
}