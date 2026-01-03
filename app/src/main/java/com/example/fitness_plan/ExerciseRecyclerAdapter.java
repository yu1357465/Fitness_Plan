package com.example.fitness_plan;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
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
import androidx.appcompat.widget.PopupMenu; // 导入 PopupMenu
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
        void onCompletionChanged();
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
        boolean isGhost = exercise.name.equals("新动作");

        if (isGhost) {
            // >>> 幽灵卡 (Ghost)
            holder.cardNormal.setAlpha(0.5f);
            holder.name.setText("点击输入动作名称...");
            holder.name.setTextColor(Color.GRAY);
            holder.name.setTypeface(null, android.graphics.Typeface.ITALIC);

            // 纯白背景
            holder.cardNormal.setCardBackgroundColor(Color.WHITE);
            holder.cardNormal.setStrokeWidth(0);
            holder.cardNormal.setCardElevation(0f);
            holder.itemView.findViewById(R.id.normalLayout).setBackground(null);

            holder.btnPin.setVisibility(View.GONE);
            holder.btnChart.setVisibility(View.GONE);
            holder.layoutSets.setAlpha(0.3f);
            holder.layoutReps.setAlpha(0.3f);

            // 【幽灵卡特权】点击整张卡片 = 重命名 (初始化)
            holder.itemView.setOnClickListener(v -> listener.onRename(exercise));
            holder.itemView.setOnLongClickListener(null);
            if (holder.dragHandle != null) holder.dragHandle.setOnTouchListener(null);

        } else {
            // >>> 正常卡片 (Normal)
            holder.cardNormal.setAlpha(1.0f);
            holder.name.setTypeface(null, android.graphics.Typeface.BOLD);
            holder.btnPin.setVisibility(View.VISIBLE);
            holder.btnChart.setVisibility(View.VISIBLE);
            holder.layoutSets.setAlpha(1.0f);
            holder.layoutReps.setAlpha(1.0f);

            boolean isPermanent = (exercise.color == null || exercise.color.equalsIgnoreCase("#FFFFFF"));
            int textColorPrimary;
            int textColorSecondary;

            if (isPermanent) {
                // === A. 计划内动作 ===
                textColorPrimary = context.getResources().getColor(R.color.flat_text_primary);
                textColorSecondary = context.getResources().getColor(R.color.flat_text_secondary);
                holder.btnPin.setImageResource(R.drawable.ic_pin_filled);
                holder.btnPin.setColorFilter(context.getResources().getColor(R.color.flat_accent), PorterDuff.Mode.SRC_IN);

                // 实心边框
                holder.itemView.findViewById(R.id.normalLayout).setBackground(null);
                holder.cardNormal.setCardBackgroundColor(Color.WHITE);
                holder.cardNormal.setCardElevation(4f);
                holder.cardNormal.setStrokeColor(Color.parseColor("#CFD8DC")); // 蓝灰色实线
                holder.cardNormal.setStrokeWidth(3);

            } else {
                // === B. 临时动作 ===
                textColorPrimary = Color.parseColor("#90A4AE");
                textColorSecondary = Color.parseColor("#B0BEC5");
                holder.btnPin.setImageResource(R.drawable.ic_pin_outline);
                holder.btnPin.setColorFilter(Color.GRAY, PorterDuff.Mode.SRC_IN);

                // 虚线背景，透明CardView
                holder.cardNormal.setCardBackgroundColor(Color.TRANSPARENT);
                holder.cardNormal.setCardElevation(0f);
                holder.cardNormal.setStrokeWidth(0);
                holder.itemView.findViewById(R.id.normalLayout).setBackgroundResource(R.drawable.bg_dashed_border);
            }

            holder.name.setTextColor(textColorPrimary);
            holder.weight.setTextColor(textColorPrimary);
            holder.sets.setTextColor(textColorPrimary);
            holder.reps.setTextColor(textColorPrimary);
            holder.unit.setTextColor(textColorSecondary);

            // 完成状态
            if (exercise.isCompleted) {
                holder.itemView.findViewById(R.id.normalLayout).setBackground(null);
                holder.cardNormal.setCardBackgroundColor(Color.parseColor("#E0F2F1"));
                holder.cardNormal.setStrokeWidth(0);
                holder.cardNormal.setCardElevation(2f);
                holder.name.setAlpha(0.6f);
                setStrikeThrough(holder.name, true);
            } else {
                holder.name.setAlpha(1.0f);
                setStrikeThrough(holder.name, false);
            }

            // 1. 点击整张卡片 (包含名字区域) -> 切换完成状态
            holder.itemView.setOnClickListener(v -> {
                exercise.isCompleted = !exercise.isCompleted;
                listener.onUpdate(exercise);
                listener.onCompletionChanged();
                notifyItemChanged(position);
            });

            // 2. 长按 -> 弹出菜单 (包含：修改名称、删除)
            holder.itemView.setOnLongClickListener(v -> {
                showPopupMenu(v, exercise);
                return true;
            });

            // 3. 拖拽把手
            if (holder.dragHandle != null) {
                holder.dragHandle.setOnTouchListener((v, event) -> {
                    if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                        listener.onStartDrag(holder);
                    }
                    return false;
                });
            }
        }

        // --- 数据填充 (保持不变) ---
        holder.name.setText(exercise.name);
        holder.btnPin.setOnClickListener(v -> listener.onTogglePin(exercise));

        holder.sets.setText(String.valueOf(exercise.sets));
        holder.reps.setText(String.valueOf(exercise.reps));

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

        if (holder.unit != null) holder.unit.setText(isLbsMode ? "lbs" : "kg");
        double displayWeight = isLbsMode ? (exercise.weight * 2.20462) : exercise.weight;
        String wStr = (displayWeight % 1 == 0) ? String.valueOf((int) displayWeight) : String.format(Locale.getDefault(), "%.1f", displayWeight);
        holder.weight.setText(wStr);

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

        holder.chartContainer.setVisibility(exercise.isExpanded ? View.VISIBLE : View.GONE);
        if (exercise.isExpanded) listener.onShowChart(holder.chart, exercise);
        if (holder.btnChart != null) {
            holder.btnChart.setOnClickListener(v -> {
                exercise.isExpanded = !exercise.isExpanded;
                notifyItemChanged(position);
            });
        }
    }
    private void updateCardAppearance(ItemViewHolder holder, ExerciseEntity exercise) {
        if (exercise.isCompleted) {
            holder.cardNormal.setCardBackgroundColor(Color.parseColor("#E0F2F1"));
            holder.name.setAlpha(0.6f);
            setStrikeThrough(holder.name, true);
        } else {
            String colorHex = (exercise.color != null) ? exercise.color : "#FFFFFF";
            try {
                holder.cardNormal.setCardBackgroundColor(Color.parseColor(colorHex));
            } catch (Exception e) {
                holder.cardNormal.setCardBackgroundColor(Color.WHITE);
            }
            holder.name.setAlpha(1.0f);
            setStrikeThrough(holder.name, false);
        }
    }

    // 更新后的弹窗菜单逻辑
    private void showPopupMenu(View view, ExerciseEntity exercise) {
        PopupMenu popup = new PopupMenu(context, view, Gravity.END);

        // 添加菜单项 (groupId, itemId, order, title)
        popup.getMenu().add(0, 1, 0, "修改名称"); // 新增选项
        popup.getMenu().add(0, 2, 1, "删除动作");

        popup.setOnMenuItemClickListener(item -> {
            String title = item.getTitle().toString();
            if (title.equals("删除动作")) {
                listener.onDelete(exercise);
                return true;
            } else if (title.equals("修改名称")) {
                // 调用重命名逻辑
                listener.onRename(exercise);
                return true;
            }
            return false;
        });
        popup.show();
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
        MaterialCardView cardNormal;
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