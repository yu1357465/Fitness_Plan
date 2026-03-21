package com.example.fitness_plan;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.example.fitness_plan.ui.history.HistoryFragment;
import com.example.fitness_plan.ui.library.LibraryFragment;
import com.example.fitness_plan.ui.workout.WorkoutFragment;

public class MainActivity extends AppCompatActivity {

    private ViewPager2 viewPager;
    private MainPagerAdapter pagerAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ⭐⭐ 关键修改：必须加载包含 ViewPager2 的那个容器布局 ⭐⭐
        setContentView(R.layout.activity_main);

        viewPager = findViewById(R.id.viewPager);

        // 如果 viewPager 还是 null，说明 activity_main.xml 里没写 ViewPager2
        if (viewPager == null) {
            throw new RuntimeException("严重错误：在 activity_main.xml 中找不到 id 为 viewPager 的控件！");
        }

        // 1. 设置 Adapter
        pagerAdapter = new MainPagerAdapter(this);
        viewPager.setAdapter(pagerAdapter);

        // 2. 核心设置：默认选中中间页 (Index 1)
        viewPager.setCurrentItem(1, false);

        // 3. 预加载设置
        viewPager.setOffscreenPageLimit(2);
    }

    private static class MainPagerAdapter extends FragmentStateAdapter {

        public MainPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
            super(fragmentActivity);
        }

        @Override
        public int getItemCount() {
            return 3;
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            switch (position) {
                case 0:
                    return new LibraryFragment();
                case 1:
                    return new WorkoutFragment();
                case 2:
                    return new HistoryFragment();
                default:
                    return new WorkoutFragment();
            }
        }
    }
}