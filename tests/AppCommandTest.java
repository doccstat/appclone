package com.lixingchi.appclone;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.junit.Test;
import static org.junit.Assert.*;

public final class AppCommandTest {
    private static final String PKG = "com.example.app";
    private static final int UID = 1110123;
    private static final String LAUNCH = "cmd activity start-activity --user 11 -a android.intent.action.MAIN"
            + " -c android.intent.category.LAUNCHER -n 'com.example.app/.Main'";

    private static class Device implements AppCommand.Device {
        long serial = 42;
        String type = "android.os.usertype.profile.CLONE";
        int parent, checks, recycledAt, starts, unlocks, appReads, writes;
        boolean ephemeral, unlocked = true, parentUnlocked = true, startAccepted = true;
        AppCommand.Target target = new AppCommand.Target(UID, 1234, true, false);
        AppCommand.Target changedTarget;
        BackgroundPolicy.State state = new BackgroundPolicy.State(UID, 1234, 0, -1);
        final List<String> commands = new ArrayList<>();
        Function<String, Shell.Result> command = cmd -> new Shell.Result(0, "Starting: Intent");
        @Override public long verify(int user) {
            checks++;
            return ProfileStart.identity(user, 11, checks == recycledAt ? serial + 1 : serial, type, parent, ephemeral);
        }
        @Override public boolean unlocked(int user) { return user == 0 ? parentUnlocked : unlocked; }
        @Override public boolean start(int user) { assertEquals(11, user); starts++; return startAccepted; }
        @Override public boolean unlockCached(int user) { assertEquals(11, user); unlocks++; unlocked = true; return true; }
        @Override public AppCommand.Target app(int user, String pkg) {
            assertEquals(11, user); assertEquals(PKG, pkg); appReads++;
            if (target == null) throw new IllegalStateException("not installed");
            return appReads > 1 && changedTarget != null ? changedTarget : target;
        }
        @Override public Shell.Result shell(String cmd) { commands.add(cmd); return command.apply(cmd); }
        @Override public BackgroundPolicy.State background(int user, String pkg) { return state; }
        @Override public void background(int uid, String pkg, int mode) {
            assertEquals(UID, uid); assertEquals(PKG, pkg); writes++; state = state.withMode(mode);
        }
    }

    @Test public void readyLaunchUsesOneCommandWithoutStartOrAuthentication() throws Exception {
        Device device = new Device();
        assertTrue(AppCommand.launch(11, 42, PKG, ".Main", device).ok());
        assertEquals(java.util.Collections.singletonList(LAUNCH), device.commands);
        assertEquals(0, device.starts); assertEquals(0, device.unlocks);
        assertTrue(device.checks >= 2); assertEquals(2, device.appReads);
    }

    @Test public void stoppedLaunchUsesExistingGuardedUnlockPath() throws Exception {
        Device device = new Device(); device.unlocked = false;
        assertTrue(AppCommand.launch(11, 42, PKG, ".Main", device).ok());
        assertEquals(1, device.starts); assertEquals(1, device.unlocks);
        for (boolean lockedParent : new boolean[] {true, false}) {
            Device rejected = new Device(); rejected.unlocked = false;
            rejected.parentUnlocked = !lockedParent; rejected.startAccepted = false;
            assertThrows(IllegalStateException.class, () -> AppCommand.launch(11, 42, PKG, ".Main", rejected));
            assertTrue(rejected.commands.isEmpty());
        }
    }

    @Test public void rejectsRecycledProfilesBeforeStartingOrLaunchingIncludingLegacy() {
        for (long expected : new long[] {-1, 42}) {
            for (int changed : new int[] {2, 3}) {
                Device device = new Device(); device.recycledAt = changed;
                assertThrows(IllegalStateException.class, () -> AppCommand.launch(11, expected, PKG, ".Main", device));
                assertTrue(device.commands.isEmpty()); assertEquals(0, device.starts);
            }
        }
        Device cold = new Device(); cold.unlocked = false; cold.recycledAt = 2;
        assertThrows(IllegalStateException.class, () -> AppCommand.launch(11, 42, PKG, ".Main", cold));
        assertEquals(0, cold.starts);
        Device stale = new Device(); stale.serial = 43;
        assertThrows(IllegalStateException.class, () -> AppCommand.launch(11, 42, PKG, ".Main", stale));
        assertEquals(0, stale.appReads);
    }

    @Test public void invalidCloneOrAppNeverLaunchesOrChangesPolicy() {
        for (int kind = 0; kind < 4; kind++) {
            Device d = new Device();
            if (kind == 0) d.type = "android.os.usertype.profile.MANAGED";
            if (kind == 1) d.type = "android.os.usertype.profile.PRIVATE";
            if (kind == 2) d.parent = 10;
            if (kind == 3) d.ephemeral = true;
            assertThrows(IllegalStateException.class, () -> AppCommand.launch(11, 42, PKG, ".Main", d));
            assertThrows(IllegalStateException.class, () -> AppCommand.read(11, 42, PKG, d));
            assertTrue(d.commands.isEmpty()); assertEquals(0, d.writes);
        }
        Device d = new Device();
        assertThrows(IllegalArgumentException.class, () -> AppCommand.launch(0, 42, PKG, ".Main", d));
        assertThrows(IllegalArgumentException.class, () -> AppCommand.launch(11, 42, "com.app; id", ".Main", d));
        assertThrows(IllegalArgumentException.class, () -> AppCommand.read(11, -1, PKG, d));
        assertEquals(0, d.checks);
    }

