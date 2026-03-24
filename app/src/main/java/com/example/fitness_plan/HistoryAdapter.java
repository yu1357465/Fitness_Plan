package com.example.fitness_plan;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.fitness_plan.data.AppDatabase;
import com.example.fitness_plan.data.EntityNameCache;
import com.example.fitness_plan.data.ExerciseBaseEntity;
import com.example.fitness_plan.data.HistoryEntity;
import com.example.fitness_plan.data.HistorySession;
import com.example.fitness_plan.data.WorkoutDao;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder> {

    private final Context context;
    private List<HistorySession> sessionList = new ArrayList<>();
    private final SparseBooleanArray expandedState = new SparseBooleanArray();

    // ⭐ 核心新增：在 Adapter 内部维护一个分类字典缓存
    private final Map<Long, String> categoryCache = new HashMap<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface OnHistoryItemClickListener {
        void onHistoryItemLongClick(HistoryEntity entity);
    }
    private final OnHistoryItemClickListener itemClickListener;

    public HistoryAdapter(Context context, List<HistoryEntity> rawList, OnHistoryItemClickListener listener) {
        this.context = context;
        this.itemClickListener = listener;
        if (rawList != null && !rawList.isEmpty()) {
            setData(rawList);
        }
    }

    public void setData(List<HistoryEntity> rawList) {
        if (rawList == null || rawList.isEmpty()) {
            this.sessionList.clear();
            notifyDataSetChanged();
            return;
        }

        // ⭐ 核心机制：异步查询数据库，补齐所有的 category 字典
        executor.execute(() -> {
            WorkoutDao dao = AppDatabase.getDatabase(context).workoutDao();
            List<ExerciseBaseEntity> bases = dao.getAllExerciseBases();
            for (ExerciseBaseEntity b : bases) {
                categoryCache.put(b.baseId, b.category);
            }

            // 数据分组逻辑
            List<HistorySession> newSessionList = new ArrayList<>();
            HistorySession currentSession = null;

            for (HistoryEntity entity : rawList) {
                if (currentSession == null || !entity.dateStr.equals(currentSession.dateStr)) {
                    currentSession = new HistorySession();
                    currentSession.dateStr = entity.dateStr;
                    currentSession.workoutName = entity.workoutName;
                    currentSession.exercises = new ArrayList<>();
                    newSessionList.add(currentSession);
                }
                currentSession.exercises.add(entity);
            }

            // 切换回主线程刷新 UI
            mainHandler.post(() -> {
                this.sessionList.clear();
                this.sessionList.addAll(newSessionList);
                if (!sessionList.isEmpty()) expandedState.put(0, true);
                notifyDataSetChanged();
            });
        });
    }

    @NonNull
    @Override
    public HistoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_history_group, parent, false);
        return new HistoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull HistoryViewHolder holder, int position) {
        HistorySession session = sessionList.get(position);

        holder.tvDate.setText(session.dateStr);
        int count = (session.exercises != null) ? session.exercises.size() : 0;
        holder.tvSubInfo.setText(session.workoutName + " · " + count + "个动作");

        boolean isExpanded = expandedState.get(position, false);

        if (isExpanded) {
            holder.rvExercises.setVisibility(View.VISIBLE);
            holder.divider.setVisibility(View.VISIBLE);
            holder.ivArrow.setRotation(180f);

            // 传入填充好的字典
            InnerExerciseAdapter innerAdapter = new InnerExerciseAdapter(session.exercises, itemClickListener, categoryCache);
            holder.rvExercises.setLayoutManager(new LinearLayoutManager(context));
            holder.rvExercises.setAdapter(innerAdapter);
        } else {
            holder.rvExercises.setVisibility(View.GONE);
            holder.divider.setVisibility(View.GONE);
            holder.ivArrow.setRotation(0f);
        }

        holder.layoutHeader.setOnClickListener(v -> {
            boolean current = expandedState.get(position, false);
            expandedState.put(position, !current);
            notifyItemChanged(position);
        });
    }

    @Override
    public int getItemCount() {
        return sessionList.size();
    }

    static class HistoryViewHolder extends RecyclerView.ViewHolder {
        TextView tvDate, tvSubInfo;
        ImageView ivArrow;
        View layoutHeader, divider;
        RecyclerView rvExercises;

        HistoryViewHolder(View itemView) {
            super(itemView);
            tvDate = itemView.findViewById(R.id.tvDate);
            tvSubInfo = itemView.findViewById(R.id.tvSubInfo);
            ivArrow = itemView.findViewById(R.id.ivExpandArrow);
            layoutHeader = itemView.findViewById(R.id.layoutHeader);
            divider = itemView.findViewById(R.id.dividerLine);
            rvExercises = itemView.findViewById(R.id.rvHistoryExercises);
        }
    }

    // =========================================================
    //  Inner Adapter (子列表适配器)
    // =========================================================
    private static class InnerExerciseAdapter extends RecyclerView.Adapter<InnerExerciseAdapter.ChildViewHolder> {
        private final List<HistoryEntity> exercises;
        private final OnHistoryItemClickListener listener;
        private final Map<Long, String> categoryCache;

        InnerExerciseAdapter(List<HistoryEntity> exercises, OnHistoryItemClickListener listener, Map<Long, String> categoryCache) {
            this.exercises = exercises != null ? exercises : new ArrayList<>();
            this.listener = listener;
            this.categoryCache = categoryCache;
        }

        @NonNull
        @Override
        public ChildViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_history_child, parent, false);
            return new ChildViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ChildViewHolder holder, int position) {
            HistoryEntity ex = exercises.get(position);

            EntityNameCache cache = EntityNameCache.getInstance();
            String exerciseName = cache.getExerciseName(ex.baseId);

            holder.tvName.setText(exerciseName);

            // ==========================================
            // ⭐ 核心修复：统一为 "重量 | 组数 | 次数" 格式，消除视觉跳跃
            // 如果是整数则抹除小数点后缀，更加清爽
            // ==========================================
            String weightStr = (ex.weight % 1 == 0) ? String.valueOf((int)ex.weight) : String.valueOf(ex.weight);
            holder.tvDetails.setText(weightStr + " kg  |  " + ex.sets + " 组  |  " + ex.reps + " 次");

            // ⭐ 核心绘制：彩色分类徽章
            if (holder.tvCategoryBadge != null) {
                String rawCategory = categoryCache.get(ex.baseId);
                if (rawCategory == null) rawCategory = "Other";

                String displayName;
                int badgeColor;

                switch (rawCategory) {
                    case "Chest": displayName = "胸部"; badgeColor = Color.parseColor("#1E88E5"); break;
                    case "Back": displayName = "背部"; badgeColor = Color.parseColor("#43A047"); break;
                    case "Legs&Glutes": displayName = "腿臀"; badgeColor = Color.parseColor("#F4511E"); break;
                    case "Shoulders": displayName = "肩部"; badgeColor = Color.parseColor("#8E24AA"); break;
                    case "Arms": displayName = "手臂"; badgeColor = Color.parseColor("#E53935"); break;
                    case "Core": displayName = "核心"; badgeColor = Color.parseColor("#FDD835");
                        holder.tvCategoryBadge.setTextColor(Color.parseColor("#424242")); break;
                    default: displayName = "其他"; badgeColor = Color.parseColor("#9E9E9E");
                        holder.tvCategoryBadge.setTextColor(Color.WHITE); break;
                }

                if (!rawCategory.equals("Core")) holder.tvCategoryBadge.setTextColor(Color.WHITE);
                holder.tvCategoryBadge.setText(displayName);

                GradientDrawable shape = new GradientDrawable();
                shape.setShape(GradientDrawable.RECTANGLE);
                shape.setCornerRadius(12f * holder.itemView.getContext().getResources().getDisplayMetrics().density);
                shape.setColor(badgeColor);
                holder.tvCategoryBadge.setBackground(shape);
            }

            holder.itemView.setOnLongClickListener(v -> {
                if (listener != null) listener.onHistoryItemLongClick(ex);
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return exercises.size();
        }

        static class ChildViewHolder extends RecyclerView.ViewHolder {
            TextView tvName, tvDetails, tvCategoryBadge;
            ChildViewHolder(View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tvExerciseName);
                tvDetails = itemView.findViewById(R.id.tvExerciseDetails);
                tvCategoryBadge = itemView.findViewById(R.id.tvCategoryBadge);
            }
        }
    }
}