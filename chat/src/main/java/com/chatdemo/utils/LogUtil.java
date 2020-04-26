package com.chatdemo.utils;

import android.util.Log;

public class LogUtil {

    public static String TAG = "chatui";
    public static boolean isOpen = true;

    public static void d(String msg) {
        if (isOpen) {
            Log.d(TAG, msg);
        }
    }

    public static void e(String msg) {
        if (isOpen) {
            Log.e(TAG, msg);
        }
    }
}