    @Test public void missingDisabledOrReinstalledAppIsNotOpened() {
        for (AppCommand.Target target : new AppCommand.Target[] {null, new AppCommand.Target(UID, 1234, false, false)}) {
            Device d = new Device(); d.unlocked = false; d.target = target;
            assertThrows(IllegalStateException.class, () -> AppCommand.launch(11, 42, PKG, ".Main", d));
            assertEquals(0, d.starts); assertTrue(d.commands.isEmpty());
        }
        for (AppCommand.Target target : new AppCommand.Target[] {new AppCommand.Target(UID, 9999, true, false),
                new AppCommand.Target(UID + 1, 1234, true, false), new AppCommand.Target(UID, 1234, false, false)}) {
            Device d = new Device(); d.changedTarget = target;
            assertThrows(IllegalStateException.class, () -> AppCommand.launch(11, 42, PKG, ".Main", d));
            assertTrue(d.commands.isEmpty());
        }
    }

    @Test public void resolutionAndErrorsAreSpecificToRequestedProfile() throws Exception {
        Device d = new Device();
        d.command = cmd -> new Shell.Result(0, cmd.startsWith("cmd package") ? "priority=0\ncom.example.app/.Main" : "Starting: Intent");
        assertTrue(AppCommand.launch(11, -1, PKG, "", d).ok());
        assertEquals("cmd package resolve-activity --brief --user 11 -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -p 'com.example.app'", d.commands.get(0));
        assertEquals(LAUNCH, d.commands.get(1));
        for (String missing : new String[] {"No activity found", "com.other/.Main"}) {
            Device no = new Device(); no.command = cmd -> new Shell.Result(0, missing);
            assertFalse(AppCommand.launch(11, 42, PKG, "", no).ok()); assertEquals(1, no.commands.size());
        }
        for (String error : new String[] {"Error: Activity not started", "Starting: Intent\nError type 3\nMissing activity"}) {
            Device bad = new Device(); bad.command = cmd -> new Shell.Result(0, error);
            assertFalse(AppCommand.launch(11, 42, PKG, ".Main", bad).ok());
        }
        Device warning = new Device(); warning.command = cmd -> new Shell.Result(0, "Warning: intent delivered to running instance.");
        assertTrue(AppCommand.launch(11, 42, PKG, ".Main", warning).ok());
        Device denied = new Device(); denied.command = cmd -> new Shell.Result(-1, "denied");
        assertFalse(AppCommand.launch(11, 42, PKG, "", denied).ok());
    }

    @Test public void profileLockingDuringResolutionPreventsLaunch() {
        Device device = new Device();
        device.command = command -> { device.unlocked = false; return new Shell.Result(0, PKG + "/.Main"); };
        assertThrows(IllegalStateException.class, () -> AppCommand.launch(11, 42, PKG, "", device));
        assertEquals(1, device.commands.size());
        assertTrue(device.commands.get(0).startsWith("cmd package resolve-activity --brief --user 11"));
    }

    @Test public void shellMetacharactersRemainQuotedData() throws Exception {
        Device d = new Device();
        AppCommand.launch(11, 42, PKG, ".Main'; echo unsafe", d);
        assertEquals(LAUNCH.replace("/.Main'", "/.Main'\\''; echo unsafe'"), d.commands.get(0));
        ProfileStart.apkPath = "/data/app/apk's base.apk";
        String command = AppCommand.command("launch", 11, 42, PKG, ".Main'; echo unsafe");
        assertTrue(command.startsWith("CLASSPATH='/data/app/apk'\\''s base.apk'"));
        assertTrue(command.endsWith("'.Main'\\''; echo unsafe'"));
    }

    @Test public void backgroundReadsAndChangesDoNotStartOrStopProfiles() throws Exception {
        Device d = new Device(); d.unlocked = false;
        BackgroundPolicy.State original = AppCommand.read(11, 42, PKG, d);
        assertEquals(original.withMode(1), AppCommand.set(11, 42, PKG, original, 1, d));
        assertEquals(1, d.writes); assertEquals(0, d.starts); assertEquals(0, d.unlocks); assertTrue(d.commands.isEmpty());
    }

    @Test public void backgroundRefusesSharedUidOverridesAndStaleState() {
        Device shared = new Device(); shared.target = new AppCommand.Target(UID, 1234, true, true);
        assertThrows(IllegalStateException.class, () -> AppCommand.read(11, 42, PKG, shared));
        Device system = new Device(); system.target = new AppCommand.Target(1101000, 1234, true, false);
        assertThrows(IllegalStateException.class, () -> AppCommand.read(11, 42, PKG, system));
        Device override = new Device(); override.state = new BackgroundPolicy.State(UID, 1234, 0, 0);
        assertThrows(IllegalStateException.class, () -> AppCommand.set(11, 42, PKG, override.state, 1, override));
        assertEquals(0, override.writes);
        Device drift = new Device();
        assertThrows(IllegalStateException.class, () -> AppCommand.set(11, 42, PKG, drift.state.withMode(4), 1, drift));
        assertEquals(0, drift.writes);
        Device replaced = new Device(); replaced.state = new BackgroundPolicy.State(UID + 1, 1234, 0, -1);
        assertThrows(IllegalStateException.class, () -> AppCommand.read(11, 42, PKG, replaced));
    }

    @Test public void backgroundVerifiesResultAndIdentityAfterMutation() {
        Device ignored = new Device() { @Override public void background(int uid, String pkg, int mode) { writes++; } };
        assertThrows(IllegalStateException.class, () -> AppCommand.set(11, 42, PKG, ignored.state, 1, ignored));
        Device recycled = new Device(); recycled.recycledAt = 3;
        assertThrows(IllegalStateException.class, () -> AppCommand.set(11, 42, PKG, recycled.state, 1, recycled));
        assertEquals(1, recycled.writes);
    }
}
