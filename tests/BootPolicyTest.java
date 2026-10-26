package com.lixingchi.appclone;

import android.content.SharedPreferences;

import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/** Real reboot policy with memory preferences/scripted root; no boot broadcast or device needed. */
public final class BootPolicyTest {
    private static final String USERS = "3 users:\n"
            + "0: id=0, name=Owner, type=full.SYSTEM, flags=INITIALIZED (running)\n"
            + "1: id=11, name=First, type=profile.CLONE, flags=PROFILE|INITIALIZED (parentId=0)\n"
            + "2: id=12, name=Second, type=profile.CLONE, flags=PROFILE|INITIALIZED (parentId=0) (running)\n";

    public static void main(String[] args) {
        Memory memory = new Memory();
        BootPolicy policy = new BootPolicy(memory.prefs);
        check(BootPolicy.SYSTEM.equals(policy.mode()), "default is system, never start/stop implicitly");
        check(policy.begin() == -1, "default does not queue a run");
        Script none = new Script();
        policy.apply(-1, BootPolicy.SYSTEM, () -> true, none);
        none.done();
        memory.values.put("mode", "future-unknown-mode");
        check(BootPolicy.SYSTEM.equals(policy.mode()), "unknown stored mode fails to system default");
        try {
            policy.save("unknown");
            throw new AssertionError("unknown setting accepted");
        } catch (IllegalArgumentException expected) { }

        policy.save(BootPolicy.START);
        check(policy.status().contains("next reboot"), "saving does not run immediately");
        long start = policy.begin();
        Script starting = new Script().then("cmd user list -v", 0, USERS)
                .then("cmd user list -v", 0, USERS)
                .then("am get-started-user-state 11", 0, "User is not started: 11")
                .then("am start-user -w 11", 0, "Success: user started")
                .then("am get-started-user-state 11", 0, "RUNNING_UNLOCKED")
                .then("cmd user list -v", 0, USERS)
                .then("am get-started-user-state 12", 0, "RUNNING_UNLOCKED");
        policy.apply(start, BootPolicy.START, () -> true, starting);
        starting.done();
        check(policy.status().contains("2 succeeded, 0 failed, 0 pending"), "start-all summary");
        policy.apply(start, BootPolicy.START, () -> true, none);
        none.done();

        policy.save(BootPolicy.STOP);
        check(!policy.current(start, BootPolicy.START), "changing setting invalidates older work");
        long stop = policy.begin();
        boolean[] keepGoing = {true};
        Script first = new Script().then("cmd user list -v", 0, USERS)
                .then("cmd user list -v", 0, USERS)
                .then("am stop-user -w -f 11", 0, "")
                .then("am is-user-stopped 11", 0, "true");
        policy.apply(stop, BootPolicy.STOP, () -> keepGoing[0], command -> {
            Shell.Result result = first.apply(command);
            if ("am is-user-stopped 11".equals(command)) keepGoing[0] = false;
            return result;
        });
        first.done();
        check(policy.status().contains("1 succeeded, 0 failed, 1 pending"), "checkpoint survives scheduler stop");
        Script rest = new Script().then("cmd user list -v", 0, USERS)
                .then("am stop-user -w -f 12", 0, "")
                .then("am is-user-stopped 12", 0, "true");
        new BootPolicy(memory.prefs).apply(stop, BootPolicy.STOP, () -> true, rest);
        rest.done();
        check(policy.status().contains("2 succeeded, 0 failed, 0 pending"), "resume skips completed clone");

        long partial = policy.begin();
        Script failures = new Script().then("cmd user list -v", 0, USERS)
                .then("cmd user list -v", 0, USERS.replace("id=11", "id=99"))
                .then("cmd user list -v", 0, USERS)
                .then("am stop-user -w -f 12", 0, "")
                .then("am is-user-stopped 12", 0, "true");
        policy.apply(partial, BootPolicy.STOP, () -> true, failures);
        failures.done();
        check(policy.status().contains("1 succeeded, 1 failed, 0 pending")
                && policy.status().contains("no longer exists: 11"), "partial failures reported, later clones still processed");
        policy.apply(partial, BootPolicy.STOP, () -> true, none);
        none.done();

        long denied = policy.begin();
        Script noRoot = new Script().then("cmd user list -v", 1, "Root denied");
        policy.apply(denied, BootPolicy.STOP, () -> true, noRoot);
        noRoot.done();
        check(policy.status().contains("failed: Root denied"), "root failure saved");
        policy.apply(denied, BootPolicy.STOP, () -> true, none);
        none.done();
        policy.fail(policy.begin(), BootPolicy.STOP, "Cannot schedule");
        check(policy.status().contains("Cannot schedule"), "scheduling failure saved");
        policy.save(BootPolicy.SYSTEM);
        policy.apply(denied, BootPolicy.STOP, () -> true, none);
        policy.fail(denied, BootPolicy.STOP, "stale failure");
        check(!policy.status().contains("stale failure"), "old task cannot overwrite new setting's status");
        none.done();

        policy.save(BootPolicy.STOP);
        long malformed = policy.begin();
        Script badList = new Script().then("cmd user list -v", 0, "Error: list unavailable");
        policy.apply(malformed, BootPolicy.STOP, () -> true, badList);
        badList.done();
        check(policy.status().contains("Could not read the profile list"), "unreadable list is not an empty success");
        long empty = policy.begin();
        Script noClones = new Script().then("cmd user list -v", 0, "0 users:\n");
        policy.apply(empty, BootPolicy.STOP, () -> true, noClones);
        noClones.done();
        check(policy.status().contains("0 succeeded, 0 failed, 0 pending"), "empty batch finishes");
        long cancelled = policy.begin();
        policy.apply(cancelled, BootPolicy.STOP, () -> false, none);
        none.done();
        long newerBoot = policy.begin();
        check(!policy.current(cancelled, BootPolicy.STOP) && policy.current(newerBoot, BootPolicy.STOP),
                "new boot supersedes old checkpoint");
        Script changedDuringList = new Script().then("cmd user list -v", 0, USERS);
        policy.apply(newerBoot, BootPolicy.STOP, () -> true, command -> {
            Shell.Result result = changedDuringList.apply(command);
            policy.save(BootPolicy.SYSTEM);
            return result;
        });
        changedDuringList.done();
        check(policy.status().contains("Saved"), "policy change during discovery prevents mutations");

        memory.failCommit = true;
        try {
            policy.save(BootPolicy.STOP);
            throw new AssertionError("storage failure swallowed");
        } catch (IllegalStateException expected) { }
        check(BootPolicy.SYSTEM.equals(policy.mode()) && policy.status().contains("Could not save"),
                "failed save restores prior in-memory choice and reports failure");
        System.out.println("ok BootPolicyTest");
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
        void done() { check(commands.isEmpty(), "unexecuted commands: " + commands); }
    }

