package com.vf6.launcher.model;

import android.content.ComponentName;

public final class AppEntry {
    public final String label;
    public final String packageName;
    public final String activityName;

    public AppEntry(String label, String packageName, String activityName) {
        this.label = label;
        this.packageName = packageName;
        this.activityName = activityName;
    }

    public ComponentName component() {
        return new ComponentName(packageName, activityName);
    }

    public String flatten() {
        return component().flattenToString();
    }

    @Override
    public String toString() {
        return label;
    }
}
