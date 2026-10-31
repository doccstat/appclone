package com.lixingchi.appclone;

import android.content.Context;
import android.content.SharedPreferences;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.LEGACY)
@org.robolectric.annotation.SQLiteMode(org.robolectric.annotation.SQLiteMode.Mode.LEGACY)
@org.robolectric.annotation.ConscryptMode(org.robolectric.annotation.ConscryptMode.Mode.OFF)
public final class BackgroundPolicyTest {
    private static final String PKG = "com.example.app";
    private static final BackgroundPolicy.State ORIGINAL = new BackgroundPolicy.State(1110123, 1234, 0, -1);
    private SharedPreferences prefs;
    private Root root;
    private BackgroundPolicy policy;

    private final class Root implements Function<String, Shell.Result> {
        BackgroundPolicy.State current = ORIGINAL;
        int writes;
        boolean failRead, failWrite, applyBeforeFailure, driftBeforeWrite, wrongResult;
        @Override public Shell.Result apply(String cmd) {
            if (cmd.equals(AppCommand.command("background-get", 11, 42, PKG)))
                return new Shell.Result(failRead ? 1 : 0, failRead ? "denied" : current.encode());
            int mode = cmd.endsWith(" '1'") ? 1 : Integer.parseInt(cmd.substring(cmd.lastIndexOf(" '") + 2, cmd.length() - 1));
            assertEquals(AppCommand.command("background-set", 11, 42, PKG, current.encode(), Integer.toString(mode)), cmd);
            assertTrue("restore point must exist before root mutation", prefs.contains("11:42:" + PKG));
            writes++;
            if (driftBeforeWrite) { current = current.withMode(4); return new Shell.Result(1, "concurrent edit"); }
            if (!failWrite || applyBeforeFailure) current = current.withMode(mode);
            return new Shell.Result(failWrite ? -1 : 0, failWrite ? "timeout" : (wrongResult ? ORIGINAL : current).encode());
        }
    }

    @Before public void setup() {
        ProfileStart.apkPath = "/data/app/appclone/base.apk";
        prefs = RuntimeEnvironment.getApplication().getSharedPreferences("background-test", Context.MODE_PRIVATE);
        prefs.edit().clear().commit(); root = new Root(); policy = new BackgroundPolicy(prefs, root);
    }

    @Test public void nothingChangesUntilSelectedAndRestoresEverySupportedPriorMode() {
        for (int mode : new int[] {0, 2, 3, 4}) {
            root.current = ORIGINAL.withMode(mode);
            BackgroundPolicy.State initial = policy.read(11, 42, PKG);
            assertNull(policy.previous(11, 42, PKG));
            int writes = root.writes;
            policy.restrict(11, 42, PKG, initial);
            assertEquals(initial, policy.previous(11, 42, PKG));
            assertEquals(initial.withMode(1), root.current);
            // Recreating the controller simulates reopening after process death/reboot.
            policy = new BackgroundPolicy(prefs, root);
            policy.restore(11, 42, PKG, root.current);
            assertEquals(initial, root.current); assertNull(policy.previous(11, 42, PKG));
            assertEquals(writes + 2, root.writes);
        }
    }

    @Test public void alreadyRestrictedOrUidOverrideDoesNotCreateAnOwnershipRecord() {
        for (BackgroundPolicy.State state : new BackgroundPolicy.State[] {ORIGINAL.withMode(1),
                new BackgroundPolicy.State(ORIGINAL.uid, 1234, 0, 0)}) {
            root.current = state;
            assertThrows(IllegalStateException.class, () -> policy.restrict(11, 42, PKG, state));
            assertNull(policy.previous(11, 42, PKG)); assertEquals(0, root.writes);
        }
    }

    @Test public void readFailureOrStalePreviewCannotApply() {
        root.failRead = true;
        assertThrows(IllegalStateException.class, () -> policy.restrict(11, 42, PKG, ORIGINAL));
        root.failRead = false; root.current = ORIGINAL.withMode(4);
        assertThrows(IllegalStateException.class, () -> policy.restrict(11, 42, PKG, ORIGINAL));
        assertEquals(0, root.writes); assertNull(policy.previous(11, 42, PKG));
    }

    @Test public void failedOrInterruptedApplyKeepsRestorePointWhetherOrNotItChangedAndroid() {
        for (boolean applied : new boolean[] {false, true}) {
            root.failWrite = true; root.applyBeforeFailure = applied;
            assertThrows(IllegalStateException.class, () -> policy.restrict(11, 42, PKG, ORIGINAL));
            assertEquals(ORIGINAL, policy.previous(11, 42, PKG));
            root.failWrite = false;
            new BackgroundPolicy(prefs, root).restore(11, 42, PKG, root.current);
            assertEquals(ORIGINAL, root.current); assertNull(policy.previous(11, 42, PKG));
        }
    }

    @Test public void failedRestoreOrUnexpectedResultDoesNotEraseRecord() {
        policy.restrict(11, 42, PKG, ORIGINAL);
        root.failWrite = true;
        assertThrows(IllegalStateException.class, () -> policy.restore(11, 42, PKG, root.current));
        assertEquals(ORIGINAL, policy.previous(11, 42, PKG));
        root.failWrite = false; policy.restore(11, 42, PKG, root.current);
        root.wrongResult = true;
        assertThrows(IllegalStateException.class, () -> policy.restrict(11, 42, PKG, ORIGINAL));
        assertEquals(ORIGINAL, policy.previous(11, 42, PKG));
    }

