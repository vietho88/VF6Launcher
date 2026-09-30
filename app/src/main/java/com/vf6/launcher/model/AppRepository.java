package com.vf6.launcher.model;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class AppRepository {
    private AppRepository() {}

    public static List<AppEntry> load(Context context) {
        Intent i = new Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> rs = context.getPackageManager().queryIntentActivities(i, PackageManager.MATCH_ALL);
        List<AppEntry> out = new ArrayList<>();
        for (ResolveInfo r : rs) {
            if (r.activityInfo == null || context.getPackageName().equals(r.activityInfo.packageName)) continue;
            String label = String.valueOf(r.loadLabel(context.getPackageManager()));
            out.add(new AppEntry(label, r.activityInfo.packageName, r.activityInfo.name));
        }
        Collections.sort(out, Comparator.comparing(a -> a.label.toLowerCase()));
        return out;
    }
}
