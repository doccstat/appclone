package com.lixingchi.appclone;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.function.Function;

public final class AppControlsTest {
    private static final String PKG = "com.example.app";
    private static final String USERS = "2 users:\n0: id=0, name=Owner, type=full.SYSTEM, flags=INITIALIZED (running)\n"
            + "1: id=11, name=Clone, type=profile.CLONE, flags=PROFILE|INITIALIZED (parentId=0) (running)\n";
    private static final String DUMP = "Users:\n  UserInfo{11:Clone:1010} serialNo=42 isPrimary=false parentId=0\n";
    private static final String LIST = "pm list packages --user 11";
    private static final String DISABLED = "pm list packages -d --user 11";
    private static final String HAVE = "package:" + PKG + "\n";

    @Test public void inventoryGroupsPackagesAndKeepsStoppedDisabledInstances() {
        Clones.Clone one = Clones.parse(USERS).get(0);
        Clones.Clone two = Clones.parse(USERS.replace("id=11", "id=12").replace(" (running)", "")).get(0);
        Inventory.Profile p1 = new Inventory.Profile(one, 42, new HashSet<>(Arrays.asList(PKG, "com.other")), Collections.emptySet());
        Inventory.Profile p2 = new Inventory.Profile(two, 43, Collections.singleton(PKG), Collections.singleton(PKG));
        List<Inventory.Entry> entries = Inventory.group(Arrays.asList(p1, p2), pkg ->
                new Inventory.Entry(new Apps.App(pkg, pkg, ""), false));
        assertEquals(2, entries.size());
        assertEquals(2, entries.get(0).profiles.size());
        assertTrue(Inventory.state(p2, PKG).contains("Disabled"));
        assertTrue(Inventory.state(p2, PKG).contains("stopped"));
        assertTrue(Inventory.group(Collections.emptyList(), pkg -> { throw new AssertionError(); }).isEmpty());
    }

    @Test public void namesAreVerbatimAndNumberingIsExplicit() {
        assertEquals(Arrays.asList("  A ' &  ", "  A ' &  "), Clones.names("  A ' &  ", 2, false, Collections.emptyList()));
        List<Clones.Clone> clones = Arrays.asList(new Clones.Clone(11, "A-8", ""), new Clones.Clone(12, "B-20", ""));
        assertEquals(Arrays.asList("A-9", "A-10"), Clones.names("A", 2, true, clones));
        for (int count : new int[] {0, -1, 501}) {
            try { Clones.names("A", count, false, clones); fail("no silent count clamping"); }
            catch (IllegalArgumentException expected) {}
        }
        for (String name : Arrays.asList("", "A\nB", "A\rB", "A\0B")) {
            try { Clones.names(name, 1, false, clones); fail("no silent name substitution"); }
            catch (IllegalArgumentException expected) {}
        }
    }

    @Test public void packageReadFailureIsNotEmptyInventory() {
        for (Shell.Result failure : Arrays.asList(new Shell.Result(1, ""), new Shell.Result(0, "Error: permission denied"))) {
            try { Apps.packages(11, "", command -> failure); fail("must fail closed"); }
            catch (IllegalStateException expected) {}
        }
    }

    @Test public void userListFailuresAreNotEmptyInventory() {
        for (Shell.Result failure : Arrays.asList(new Shell.Result(1, ""), new Shell.Result(0, "Error: permission denied"))) {
            try { Clones.list(command -> failure); fail("must fail closed"); }
            catch (IllegalStateException expected) {}
        }
        assertEquals(1, Clones.list(command -> new Shell.Result(0, USERS)).size());
    }

    @Test public void profileRemovalChecksIdentityAndConfirmedAbsence() {
        String ownerOnly = "1 users:\n0: id=0, name=Owner, type=full.SYSTEM, flags=INITIALIZED (running)\n";
        for (String remaining : Arrays.asList(ownerOnly, USERS, "Error: permission denied")) {
            Script script = checked().then("pm remove-user --wait 11", 0, "Success: removed user")
                    .then("cmd user list -v", 0, remaining);
            assertEquals(remaining.equals(ownerOnly), Clones.remove(11, 42, script).ok());
            script.done();
        }
        Script recycled = new Script().then("cmd user list -v", 0, USERS)
                .then("dumpsys user", 0, DUMP.replace("42", "43"));
        assertFalse(Clones.remove(11, 42, recycled).ok()); recycled.done();
        Script rejected = checked().then("pm remove-user --wait 11", 0, "Error: cannot remove user");
        assertFalse(Clones.remove(11, 42, rejected).ok()); rejected.done();
    }

