package com.example.fitness_plan.ui.library;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.fitness_plan.R;
import com.example.fitness_plan.data.ExerciseBaseEntity;

import java.util.ArrayList;
import java.util.List;

public class LibraryAdapter extends RecyclerView.Adapter<LibraryAdapter.LibraryViewHolder> {

    private List<ExerciseBaseEntity> list = new ArrayList<>();
    private final OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(ExerciseBaseEntity entity);
        void onItemLongClick(ExerciseBaseEntity entity);
    }

    public LibraryAdapter(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setData(List<ExerciseBaseEntity> newList) {
        this.list = newList;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public LibraryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // ⭐ 替换为我们刚刚手写的精美卡片布局
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_library_card, parent, false);
        return new LibraryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull LibraryViewHolder holder, int position) {
        ExerciseBaseEntity item = list.get(position);

        // 1. 设置名字
        holder.tvName.setText(item.name);

        // 2. 提取底层英文 Category
        String rawCategory = item.category != null ? item.category : "Other";

        // 3. ⭐ 核心渲染：翻译并涂色
        String displayName;
        int badgeColor;

        switch (rawCategory) {
            case "Chest":
                displayName = "胸部";
                badgeColor = Color.parseColor("#1E88E5"); // 战神蓝
                break;
            case "Back":
                displayName = "背部";
                badgeColor = Color.parseColor("#43A047"); // 宽厚绿
                break;
            case "Legs&Glutes":
                displayName = "腿臀";
                badgeColor = Color.parseColor("#F4511E"); // 爆发橙
                break;
            case "Shoulders":
                displayName = "肩部";
                badgeColor = Color.parseColor("#8E24AA"); // 倒三角紫
                break;
            case "Arms":
                displayName = "手臂";
                badgeColor = Color.parseColor("#E53935"); // 充血红
                break;
            case "Core":
                displayName = "核心";
                badgeColor = Color.parseColor("#FDD835"); // 核心金
                holder.tvCategoryBadge.setTextColor(Color.parseColor("#424242")); // 金底黑字更清楚
                break;
            default:
                displayName = "其他";
                badgeColor = Color.parseColor("#9E9E9E"); // 边缘灰
                holder.tvCategoryBadge.setTextColor(Color.WHITE); // 恢复白字
                break;
        }

        // 如果不是核心金，统一用白字
        if (!rawCategory.equals("Core")) {
            holder.tvCategoryBadge.setTextColor(Color.WHITE);
        }

        holder.tvCategoryBadge.setText(displayName);

        // ⭐ 无中生有：用纯代码捏出一个带有 16dp 圆角的纯色背景，贴到 TextView 上
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setCornerRadius(16f * holder.itemView.getContext().getResources().getDisplayMetrics().density); // 动态转换为像素
        shape.setColor(badgeColor);
        holder.tvCategoryBadge.setBackground(shape);

        // 4. 绑定点击事件
        holder.itemView.setOnClickListener(v -> listener.onItemClick(item));
        holder.itemView.setOnLongClickListener(v -> {
            listener.onItemLongClick(item);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    static class LibraryViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvCategoryBadge;

        public LibraryViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvName);
            tvCategoryBadge = itemView.findViewById(R.id.tvCategoryBadge);
        }
    }
}