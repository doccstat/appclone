package com.lixingchi.appclone;

import android.os.Build;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Runs in system_server (module scope {@code system}). Raises the clone-profile
 * creation limits and the running-user budget so many clones can be created and
 * stay running, matching Moto's app clone.
 */
public final class MainHook implements IXposedHookLoadPackage {
    private static final String TAG = "AppClone: ";
    private static final String USER_TYPE_DETAILS = "com.android.server.pm.UserTypeDetails";
    private static final String USER_MANAGER_SERVICE = "com.android.server.pm.UserManagerService";
    private static final String USER_CONTROLLER = "com.android.server.am.UserController";
    private static boolean installed;

    @Override public synchronized void handleLoadPackage(XC_LoadPackage.LoadPackageParam pkg) {
        if (installed || !ClonePolicy.supported(Build.VERSION.SDK_INT)) return;
        if (!"android".equals(pkg.packageName)) return;
        installed = true;
        final ClassLoader cl = pkg.classLoader;
        int ok = 0;
        ok += tryInstall("clone type limits", () -> hookCloneTypeLimits(cl));
        ok += tryInstall("max supported users", () -> hookMaxSupportedUsers(cl));
        ok += tryInstall("max running users", () -> hookMaxRunningUsers(cl));
        ok += tryInstall("clone total fallback", () -> hookCanAddMoreUsersOfType(cl));
        ok += tryInstall("clone profile fallback", () -> hookCanAddMoreProfilesToUser(cl));
        XposedBridge.log(TAG + "hooks installed: " + ok + "/5 sdk=" + Build.VERSION.SDK_INT);
    }

    private interface Hook {
        void install() throws Throwable;
    }

    private static int tryInstall(String what, Hook hook) {
        try {
            hook.install();
            return 1;
        } catch (Throwable t) {
            XposedBridge.log(TAG + "failed to hook " + what + ": " + t);
            return 0;
        }
    }

    /** Raise the CLONE total and per-parent profile caps. */
    private static void hookCloneTypeLimits(ClassLoader cl) {
        XC_MethodHook raiseForClone = new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                Object name = XposedHelpers.getObjectField(param.thisObject, "mName");
                if (ClonePolicy.isCloneType((String) name)) {
                    param.setResult(ClonePolicy.LIMIT);
                }
            }
        };
        XposedHelpers.findAndHookMethod(USER_TYPE_DETAILS, cl, "getMaxAllowed", raiseForClone);
        XposedHelpers.findAndHookMethod(USER_TYPE_DETAILS, cl, "getMaxAllowedPerParent",
                raiseForClone);
    }

    /** Remove the device-wide user ceiling (stock value is 1). */
    private static void hookMaxSupportedUsers(ClassLoader cl) {
        XposedHelpers.findAndHookMethod("android.os.UserManager", cl, "getMaxSupportedUsers",
                new XC_MethodReplacement() {
                    @Override protected Object replaceHookedMethod(MethodHookParam param) {
                        return ClonePolicy.LIMIT;
                    }
                });
    }

    /**
     * OEM fallback: force the CLONE total cap, leaving every other user type alone.
     * AOSP already derives this from {@link #hookCloneTypeLimits}; this only matters
     * when firmware replaces {@code canAddMoreUsersOfType} with its own check.
     */
    private static void hookCanAddMoreUsersOfType(ClassLoader cl) {
        XposedHelpers.findAndHookMethod(USER_MANAGER_SERVICE, cl, "canAddMoreUsersOfType",
                String.class, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        if (ClonePolicy.isCloneType((String) param.args[0])) param.setResult(true);
                    }
                });
    }

    /**
     * OEM fallback: force the CLONE per-parent cap, leaving managed and private
     * profiles alone. AOSP already derives this from {@link #hookCloneTypeLimits}.
     */
    private static void hookCanAddMoreProfilesToUser(ClassLoader cl) {
        XposedHelpers.findAndHookMethod(USER_MANAGER_SERVICE, cl, "canAddMoreProfilesToUser",
                String.class, int.class, boolean.class, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        if (ClonePolicy.isCloneType((String) param.args[0])) param.setResult(true);
                    }
                });
    }

    /**
     * Raise the running-user budget (stock value is 3) so clones stay started.
     * The signature grew over Android versions (4 args on 16, 6 on 17), so hook
     * every declared {@code setInitialConfig(boolean, int, ...)} overload.
     */
    private static void hookMaxRunningUsers(ClassLoader cl) {
        Class<?> controller = XposedHelpers.findClass(USER_CONTROLLER, cl);
        XC_MethodHook raiseBudget = new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                param.args[1] = ClonePolicy.LIMIT;
            }
        };
        int hooked = 0;
        for (java.lang.reflect.Method method : controller.getDeclaredMethods()) {
            Class<?>[] params = method.getParameterTypes();
            if (method.getName().equals("setInitialConfig") && params.length >= 2
                    && params[0] == boolean.class && params[1] == int.class) {
                XposedBridge.hookMethod(method, raiseBudget);
                hooked++;
            }
        }
        if (hooked == 0) {
            throw new NoSuchMethodError("UserController#setInitialConfig(boolean, int, ...)");
        }
    }
}
