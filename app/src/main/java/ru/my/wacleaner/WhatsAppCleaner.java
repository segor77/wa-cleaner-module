package ru.my.wacleaner;

import java.io.File;

import android.app.Activity;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import de.robv.android.xposed.XposedHelpers;

public class WhatsAppCleaner implements IXposedHookLoadPackage {

    private static final String TARGET_PKG = "com.whatsapp";
    private static final String TARGET_DIR =
            "/data/data/com.whatsapp/app_light_prefs/com.whatsapp";

    // Количество живых Activity в процессе WhatsApp
    private static int sActivityCount = 0;

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!TARGET_PKG.equals(lpparam.packageName)) {
            return;
        }

        XposedBridge.log("WA Cleaner: loaded in " + lpparam.packageName);

        Class<?> activityClass = XposedHelpers.findClass(
                "android.app.Activity", lpparam.classLoader);

        // onCreate: увеличиваем счётчик активностей
        XposedBridge.hookAllMethods(activityClass, "onCreate", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (!(param.thisObject instanceof Activity)) {
                    return;
                }
                sActivityCount++;
                XposedBridge.log("WA Cleaner: Activity created, count=" + sActivityCount);
            }
        });

        // onDestroy: уменьшаем счётчик, при 0 — считаем, что приложение закрыто
        XposedBridge.hookAllMethods(activityClass, "onDestroy", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (!(param.thisObject instanceof Activity)) {
                    return;
                }

                if (sActivityCount > 0) {
                    sActivityCount--;
                } else {
                    sActivityCount = 0;
                }

                XposedBridge.log("WA Cleaner: Activity destroyed, count=" + sActivityCount);

                if (sActivityCount == 0) {
                    // Все Activity уничтожены → чистим каталог
                    cleanWhatsAppDir();
                }
            }
        });
    }

    private static void cleanWhatsAppDir() {
        try {
            File dir = new File(TARGET_DIR);
            if (!dir.exists()) {
                XposedBridge.log("WA Cleaner: dir does not exist, nothing to clean");
                return;
            }

            deleteRecursive(dir);
            XposedBridge.log("WA Cleaner: directory cleaned: " + TARGET_DIR);
        } catch (Throwable t) {
            XposedBridge.log("WA Cleaner: error cleaning dir: " + t);
        }
    }

    private static void deleteRecursive(File f) {
        if (f == null || !f.exists()) return;

        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) {
                for (File c : children) {
                    deleteRecursive(c);
                }
            }
        }
        try {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        } catch (Throwable ignored) {}
    }
}
