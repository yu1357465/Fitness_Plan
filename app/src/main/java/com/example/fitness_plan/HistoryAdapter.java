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

import com.example.fitness_plan.data.ExerciseEntity;
import com.example.fitness_plan.data.HistorySession;

import java.util.List;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder> {

    private final Context context;
    private final List<HistorySession> historyList;
    private final SparseBooleanArray expandedState = new SparseBooleanArray();

    public HistoryAdapter(Context context, List<HistorySession> historyList) {
        this.context = context;
        this.historyList = historyList;
        // 默认展开第一项
        if (!historyList.isEmpty()) expandedState.put(0, true);
    }

    @NonNull
    @Override
    public HistoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // 使用 item_history_group 布局
        View view = LayoutInflater.from(context).inflate(R.layout.item_history_group, parent, false);
        return new HistoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull HistoryViewHolder holder, int position) {
        HistorySession session = historyList.get(position);

        holder.tvDate.setText(session.dateStr);
        holder.tvSubInfo.setText(session.workoutName + " · " + session.exercises.size() + "个动作");

        boolean isExpanded = expandedState.get(position, false);

        if (isExpanded) {
            holder.rvExercises.setVisibility(View.VISIBLE);
            holder.divider.setVisibility(View.VISIBLE);
            holder.ivArrow.setRotation(180f);

            InnerExerciseAdapter innerAdapter = new InnerExerciseAdapter(session.exercises);
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
    public int getItemCount() { return historyList.size(); }

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

    // 内部子列表 Adapter
    private static class InnerExerciseAdapter extends RecyclerView.Adapter<InnerExerciseAdapter.ChildViewHolder> {
        private final List<ExerciseEntity> exercises;

        InnerExerciseAdapter(List<ExerciseEntity> exercises) {
            this.exercises = exercises;
        }

        @NonNull
        @Override
        public ChildViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            // 使用 item_history_child 布局
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_history_child, parent, false);
            return new ChildViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ChildViewHolder holder, int position) {
            ExerciseEntity ex = exercises.get(position);
            holder.tvName.setText(ex.name);
            holder.tvDetails.setText("完成: " + ex.weight + "kg × " + ex.reps + "次 (" + ex.sets + "组)");
        }

        @Override
        public int getItemCount() { return exercises.size(); }

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