package com.example.fitness_plan;

import android.content.Context;
import android.graphics.Paint;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.example.fitness_plan.data.ExerciseEntity;
import com.github.mikephil.charting.charts.LineChart;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class ExerciseRecyclerAdapter extends RecyclerView.Adapter<ExerciseRecyclerAdapter.ViewHolder> {

    private final Context context;
    private final List<ExerciseEntity> exerciseList;
    private final OnItemActionListener listener;
    private final boolean isLbsMode;

    public interface OnItemActionListener {
        void onUpdate(ExerciseEntity exercise);
        void onShowChart(LineChart chart, ExerciseEntity exercise);
        void onItemLongClick(ExerciseEntity exercise);
        void onOrderChanged();
        void onStartDrag(RecyclerView.ViewHolder holder);
        void onRename(ExerciseEntity exercise);
        void onDelete(ExerciseEntity exercise);
    }

    public ExerciseRecyclerAdapter(Context context, List<ExerciseEntity> list, boolean isLbsMode, OnItemActionListener listener) {
        this.context = context;
        this.exerciseList = list;
        this.isLbsMode = isLbsMode;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_exercise, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ExerciseEntity exercise = exerciseList.get(position);

        // ==========================================
        //  1. 胶卷滑动复位 & 状态机
        // ==========================================
        holder.cardNormal.setTranslationX(0f);
        holder.cardNormal.setAlpha(1.0f);
        holder.cardDelete.setTranslationX(0f);
        holder.cardCompleted.setTranslationX(0f);

        // 默认隐藏左右护法
        holder.cardDelete.setVisibility(View.INVISIBLE);
        holder.cardCompleted.setVisibility(View.INVISIBLE);

        // 如果处于删除模式，显示红卡
        if (exercise.isDeleteConfirmMode) {
            holder.cardDelete.setVisibility(View.VISIBLE);
            holder.cardNormal.setVisibility(View.INVISIBLE);
            holder.deleteClickArea.setOnClickListener(v -> listener.onDelete(exercise));
            return; // 结束，不绑定正常数据
        } else {
            holder.cardNormal.setVisibility(View.VISIBLE);
        }

        // ==========================================
        //  2. 常规数据绑定
        // ==========================================
        if (holder.weightWatcher != null) holder.weight.removeTextChangedListener(holder.weightWatcher);

        holder.name.setText(exercise.name);
        holder.nameCompleted.setText(exercise.name); // 左侧完成卡的名字
        holder.sets.setText(String.valueOf(exercise.sets));
        holder.reps.setText(String.valueOf(exercise.reps));
        if (holder.unit != null) holder.unit.setText(isLbsMode ? "lbs" : "kg");

        double displayWeight = isLbsMode ? (exercise.weight * 2.20462) : exercise.weight;
        String wStr = (displayWeight % 1 == 0) ? String.valueOf((int) displayWeight) : String.format(Locale.getDefault(), "%.1f", displayWeight);
        holder.weight.setText(wStr);

        updateUIState(holder, exercise);

        holder.name.setOnClickListener(v -> listener.onRename(exercise));
        holder.layoutSets.setOnClickListener(v -> showNumberPickerDialog("设置组数", 1, 20, exercise.sets, val -> {
            exercise.sets = val;
            listener.onUpdate(exercise);
        }));
        holder.layoutReps.setOnClickListener(v -> showNumberPickerDialog("设置次数", 1, 100, exercise.reps, val -> {
            exercise.reps = val;
            listener.onUpdate(exercise);
        }));

        holder.chartContainer.setVisibility(exercise.isExpanded ? View.VISIBLE : View.GONE);
        if (exercise.isExpanded) listener.onShowChart(holder.chart, exercise);
        if (holder.btnChart != null) {
            holder.btnChart.setOnClickListener(v -> {
                exercise.isExpanded = !exercise.isExpanded;
                notifyItemChanged(position);
            });
        }

        holder.weightWatcher = new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                try {
                    String input = s.toString();
                    if (!input.isEmpty()) {
                        double inputVal = Double.parseDouble(input);
                        exercise.weight = isLbsMode ? (inputVal / 2.20462) : inputVal;
                        listener.onUpdate(exercise);
                    }
                } catch (NumberFormatException ignored) {}
            }
        };
        holder.weight.addTextChangedListener(holder.weightWatcher);

        if (holder.dragHandle != null) {
            holder.dragHandle.setOnTouchListener((v, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) listener.onStartDrag(holder);
                return false;
            });
        }
    }

    private void updateUIState(ViewHolder holder, ExerciseEntity exercise) {
        boolean isDone = exercise.isCompleted;
        float alpha = isDone ? 0.6f : 1.0f;

        holder.name.setAlpha(alpha);
        holder.weight.setAlpha(alpha);
        holder.layoutSets.setAlpha(alpha);
        holder.layoutReps.setAlpha(alpha);

        // 注意：原先 layoutWeight 可能没用到，这里安全起见只处理内部元素
        if (holder.weight != null) holder.weight.setAlpha(alpha);

        setStrikeThrough(holder.name, isDone);
        setStrikeThrough(holder.weight, isDone);
        setStrikeThrough(holder.sets, isDone);
        setStrikeThrough(holder.reps, isDone);
        setStrikeThrough(holder.unit, isDone);
    }

    private void setStrikeThrough(TextView tv, boolean active) {
        if (active) tv.setPaintFlags(tv.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        else tv.setPaintFlags(tv.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
    }

    public void deleteItem(int position) {
        if (position >= 0 && position < exerciseList.size()) {
            exerciseList.remove(position);
            notifyItemRemoved(position);
        }
    }

    public void restoreItem(ExerciseEntity item, int position) {
        exerciseList.add(position, item);
        notifyItemInserted(position);
    }

    public ExerciseEntity getItem(int position) {
        if (position >= 0 && position < exerciseList.size()) return exerciseList.get(position);
        return null;
    }

    @Override
    public int getItemCount() { return exerciseList.size(); }

    public void onItemMove(int fromPosition, int toPosition) {
        if (fromPosition < toPosition) {
            for (int i = fromPosition; i < toPosition; i++) Collections.swap(exerciseList, i, i + 1);
        } else {
            for (int i = fromPosition; i > toPosition; i--) Collections.swap(exerciseList, i, i - 1);
        }
        notifyItemMoved(fromPosition, toPosition);
        listener.onOrderChanged();
    }

    private void showNumberPickerDialog(String title, int min, int max, int currentVal, OnValueChangeListener callback) {
        NumberPicker picker = new NumberPicker(context);
        picker.setMinValue(min);
        picker.setMaxValue(max);
        picker.setValue(currentVal);
        picker.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
        new AlertDialog.Builder(context).setTitle(title).setView(picker)
                .setPositiveButton("确定", (d, w) -> callback.onValueChange(picker.getValue()))
                .setNegativeButton("取消", null).show();
    }

    interface OnValueChangeListener { void onValueChange(int val); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        // 胶卷滑动用的三张卡
        CardView cardNormal;      // 中 (白)
        CardView cardDelete;      // 右 (红)
        CardView cardCompleted;   // 左 (灰)

        View deleteClickArea;
        TextView nameCompleted;

        TextView name, sets, reps, unit;
        LinearLayout layoutSets, layoutReps;
        EditText weight;
        View btnChart, chartContainer;
        LineChart chart;
        ImageView dragHandle;
        TextWatcher weightWatcher;

        ViewHolder(View itemView) {
            super(itemView);
            // 绑定最外层的三张卡
            cardNormal = itemView.findViewById(R.id.cardNormal);
            cardDelete = itemView.findViewById(R.id.cardDelete);
            cardCompleted = itemView.findViewById(R.id.cardCompleted);

            deleteClickArea = itemView.findViewById(R.id.deleteClickArea);
            nameCompleted = itemView.findViewById(R.id.tvNameCompleted);

            name = itemView.findViewById(R.id.tvExerciseName);
            layoutSets = itemView.findViewById(R.id.layoutSets);
            layoutReps = itemView.findViewById(R.id.layoutReps);
            sets = itemView.findViewById(R.id.tvSets);
            reps = itemView.findViewById(R.id.tvReps);
            unit = itemView.findViewById(R.id.tvUnit);
            weight = itemView.findViewById(R.id.etWeight);
            btnChart = itemView.findViewById(R.id.btnChart);
            chartContainer = itemView.findViewById(R.id.chartContainer);
            chart = itemView.findViewById(R.id.miniLineChart);
            dragHandle = itemView.findViewById(R.id.ivDragHandle);
        }
    }

    abstract static class SimpleTextWatcher implements TextWatcher {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
    }
}