    private static final class Memory {
        final Map<String, Object> values = new HashMap<>();
        boolean failCommit;
        final SharedPreferences prefs = (SharedPreferences) Proxy.newProxyInstance(
                SharedPreferences.class.getClassLoader(), new Class<?>[] {SharedPreferences.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("edit")) return editor();
                    if (method.getName().startsWith("get")) {
                        Object value = values.getOrDefault(args[0], args[1]);
                        return value instanceof Set ? new HashSet<>((Set<?>) value) : value;
                    }
                    throw new AssertionError(method.getName());
                });
        SharedPreferences.Editor editor() {
            Map<String, Object> changes = new HashMap<>();
            return (SharedPreferences.Editor) Proxy.newProxyInstance(
                    SharedPreferences.Editor.class.getClassLoader(), new Class<?>[] {SharedPreferences.Editor.class},
                    (proxy, method, args) -> {
                        String name = method.getName();
                        if (name.startsWith("put")) {
                            changes.put((String) args[0], args[1] instanceof Set ? new HashSet<>((Set<?>) args[1]) : args[1]);
                            return proxy;
                        }
                        if (name.equals("remove")) { changes.put((String) args[0], null); return proxy; }
                        if (name.equals("commit")) {
                            for (Map.Entry<String, Object> entry : changes.entrySet()) {
                                if (entry.getValue() == null) values.remove(entry.getKey());
                                else values.put(entry.getKey(), entry.getValue());
                            }
                            return !failCommit;
                        }
                        throw new AssertionError(name);
                    });
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
