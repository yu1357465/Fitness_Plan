package com.example.fitness_plan.ui.library;

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
        void onItemClick(ExerciseBaseEntity entity); // 点击 (暂无动作，可预留)
        void onItemLongClick(ExerciseBaseEntity entity); // 长按 (编辑/删除)
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
        // 复用之前的 item_exercise_card 布局，或者新建一个简单的 item_library_card
        // 这里为了省事，我们直接用代码生成一个简单的布局，或者你可以用 item_exercise_card
        // 建议新建 item_library_card.xml，内容如下：
        /*
        <LinearLayout ... padding="16dp" orientation="vertical">
             <TextView id="@+id/tvName" textSize="18sp" bold .../>
             <TextView id="@+id/tvCategory" textSize="12sp" color="#Grey" .../>
        </LinearLayout>
        */
        // 这里演示使用简单的 inflate，假设你用 android.R.layout.simple_list_item_2 或者自己建布局
        // 为了视觉效果，我建议你复用现有的 card 布局，或者新建一个简单的：
        View view = LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_2, parent, false);
        return new LibraryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull LibraryViewHolder holder, int position) {
        ExerciseBaseEntity item = list.get(position);
        holder.tvName.setText(item.name);
        holder.tvCategory.setText(item.category + " | 默认单位: " + item.defaultUnit);

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
        TextView tvName, tvCategory;

        public LibraryViewHolder(@NonNull View itemView) {
            super(itemView);
            // 对应 simple_list_item_2 的 ID
            tvName = itemView.findViewById(android.R.id.text1);
            tvCategory = itemView.findViewById(android.R.id.text2);

            tvName.setTextSize(18);
            tvName.setPadding(0, 10, 0, 0);
        }
    }
}