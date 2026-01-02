package com.example.fitness_plan;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.example.fitness_plan.data.ExerciseEntity;
import com.github.mikephil.charting.charts.LineChart;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class ExerciseRecyclerAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_ITEM = 0;
    private static final int TYPE_FOOTER = 1;

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
        void onTogglePin(ExerciseEntity exercise);
        void onAddEmptyCard();
    }

    public ExerciseRecyclerAdapter(Context context, List<ExerciseEntity> list, boolean isLbsMode, OnItemActionListener listener) {
        this.context = context;
        this.exerciseList = list;
        this.isLbsMode = isLbsMode;
        this.listener = listener;
    }

    @Override
    public int getItemCount() { return exerciseList.size() + 1; }

    @Override
    public int getItemViewType(int position) {
        return (position == exerciseList.size()) ? TYPE_FOOTER : TYPE_ITEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_FOOTER) {
            View view = LayoutInflater.from(context).inflate(R.layout.item_footer_add, parent, false);
            return new FooterViewHolder(view);
        } else {
            View view = LayoutInflater.from(context).inflate(R.layout.item_exercise, parent, false);
            return new ItemViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof FooterViewHolder) {
            ((FooterViewHolder) holder).itemView.setOnClickListener(v -> listener.onAddEmptyCard());
        } else if (holder instanceof ItemViewHolder) {
            bindItem((ItemViewHolder) holder, position);
        }
    }

    private void bindItem(ItemViewHolder holder, int position) {
        ExerciseEntity exercise = exerciseList.get(position);

        // ==========================================
        //  【核心修改】严格的幽灵卡片逻辑
        // ==========================================
        // 只要名字叫 "新动作"，就认定为临时草稿
        boolean isGhost = exercise.name.equals("新动作");

        if (isGhost) {
            // >>> 幽灵状态 <<<
            holder.cardNormal.setAlpha(0.5f); // 半透明
            holder.name.setText("点击输入动作名称...");
            holder.name.setTextColor(Color.GRAY);
            holder.name.setTypeface(null, android.graphics.Typeface.ITALIC);

            // 隐藏干扰元素
            holder.btnPin.setVisibility(View.GONE);
            holder.btnChart.setVisibility(View.GONE);
            // 幽灵卡片通常不需要设置组数次数，也可以选择隐藏或禁用
            holder.layoutSets.setAlpha(0.3f);
            holder.layoutReps.setAlpha(0.3f);
        } else {
            // >>> 正常状态 <<<
            holder.cardNormal.setAlpha(1.0f);
            holder.name.setText(exercise.name);
            holder.name.setTextColor(context.getResources().getColor(R.color.flat_text_primary));
            holder.name.setTypeface(null, android.graphics.Typeface.BOLD);

            holder.btnPin.setVisibility(View.VISIBLE);
            holder.btnChart.setVisibility(View.VISIBLE);
            holder.layoutSets.setAlpha(1.0f);
            holder.layoutReps.setAlpha(1.0f);
        }

        // ==========================================
        //  常规绑定
        // ==========================================
        holder.cardNormal.setTranslationX(0f);

        String colorHex = (exercise.color != null) ? exercise.color : "#FFFFFF";
        try { holder.cardNormal.setCardBackgroundColor(Color.parseColor(colorHex)); }
        catch (IllegalArgumentException e) { holder.cardNormal.setCardBackgroundColor(Color.WHITE); colorHex = "#FFFFFF"; }

        // 图钉逻辑
        boolean isPermanent = colorHex.equalsIgnoreCase("#FFFFFF");
        if (isPermanent) {
            holder.btnPin.setImageResource(android.R.drawable.ic_menu_save);
            holder.btnPin.setColorFilter(context.getResources().getColor(R.color.flat_accent), PorterDuff.Mode.SRC_IN);
        } else {
            holder.btnPin.setImageResource(android.R.drawable.ic_menu_my_calendar);
            holder.btnPin.setColorFilter(Color.GRAY, PorterDuff.Mode.SRC_IN);
        }
        holder.btnPin.setOnClickListener(v -> listener.onTogglePin(exercise));

        // 删除卡片逻辑
        holder.cardDelete.setVisibility(View.INVISIBLE);
        holder.cardCompleted.setVisibility(View.INVISIBLE);

        if (exercise.isDeleteConfirmMode) {
            holder.cardDelete.setVisibility(View.VISIBLE);
            holder.cardNormal.setVisibility(View.INVISIBLE);
            holder.deleteClickArea.setOnClickListener(v -> listener.onDelete(exercise));
            return;
        } else {
            holder.cardNormal.setVisibility(View.VISIBLE);
        }

        if (holder.weightWatcher != null) holder.weight.removeTextChangedListener(holder.weightWatcher);

        holder.nameCompleted.setText(exercise.name);
        holder.sets.setText(String.valueOf(exercise.sets));
        holder.reps.setText(String.valueOf(exercise.reps));
        if (holder.unit != null) holder.unit.setText(isLbsMode ? "lbs" : "kg");

        double displayWeight = isLbsMode ? (exercise.weight * 2.20462) : exercise.weight;
        String wStr = (displayWeight % 1 == 0) ? String.valueOf((int) displayWeight) : String.format(Locale.getDefault(), "%.1f", displayWeight);
        holder.weight.setText(wStr);

        // 完成状态划线 (仅对非幽灵卡片生效，避免样式冲突)
        if (!isGhost) {
            updateUIState(holder, exercise);
        }

        // 点击事件
        holder.name.setOnClickListener(v -> listener.onRename(exercise));

        holder.layoutSets.setOnClickListener(v -> {
            if(!isGhost) showNumberPickerDialog("设置组数", 1, 20, exercise.sets, val -> {
                exercise.sets = val; listener.onUpdate(exercise);
            });
        });
        holder.layoutReps.setOnClickListener(v -> {
            if(!isGhost) showNumberPickerDialog("设置次数", 1, 100, exercise.reps, val -> {
                exercise.reps = val; listener.onUpdate(exercise);
            });
        });

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

    private void updateUIState(ItemViewHolder holder, ExerciseEntity exercise) {
        boolean isDone = exercise.isCompleted;
        float alpha = isDone ? 0.6f : 1.0f;
        holder.name.setAlpha(alpha);
        holder.weight.setAlpha(alpha);
        holder.layoutSets.setAlpha(alpha);
        holder.layoutReps.setAlpha(alpha);
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

    public void addItem(ExerciseEntity item) {
        exerciseList.add(item);
        notifyItemInserted(exerciseList.size() - 1);
    }

    public void removeItem(ExerciseEntity item) {
        int pos = exerciseList.indexOf(item);
        if (pos != -1) {
            exerciseList.remove(pos);
            notifyItemRemoved(pos);
        }
    }

    public ExerciseEntity getItem(int position) {
        if (position >= 0 && position < exerciseList.size()) return exerciseList.get(position);
        return null;
    }

    public void onItemMove(int fromPosition, int toPosition) {
        if (toPosition >= exerciseList.size()) return;
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

    static class FooterViewHolder extends RecyclerView.ViewHolder {
        FooterViewHolder(View itemView) { super(itemView); }
    }

    static class ItemViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView cardNormal, cardDelete, cardCompleted;
        View deleteClickArea;
        TextView nameCompleted;
        TextView name, sets, reps, unit;
        LinearLayout layoutSets, layoutReps;
        EditText weight;
        ImageView btnPin;
        View btnChart, chartContainer;
        LineChart chart;
        ImageView dragHandle;
        TextWatcher weightWatcher;

        ItemViewHolder(View itemView) {
            super(itemView);
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
            btnPin = itemView.findViewById(R.id.btnPin);
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