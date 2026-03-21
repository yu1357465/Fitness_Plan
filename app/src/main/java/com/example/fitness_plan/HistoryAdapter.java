package com.example.fitness_plan;

import android.content.Context;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.fitness_plan.data.EntityNameCache;
import com.example.fitness_plan.data.HistoryEntity;
import com.example.fitness_plan.data.HistorySession;

import java.util.ArrayList;
import java.util.List;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder> {

    private final Context context;
    // 适配器内部维护分组后的列表
    private List<HistorySession> sessionList = new ArrayList<>();
    // 记录展开状态
    private final SparseBooleanArray expandedState = new SparseBooleanArray();

    // ✅ 新增：长按监听器接口
    public interface OnHistoryItemClickListener {
        void onHistoryItemLongClick(HistoryEntity entity);
    }
    private final OnHistoryItemClickListener itemClickListener;

    // ✅ 修改构造函数：接收监听器
    public HistoryAdapter(Context context, List<HistoryEntity> rawList, OnHistoryItemClickListener listener) {
        this.context = context;
        this.itemClickListener = listener;
        // 初始化时如果传了数据，直接处理
        if (rawList != null && !rawList.isEmpty()) {
            setData(rawList);
        }
    }

    /**
     * 将数据库返回的扁平 List<HistoryEntity> 转换为分组的 List<HistorySession>
     */
    public void setData(List<HistoryEntity> rawList) {
        this.sessionList.clear();
        if (rawList != null && !rawList.isEmpty()) {
            // 数据分组逻辑 (前提：rawList 已经是按时间倒序排列的)
            HistorySession currentSession = null;

            for (HistoryEntity entity : rawList) {
                // 如果是新的日期，或者当前没有 Session
                if (currentSession == null || !entity.dateStr.equals(currentSession.dateStr)) {
                    // 创建新 Session
                    currentSession = new HistorySession();
                    currentSession.dateStr = entity.dateStr;
                    currentSession.workoutName = entity.workoutName;
                    currentSession.exercises = new ArrayList<>();

                    this.sessionList.add(currentSession);
                }

                // 将记录添加到当前 Session
                currentSession.exercises.add(entity);
            }
        }

        // 默认展开第一项 (保持原有逻辑)
        if (!sessionList.isEmpty()) expandedState.put(0, true);

        notifyDataSetChanged();
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
        // 更新摘要信息：显示动作数量
        int count = (session.exercises != null) ? session.exercises.size() : 0;
        holder.tvSubInfo.setText(session.workoutName + " · " + count + "个动作");

        boolean isExpanded = expandedState.get(position, false);

        if (isExpanded) {
            holder.rvExercises.setVisibility(View.VISIBLE);
            holder.divider.setVisibility(View.VISIBLE);
            holder.ivArrow.setRotation(180f);

            // ✅ 将监听器传递给内部的子列表
            InnerExerciseAdapter innerAdapter = new InnerExerciseAdapter(session.exercises, itemClickListener);
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
            notifyItemChanged(position); // 刷新当前项以切换显示/隐藏
        });
    }

    @Override
    public int getItemCount() {
        return sessionList.size();
    }

    // =========================================================
    //  ViewHolder (保持不变)
    // =========================================================
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
        private final OnHistoryItemClickListener listener; // ✅ 新增监听器引用

        // ✅ 修改内部构造函数接收监听器
        InnerExerciseAdapter(List<HistoryEntity> exercises, OnHistoryItemClickListener listener) {
            this.exercises = exercises != null ? exercises : new ArrayList<>();
            this.listener = listener;
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

            // 通过 baseId 查缓存获取名称
            EntityNameCache cache = EntityNameCache.getInstance();
            String exerciseName = cache.getExerciseName(ex.baseId);

            holder.tvName.setText(exerciseName);
            holder.tvDetails.setText("完成: " + ex.weight + "kg × " + ex.reps + "次 (" + ex.sets + "组)");

            // ✅ 核心逻辑：给子项绑定长按事件
            holder.itemView.setOnLongClickListener(v -> {
                if (listener != null) {
                    listener.onHistoryItemLongClick(ex); // 触发弹窗
                }
                return true; // 返回 true 表示消耗了这个长按事件
            });
        }

        @Override
        public int getItemCount() {
            return exercises.size();
        }

        static class ChildViewHolder extends RecyclerView.ViewHolder {
            TextView tvName, tvDetails;
            ChildViewHolder(View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tvExerciseName);
                tvDetails = itemView.findViewById(R.id.tvExerciseDetails);
            }
        }
    }
}