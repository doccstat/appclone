package com.lixingchi.appclone;

import java.util.ArrayDeque;
import java.util.List;
import java.util.function.Function;

/** Exercises the real lifecycle paths and one-command launch wrapper without root. */
public final class ProfileLifecycleTest {
    private static final String USERS = "6 users:\n\n"
            + "0: id=0, name=Owner, type=full.SYSTEM, flags=INITIALIZED (running)\n"
            + "1: id=12, name=Second, type=profile.CLONE, flags=PROFILE|INITIALIZED (parentId=0) (running)\n"
            + "2: id=11, name=  First (running)  , type=android.os.usertype.profile.CLONE, flags=PROFILE|INITIALIZED (parentId=0)\n"
            + "3: id=13, name=Work, type=profile.MANAGED, flags=PROFILE (parentId=0) (running)\n"
            + "4: id=14, name=Private, type=profile.PRIVATE, flags=PROFILE (parentId=0)\n"
            + "5: id=15, name=Other, type=CLONE, flags=PROFILE (parentId=0)\n";
    private static final Apps.App APP = new Apps.App("Example", "com.example.app", ".Main");
    private static final String START = "CLASSPATH='/data/app/appclone/base.apk' app_process /system/bin com.lixingchi.appclone.ProfileStart 11";

    public static void main(String[] args) {
        ProfileStart.apkPath = "/data/app/appclone/base.apk";
        List<Clones.Clone> clones = Clones.parse(USERS);
        check(clones.size() == 2, "only exact full/short clone types");
        check(clones.get(0).id == 11 && clones.get(1).id == 12, "sorted ids");
        check("  First (running)  ".equals(clones.get(0).name), "verbatim name");
        check(!clones.get(0).running && clones.get(1).running, "state is not taken from name");
        check(Clones.parse(USERS.replace("id=11", "id=99999999999999")).size() == 1,
                "reject oversized id");
        check(Clones.parse(USERS.replace("name=Work", "name=Work, type=profile.CLONE, flags=PROFILE"))
                .size() == 2, "type text in a name cannot turn a managed profile into a clone");

        for (long serial : new long[] {-1, 42}) {
            String command = "CLASSPATH='/data/app/appclone/base.apk' app_process /system/bin com.lixingchi.appclone.AppCommand"
                    + " 'launch' '11' '" + serial + "' 'com.example.app' '.Main'";
            for (int code : new int[] {0, 1, -1}) {
                Script script = new Script().then(command, code, "result");
                check(Apps.launch(11, serial, APP, script).code == code, "one root command, including legacy shortcuts");
                script.done();
            }
        }
        Script ready = users().then("am get-started-user-state 11", 0, "RUNNING_UNLOCKED");
        check(Clones.setRunning(11, true, ready).ok(), "ready profile is not restarted");
        ready.done();

        Script start = users().then("am get-started-user-state 11", 0, "RUNNING_UNLOCKING")
                .then(START, 0, "Profile 11 is unlocked.")
                .then("am get-started-user-state 11", 0, "RUNNING_UNLOCKED");
        check(Clones.setRunning(11, true, start).ok(), "manual start also waits for unlock");
        start.done();

        for (int id : new int[] {11, 12}) {
            Script stop = users().then("am stop-user -w -f " + id, 0, "")
                    .then("am is-user-stopped " + id, 0, "true");
            check(Clones.setRunning(id, false, stop).ok(), "stop only the selected clone, idempotently");
            stop.done();
        }
        Script aborted = users().then("am stop-user -w -f 12", 0, "")
                .then("am is-user-stopped 12", 0, "false");
        check(!Clones.setRunning(12, false, aborted).ok(), "aborted stop is not success");
        aborted.done();
        Script stopFailure = users().then("am stop-user -w -f 12", -1, "Switch failed");
        check(!Clones.setRunning(12, false, stopFailure).ok(), "stop failure reported");
        stopFailure.done();

        for (String output : new String[] {"Success: user started", "Error: could not start user"}) {
            Script locked = users().then("am get-started-user-state 11", 0, "RUNNING_LOCKED")
                    .then(START, 0, output)
                    .then("am get-started-user-state 11", 0, "RUNNING_LOCKED");
            check(!Clones.setRunning(11, true, locked).ok(), "exit 0 does not imply unlocked");
            locked.done();
        }
        Script timeout = users().then("am get-started-user-state 11", 0, "User is not started: 11")
                .then(START, -1, "Root command timed out");
        check(!Clones.setRunning(11, true, timeout).ok(), "timeout reported");
        timeout.done();
        Script stateFailure = users().then("am get-started-user-state 11", 1, "Permission denied");
        check(!Clones.setRunning(11, true, stateFailure).ok(), "failed query prevents start");
        stateFailure.done();
        Script afterStartFailure = users().then("am get-started-user-state 11", 0, "RUNNING_LOCKED")
                .then(START, 0, "Profile 11 is unlocked.")
                .then("am get-started-user-state 11", 1, "Permission denied");
        check(!Clones.setRunning(11, true, afterStartFailure).ok(), "failed verification reported");
        afterStartFailure.done();
        Script denied = new Script().then("cmd user list -v", 1, USERS);
        check(!Clones.setRunning(11, true, denied).ok(), "failed discovery rejects even plausible output");
        denied.done();

        for (int id : new int[] {-1, 0, 13, 14, 15, 99}) {
            for (boolean running : new boolean[] {true, false}) {
                Script invalid = id <= 0 ? new Script() : users();
                check(!Clones.setRunning(id, running, invalid).ok(), "reject non-clone " + id);
                invalid.done();
            }
        }
        for (String unsafe : new String[] {USERS.replace("parentId=0", "parentId=10"),
                USERS.replace("PROFILE|INITIALIZED", "PROFILE|INITIALIZED|EPHEMERAL")}) {
            for (boolean running : new boolean[] {true, false}) {
                Script invalid = new Script().then("cmd user list -v", 0, unsafe);
                check(!Clones.setRunning(11, running, invalid).ok(), "reject unsafe lifecycle target");
                invalid.done();
            }
        }

        Script invalidApp = new Script();
        check(!Apps.launch(11, 42, new Apps.App("", null, ".Main"), invalidApp).ok(), "missing package");
        check(!Apps.launch(11, 42, new Apps.App("", "com.example", null), invalidApp).ok(), "missing activity");
        check(!Apps.launch(0, 42, APP, invalidApp).ok(), "owner rejected before root");
        check(!Apps.launch(11, -2, APP, invalidApp).ok(), "invalid serial rejected before root");
        invalidApp.done();
        System.out.println("ok ProfileLifecycleTest");
    }

    private static Script users() {
        return new Script().then("cmd user list -v", 0, USERS);
    }

    private static final class Script implements Function<String, Shell.Result> {
        final ArrayDeque<String> commands = new ArrayDeque<>();
        final ArrayDeque<Shell.Result> results = new ArrayDeque<>();

        Script then(String command, int code, String output) {
            commands.add(command);
            results.add(new Shell.Result(code, output));
            return this;
        }

        @Override public Shell.Result apply(String command) {
            check(!commands.isEmpty(), "unexpected command: " + command);
            String expected = commands.remove();
            check(expected.equals(command), "expected " + expected + ", got " + command);
            return results.remove();
        }

        void done() {
            check(commands.isEmpty(), "unexecuted commands: " + commands);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
