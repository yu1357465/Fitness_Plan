package com.example.fitness_plan.data;

import java.util.List;

public class HistorySession {
    public String dateStr;
    public String workoutName;
    public List<ExerciseEntity> exercises;

    public HistorySession(String dateStr, String workoutName, List<ExerciseEntity> exercises) {
        this.dateStr = dateStr;
        this.workoutName = workoutName;
        this.exercises = exercises;
    }
}