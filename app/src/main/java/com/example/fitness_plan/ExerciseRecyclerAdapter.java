package com.example.fitness_plan;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.os.Build;
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
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.example.fitness_plan.data.ExerciseEntity;
import com.example.fitness_plan.data.ExerciseWithDetail;
import com.github.mikephil.charting.charts.LineChart;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class ExerciseRecyclerAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_ITEM = 0;
    private static final int TYPE_FOOTER = 1;

    private final Context context;
    private final List<ExerciseWithDetail> exerciseList;
    private final OnItemActionListener listener;
    private final boolean isLbsMode;

    public interface OnItemActionListener {
        void onUpdate(ExerciseWithDetail exercise);
        void onShowChart(LineChart chart, ExerciseWithDetail exercise);
        void onItemLongClick(ExerciseWithDetail exercise);
        void onOrderChanged();
        void onStartDrag(RecyclerView.ViewHolder holder);
        void onRename(ExerciseWithDetail exercise);
        void onDelete(ExerciseWithDetail exercise);
        void onTogglePin(ExerciseWithDetail exercise);
        void onAddEmptyCard();
        void onCompletionChanged();
    }

    public ExerciseRecyclerAdapter(Context context, List<ExerciseWithDetail> list, boolean isLbsMode, OnItemActionListener listener) {
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

    @SuppressLint("ClickableViewAccessibility")
    private void bindItem(ItemViewHolder holder, int position) {
        ExerciseWithDetail exercise = exerciseList.get(position);
        boolean isGhost = exercise.getBaseId() == -1;

        // ============================================================
        // 全局强制去阴影、去点击浮动效果
        // ============================================================
        holder.cardNormal.setCardElevation(0f);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            holder.cardNormal.setElevation(0f);
            holder.cardNormal.setStateListAnimator(null);
        }

        if (isGhost) {
            holder.cardNormal.setAlpha(0.5f);
            holder.name.setText("点击输入动作名称...");
            holder.name.setTextColor(Color.GRAY);
            holder.name.setTypeface(null, android.graphics.Typeface.ITALIC);

            holder.cardNormal.setCardBackgroundColor(Color.WHITE);
            holder.cardNormal.setStrokeWidth(0);
            holder.itemView.findViewById(R.id.normalLayout).setBackground(null);

            holder.btnPin.setVisibility(View.GONE);
            holder.btnChart.setVisibility(View.GONE);
            holder.layoutSets.setAlpha(0.3f);
            holder.layoutReps.setAlpha(0.3f);

            // ⭐ 隐藏幽灵卡片的徽章
            if (holder.tvCategoryBadge != null) holder.tvCategoryBadge.setVisibility(View.GONE);

            holder.itemView.setOnClickListener(v -> listener.onRename(exercise));
            holder.itemView.setOnLongClickListener(null);
            holder.itemView.setOnTouchListener(null);
            if (holder.dragHandle != null) holder.dragHandle.setOnTouchListener(null);

        } else {
            holder.cardNormal.setAlpha(1.0f);
            holder.name.setTypeface(null, android.graphics.Typeface.BOLD);
            holder.btnPin.setVisibility(View.VISIBLE);
            holder.btnChart.setVisibility(View.VISIBLE);
            holder.layoutSets.setAlpha(1.0f);
            holder.layoutReps.setAlpha(1.0f);

            holder.cardNormal.setClickable(false);

            boolean isPermanent = (exercise.getColor() == null || exercise.getColor().equalsIgnoreCase("#FFFFFF"));
            int textColorPrimary;
            int textColorSecondary;

            if (isPermanent) {
                textColorPrimary = context.getResources().getColor(R.color.flat_text_primary);
                textColorSecondary = context.getResources().getColor(R.color.flat_text_secondary);
                holder.btnPin.setImageResource(R.drawable.ic_pin_filled);
                holder.btnPin.setColorFilter(context.getResources().getColor(R.color.flat_accent), PorterDuff.Mode.SRC_IN);

                holder.itemView.findViewById(R.id.normalLayout).setBackground(null);
                holder.cardNormal.setCardBackgroundColor(Color.WHITE);
                holder.cardNormal.setStrokeColor(Color.TRANSPARENT);
                holder.cardNormal.setStrokeWidth(0);
            } else {
                textColorPrimary = Color.parseColor("#90A4AE");
                textColorSecondary = Color.parseColor("#B0BEC5");
                holder.btnPin.setImageResource(R.drawable.ic_pin_outline);
                holder.btnPin.setColorFilter(Color.GRAY, PorterDuff.Mode.SRC_IN);

                holder.cardNormal.setCardBackgroundColor(Color.TRANSPARENT);
                holder.cardNormal.setStrokeWidth(0);
                holder.itemView.findViewById(R.id.normalLayout).setBackgroundResource(R.drawable.bg_dashed_border);
            }

            holder.name.setTextColor(textColorPrimary);
            holder.weight.setTextColor(textColorPrimary);
            holder.sets.setTextColor(textColorPrimary);
            holder.reps.setTextColor(textColorPrimary);
            holder.unit.setTextColor(textColorSecondary);

            if (exercise.isCompleted()) {
                holder.itemView.findViewById(R.id.normalLayout).setBackground(null);
                holder.cardNormal.setCardBackgroundColor(Color.parseColor("#E0F2F1"));
                holder.cardNormal.setStrokeWidth(0);
                holder.name.setAlpha(0.6f);
                setStrikeThrough(holder.name, true);

                // ⭐ 完成状态下，徽章也变暗一点
                if (holder.tvCategoryBadge != null) holder.tvCategoryBadge.setAlpha(0.6f);
            } else {
                holder.name.setAlpha(1.0f);
                setStrikeThrough(holder.name, false);
                if (holder.tvCategoryBadge != null) holder.tvCategoryBadge.setAlpha(1.0f);
            }

            // --- ⭐ 核心绘制：动作卡片上的彩色分类徽章 ---
            if (holder.tvCategoryBadge != null) {
                holder.tvCategoryBadge.setVisibility(View.VISIBLE);
                String rawCategory = exercise.category != null ? exercise.category : "Other";
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

                android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
                shape.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
                // 边角稍微圆滑一点 (6dp)
                shape.setCornerRadius(6f * context.getResources().getDisplayMetrics().density);
                shape.setColor(badgeColor);
                holder.tvCategoryBadge.setBackground(shape);
            }

            holder.itemView.setOnTouchListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    holder.lastTouchX = event.getX();
                    holder.lastTouchY = event.getY();
                }
                return false;
            });

            holder.itemView.setOnClickListener(v -> {
                exercise.exercise.isCompleted = !exercise.exercise.isCompleted;
                listener.onUpdate(exercise);
                listener.onCompletionChanged();
                notifyItemChanged(position);
            });

            holder.itemView.setOnLongClickListener(v -> {
                holder.popupAnchor.setX(holder.lastTouchX);
                holder.popupAnchor.setY(holder.lastTouchY);
                showPopupMenu(holder.popupAnchor, exercise);
                return true;
            });

            if (holder.dragHandle != null) {
                holder.dragHandle.setOnTouchListener((v, event) -> {
                    if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                        listener.onStartDrag(holder);
                    }
                    return false;
                });
            }
        }

        // 通用数据填充
        holder.name.setText(exercise.exerciseName);
        holder.btnPin.setOnClickListener(v -> listener.onTogglePin(exercise));

        holder.sets.setText(String.valueOf(exercise.getSets()));
        holder.reps.setText(String.valueOf(exercise.getReps()));

        holder.layoutSets.setOnClickListener(v -> {
            if(!isGhost) showNumberPickerDialog("设置组数", 1, 20, exercise.getSets(), val -> {
                exercise.exercise.sets = val;
                listener.onUpdate(exercise); // 1. 后台偷偷更新数据库
                notifyItemChanged(position); // ⭐ 2. 核心修复：强行命令 UI 立即刷新当前卡片！
            });
        });
        holder.layoutReps.setOnClickListener(v -> {
            if(!isGhost) showNumberPickerDialog("设置次数", 1, 100, exercise.getReps(), val -> {
                exercise.exercise.reps = val;
                listener.onUpdate(exercise); // 1. 后台偷偷更新数据库
                notifyItemChanged(position); // ⭐ 2. 核心修复：强行命令 UI 立即刷新当前卡片！
            });
        });

        if (holder.unit != null) holder.unit.setText(isLbsMode ? "lbs" : "kg");
        double displayWeight = isLbsMode ? (exercise.getWeight() * 2.20462) : exercise.getWeight();
        String wStr = (displayWeight % 1 == 0) ? String.valueOf((int) displayWeight) : String.format(Locale.getDefault(), "%.1f", displayWeight);
        holder.weight.setText(wStr);

        holder.weightWatcher = new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                try {
                    String input = s.toString();
                    if (!input.isEmpty()) {
                        double inputVal = Double.parseDouble(input);
                        exercise.exercise.weight = isLbsMode ? (inputVal / 2.20462) : inputVal;
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

    private void showPopupMenu(View anchorView, ExerciseWithDetail exercise) {
        PopupMenu popup = new PopupMenu(context, anchorView, Gravity.NO_GRAVITY);
        popup.getMenu().add(0, 1, 0, "修改动作"); // 改个名字更贴切
        popup.getMenu().add(0, 2, 1, "删除卡片");
        popup.setOnMenuItemClickListener(item -> {
            String title = item.getTitle().toString();
            if (title.equals("删除卡片")) {
                listener.onDelete(exercise);
                return true;
            } else if (title.equals("修改动作")) {
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

    public void restoreItem(ExerciseWithDetail item, int position) {
        exerciseList.add(position, item);
        notifyItemInserted(position);
    }

    public void addItem(ExerciseWithDetail item) {
        exerciseList.add(item);
        notifyItemInserted(exerciseList.size() - 1);
    }

    public void removeItem(ExerciseWithDetail item) {
        int pos = exerciseList.indexOf(item);
        if (pos != -1) {
            exerciseList.remove(pos);
            notifyItemRemoved(pos);
        }
    }

    public ExerciseWithDetail getItem(int position) {
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

    public static class ItemViewHolder extends RecyclerView.ViewHolder {

        public com.google.android.material.card.MaterialCardView cardNormal;
        // ⭐ 新增：绑定徽章控件
        public TextView name, sets, reps, unit, tvCategoryBadge;
        public LinearLayout layoutSets, layoutReps;
        public EditText weight;
        public ImageView btnPin;
        public View btnChart, chartContainer;
        public com.github.mikephil.charting.charts.LineChart chart;
        public ImageView dragHandle;
        public TextWatcher weightWatcher;
        public View popupAnchor;
        public float lastTouchX = 0f;
        public float lastTouchY = 0f;

        public ItemViewHolder(View itemView) {
            super(itemView);
            cardNormal = itemView.findViewById(R.id.cardNormal);
            popupAnchor = itemView.findViewById(R.id.popupAnchor);
            name = itemView.findViewById(R.id.tvExerciseName);
            // ⭐ 映射徽章的 ID
            tvCategoryBadge = itemView.findViewById(R.id.tvCategoryBadge);
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