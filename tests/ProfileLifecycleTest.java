package com.lixingchi.appclone;

import java.util.ArrayDeque;
import java.util.List;
import java.util.function.Function;

/** Exercises the real lifecycle/launch paths without root or a device. */
public final class ProfileLifecycleTest {
    private static final String USERS = "6 users:\n\n"
            + "0: id=0, name=Owner, type=full.SYSTEM, flags=INITIALIZED (running)\n"
            + "1: id=12, name=Second, type=profile.CLONE, flags=PROFILE|INITIALIZED (parentId=0) (running)\n"
            + "2: id=11, name=  First (running)  , type=android.os.usertype.profile.CLONE, flags=PROFILE|INITIALIZED (parentId=0)\n"
            + "3: id=13, name=Work, type=profile.MANAGED, flags=PROFILE (parentId=0) (running)\n"
            + "4: id=14, name=Private, type=profile.PRIVATE, flags=PROFILE (parentId=0)\n"
            + "5: id=15, name=Other, type=CLONE, flags=PROFILE (parentId=0)\n";
    private static final Apps.App APP = new Apps.App("Example", "com.example.app", ".Main");
    private static final String LAUNCH = "am start --user 11 -a android.intent.action.MAIN"
            + " -c android.intent.category.LAUNCHER -n 'com.example.app/.Main'";

    public static void main(String[] args) {
        List<Clones.Clone> clones = Clones.parse(USERS);
        check(clones.size() == 2, "only exact full/short clone types");
        check(clones.get(0).id == 11 && clones.get(1).id == 12, "sorted ids");
        check("  First (running)  ".equals(clones.get(0).name), "verbatim name");
        check(!clones.get(0).running && clones.get(1).running, "state is not taken from name");
        check(Clones.parse(USERS.replace("id=11", "id=99999999999999")).size() == 1,
                "reject oversized id");
        check(Clones.parse(USERS.replace("name=Work", "name=Work, type=profile.CLONE, flags=PROFILE"))
                .size() == 2, "type text in a name cannot turn a managed profile into a clone");

        Script ready = ready().then(LAUNCH, 0, "Starting: Intent");
        check(Apps.launch(11, APP, ready).ok(), "running shortcut launches without restarting");
        ready.done();

        Script cold = users().then("am get-started-user-state 11", 0, "User is not started: 11")
                .then("am start-user -w 11", 0, "Success: user started")
                .then("am get-started-user-state 11", 0, "RUNNING_UNLOCKED")
                .then(LAUNCH, 0, "Starting: Intent");
        check(Apps.launch(11, APP, cold).ok(), "stopped shortcut waits before launching");
        cold.done();

        Script start = users().then("am get-started-user-state 11", 0, "RUNNING_UNLOCKING")
                .then("am start-user -w 11", 0, "Success: user started")
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
                    .then("am start-user -w 11", 0, output)
                    .then("am get-started-user-state 11", 0, "RUNNING_LOCKED");
            check(!Apps.launch(11, APP, locked).ok(), "no launch until unlocked, even with exit 0");
            locked.done();
        }
        Script timeout = users().then("am get-started-user-state 11", 0, "User is not started: 11")
                .then("am start-user -w 11", -1, "Root command timed out");
        check(!Apps.launch(11, APP, timeout).ok(), "timeout prevents launch");
        timeout.done();
        Script stateFailure = users().then("am get-started-user-state 11", 1, "Permission denied");
        check(!Apps.launch(11, APP, stateFailure).ok(), "failed query prevents launch");
        stateFailure.done();
        Script afterStartFailure = users().then("am get-started-user-state 11", 0, "RUNNING_LOCKED")
                .then("am start-user -w 11", 0, "Success: user started")
                .then("am get-started-user-state 11", 1, "Permission denied");
        check(!Apps.launch(11, APP, afterStartFailure).ok(), "failed verification prevents launch");
        afterStartFailure.done();
        Script denied = new Script().then("cmd user list -v", 1, USERS);
        check(!Apps.launch(11, APP, denied).ok(), "failed discovery rejects even plausible output");
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

        for (String error : new String[] {"Error: Activity not started", "Starting: Intent\nError type 3\nMissing activity"}) {
            Script missing = ready().then(LAUNCH, 0, error);
            check(!Apps.launch(11, APP, missing).ok(), "am launch error with exit 0 is reported");
            missing.done();
        }
        Script warning = ready().then(LAUNCH, 0, "Warning: Activity not started, intent delivered to running instance.");
        check(Apps.launch(11, APP, warning).ok(), "already open is success");
        warning.done();
        Script invalidApp = new Script();
        check(!Apps.launch(11, new Apps.App("", null, ".Main"), invalidApp).ok(), "missing package");
        check(!Apps.launch(11, new Apps.App("", "com.example", ""), invalidApp).ok(), "empty activity");
        invalidApp.done();
        Script quoted = ready().then(LAUNCH.replace("/.Main'", "/.Main'\\''; echo unsafe'"), 1, "Invalid component");
        check(!Apps.launch(11, new Apps.App("", APP.pkg, ".Main'; echo unsafe"), quoted).ok(),
                "exported shortcut component stays one quoted shell argument");
        quoted.done();
        System.out.println("ok ProfileLifecycleTest");
    }

    private static Script users() {
        return new Script().then("cmd user list -v", 0, USERS);
    }

    private static Script ready() {
        return users().then("am get-started-user-state 11", 0, "RUNNING_UNLOCKED\n");
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