    @Test public void externalChangesReinstallsAndUidOverridesAreNotOverwrittenOnRestore() {
        policy.restrict(11, 42, PKG, ORIGINAL);
        for (BackgroundPolicy.State state : new BackgroundPolicy.State[] {ORIGINAL.withMode(4),
                new BackgroundPolicy.State(ORIGINAL.uid + 1, 1234, 1, -1),
                new BackgroundPolicy.State(ORIGINAL.uid, 9999, 1, -1),
                new BackgroundPolicy.State(ORIGINAL.uid, 1234, 1, 0)}) {
            root.current = state;
            assertThrows(IllegalStateException.class, () -> policy.restore(11, 42, PKG, state));
            assertEquals(1, root.writes); assertEquals(state, root.current);
            assertEquals(ORIGINAL, policy.previous(11, 42, PKG));
        }
        root.current = ORIGINAL;
        policy.restore(11, 42, PKG, ORIGINAL); // Already back to the exact previous state: clear only.
        assertEquals(1, root.writes); assertNull(policy.previous(11, 42, PKG));
    }

    @Test public void identityScopedRecordsDoNotLeakToSiblingsOwnerOrReplacementProfiles() {
        policy.restrict(11, 42, PKG, ORIGINAL);
        assertNull(policy.previous(12, 43, PKG));
        assertNull(policy.previous(11, 43, PKG));
        assertNull(policy.previous(11, 42, "com.other.app"));
        assertThrows(IllegalArgumentException.class, () -> policy.read(0, 42, PKG));
        assertThrows(IllegalArgumentException.class, () -> policy.read(11, -1, PKG));
        assertThrows(IllegalStateException.class, () -> policy.restrict(11, 42, PKG, root.current));
        assertEquals(ORIGINAL, policy.previous(11, 42, PKG));
    }

    @Test public void forgettingOnlyRemovesTheSelectedRecordNeverAndroidPolicy() {
        policy.restrict(11, 42, PKG, ORIGINAL);
        assertThrows(IllegalStateException.class, () -> policy.forget(11, 42, PKG, ORIGINAL.withMode(4)));
        policy.forget(11, 42, PKG, ORIGINAL);
        assertEquals(1, root.writes); assertEquals(ORIGINAL.withMode(1), root.current);
        assertNull(policy.previous(11, 42, PKG));
    }

    @Test public void raceAfterPreviewIsRejectedByRootAndPriorModeRetained() {
        root.driftBeforeWrite = true;
        assertThrows(IllegalStateException.class, () -> policy.restrict(11, 42, PKG, ORIGINAL));
        assertEquals(ORIGINAL.withMode(4), root.current);
        assertEquals(ORIGINAL, policy.previous(11, 42, PKG));
    }

    @Test public void storageFailurePreventsMutationAndKeepsRestoreRecordOnCleanupFailure() {
        SharedPreferences failing = failingCommitPreferences();
        BackgroundPolicy broken = new BackgroundPolicy(failing, root);
        assertThrows(IllegalStateException.class, () -> broken.restrict(11, 42, PKG, ORIGINAL));
        assertEquals(0, root.writes);
        policy.restrict(11, 42, PKG, ORIGINAL);
        assertThrows(IllegalStateException.class, () -> broken.restore(11, 42, PKG, root.current));
        assertEquals(ORIGINAL, root.current); assertEquals(ORIGINAL, policy.previous(11, 42, PKG));
    }

    @Test public void malformedAndCrossUserReadResultsFailClosed() {
        for (String text : new String[] {"", "0:0:0:-1", "1110123:0:5:-1", "1110123:0:0:5", "1110123:0:0", "1110123:-1:0:-1"})
            assertThrows(IllegalArgumentException.class, () -> BackgroundPolicy.State.parse(text));
        AtomicInteger calls = new AtomicInteger();
        BackgroundPolicy other = new BackgroundPolicy(prefs, cmd -> {
            calls.incrementAndGet(); return new Shell.Result(0, "1210123:1234:0:-1");
        });
        assertThrows(IllegalStateException.class, () -> other.restrict(11, 42, PKG, ORIGINAL));
        assertEquals(1, calls.get()); assertNull(other.previous(11, 42, PKG));
    }

    @Test public void namedRootApiSignaturesExistOnAndroid35() throws Exception {
        Class<?> api = Class.forName("com.android.internal.app.IAppOpsService");
        assertNotNull(api.getMethod("getOpsForPackage", int.class, String.class, int[].class));
        assertNotNull(api.getMethod("getUidOps", int.class, int[].class));
        assertNotNull(api.getMethod("setMode", int.class, int.class, String.class, int.class));
        Class<?> pm = Class.forName("android.content.pm.IPackageManager");
        assertNotNull(pm.getMethod("getPackageInfo", String.class, long.class, int.class));
        assertNotNull(pm.getMethod("getPackagesForUid", int.class));
        Class<?> ops = android.app.AppOpsManager.class;
        int op = (Integer) ops.getMethod("strOpToOp", String.class).invoke(null, "android:run_any_in_background");
        assertEquals(0, ops.getMethod("opToDefaultMode", int.class).invoke(null, op));
        assertNotNull(Class.forName("android.app.AppOpsManager$PackageOps").getMethod("getOps"));
        assertNotNull(Class.forName("android.app.AppOpsManager$OpEntry").getMethod("getOp"));
        assertNotNull(Class.forName("android.app.AppOpsManager$OpEntry").getMethod("getMode"));
    }

    private SharedPreferences failingCommitPreferences() {
        return (SharedPreferences) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {SharedPreferences.class}, (proxy, method, args) -> {
            Object value = method.invoke(prefs, args);
            if (!method.getName().equals("edit")) return value;
            return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {SharedPreferences.Editor.class}, (editorProxy, edit, values) -> {
                Object result = edit.invoke(value, values);
                if (edit.getName().equals("commit")) return false;
                return result instanceof SharedPreferences.Editor ? editorProxy : result;
            });
        });
    }
}