    @Test public void disableEnableAndDefaultAreExactUserActions() {
        for (Apps.Action action : new Apps.Action[] {Apps.Action.DISABLE, Apps.Action.ENABLE, Apps.Action.DEFAULT}) {
            String verb = action == Apps.Action.DISABLE ? "disable-user" : action == Apps.Action.ENABLE ? "enable" : "default-state";
            String state = action == Apps.Action.DISABLE ? "disabled-user" : action == Apps.Action.ENABLE ? "enabled" : "default";
            Script script = checked().then(LIST, 0, HAVE)
                    .then("pm " + verb + " --user 11 '" + PKG + "'", 0, "Package " + PKG + " new state: " + state);
            if (action != Apps.Action.DEFAULT) script.then(DISABLED, 0, action == Apps.Action.DISABLE ? HAVE : "");
            assertTrue(Apps.change(11, 42, PKG, action, script).ok());
            script.done();
        }
    }

    @Test public void forceStopVerifiesFirstActiveUserStateNotFactoryFallback() {
        Script stopped = checked().then(LIST, 0, HAVE).then("am force-stop --user 11 '" + PKG + "'", 0, "")
                .then("dumpsys package '" + PKG + "'", 0, "  User 11: installed=true stopped=true enabled=0\n");
        assertTrue(Apps.change(11, 42, PKG, Apps.Action.FORCE_STOP, stopped).ok()); stopped.done();
        Script notStopped = checked().then(LIST, 0, HAVE).then("am force-stop --user 11 '" + PKG + "'", 0, "")
                .then("dumpsys package '" + PKG + "'", 0, "  User 11: installed=true stopped=false enabled=0\nHidden system packages:\n  User 11: stopped=true\n");
        assertFalse(Apps.change(11, 42, PKG, Apps.Action.FORCE_STOP, notStopped).ok()); notStopped.done();
    }

    @Test public void installAndRemoveVerifyPresence() {
        Script install = checked().then(LIST, 0, "").then("pm install-existing --user 11 '" + PKG + "'", 0, "installed")
                .then(LIST, 0, HAVE);
        assertTrue(Apps.change(11, 42, PKG, Apps.Action.INSTALL, install).ok()); install.done();
        Script remove = checked().then(LIST, 0, HAVE).then("pm uninstall --user 11 '" + PKG + "'", 0, "Success")
                .then(LIST, 0, "");
        assertTrue(Apps.change(11, 42, PKG, Apps.Action.REMOVE, remove).ok()); remove.done();
        Script failed = checked().then(LIST, 0, HAVE).then("pm uninstall --user 11 '" + PKG + "'", 0, "Failure")
                .then(LIST, 0, HAVE);
        assertFalse(Apps.change(11, 42, PKG, Apps.Action.REMOVE, failed).ok()); failed.done();
    }

    @Test public void rejectsUnsafeInputsAndRecycledUser() {
        for (String pkg : Arrays.asList("", "-a", "com.app;echo x", "com.app/Activity", "com.app'", null)) {
            Script script = new Script();
            assertFalse(Apps.change(11, 42, pkg, Apps.Action.DISABLE, script).ok()); script.done();
        }
        for (int id : new int[] {0, -1}) {
            Script script = new Script();
            assertFalse(Apps.change(id, 42, PKG, Apps.Action.REMOVE, script).ok()); script.done();
        }
        for (String unsafe : Arrays.asList(USERS.replace("profile.CLONE", "profile.MANAGED"),
                USERS.replace("parentId=0", "parentId=10"), USERS.replace("PROFILE|INITIALIZED", "PROFILE|EPHEMERAL"))) {
            Script script = new Script().then("cmd user list -v", 0, unsafe);
            assertFalse(Apps.change(11, 42, PKG, Apps.Action.REMOVE, script).ok()); script.done();
        }
        Script recycled = new Script().then("cmd user list -v", 0, USERS).then("dumpsys user", 0, DUMP.replace("42", "43"));
        assertFalse(Apps.change(11, 42, PKG, Apps.Action.REMOVE, recycled).ok()); recycled.done();
        Script denied = new Script().then("cmd user list -v", 1, USERS);
        assertFalse(Apps.change(11, 42, PKG, Apps.Action.DISABLE, denied).ok()); denied.done();
    }

    @Test public void rejectedOrUnchangedDisableIsNotSuccess() {
        for (boolean rejected : new boolean[] {true, false}) {
            Script script = checked().then(LIST, 0, HAVE)
                    .then("pm disable-user --user 11 '" + PKG + "'", 0,
                            rejected ? "Error: denied" : "Package " + PKG + " new state: disabled-user");
            if (!rejected) script.then(DISABLED, 0, "");
            assertFalse(Apps.change(11, 42, PKG, Apps.Action.DISABLE, script).ok()); script.done();
        }
    }

    private static Script checked() { return new Script().then("cmd user list -v", 0, USERS).then("dumpsys user", 0, DUMP); }
    private static final class Script implements Function<String, Shell.Result> {
        final ArrayDeque<String> commands = new ArrayDeque<>();
        final ArrayDeque<Shell.Result> results = new ArrayDeque<>();
        Script then(String command, int code, String output) { commands.add(command); results.add(new Shell.Result(code, output)); return this; }
        public Shell.Result apply(String command) { assertFalse("unexpected " + command, commands.isEmpty()); assertEquals(commands.remove(), command); return results.remove(); }
        void done() { assertTrue("unexecuted " + commands, commands.isEmpty()); }
    }
}
