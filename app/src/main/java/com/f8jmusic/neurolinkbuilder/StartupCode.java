package com.f8jmusic.neurolinkbuilder;

import android.app.Application;

import com.f8jmusic.uroborostlib.UroborosTRuntime;

import android.app.Application;
import android.content.Context;

public class StartupCode extends Application {
    static StartupCode self = null;
    private static boolean activityVisible = false;
    private static Context appContext_ = null;

    public static boolean isActivityVisible() {
        return activityVisible;
    }

    public static void activityResumed() {
        activityVisible = true;
    }

    public static void activityPaused() {
        activityVisible = false;
    }

    // An alternate way to get context
    public static Context getContext() {
        return appContext_;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        appContext_ = getApplicationContext();

        self = this;

        if (UroborosTRuntime.hasBasicPermissions(this)) {
            UroborosTRuntime.PerformLogsSetup();
        }

        UroborosTRuntime.setApplicationContext(appContext_);
    }
}