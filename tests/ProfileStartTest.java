package com.lixingchi.appclone;

import org.junit.Test;
import static org.junit.Assert.*;

/** The root startup policy with a fake Android boundary; no su, credentials or device. */
public final class ProfileStartTest {
    private static final String CLONE = "android.os.usertype.profile.CLONE";

    private static final class Device implements ProfileStart.Device {
        boolean owner = true, ready, startAccepted = true, cached = true, unlockReady = true;
        boolean autoUnlock, readyAfterCache;
        int starts, unlocks, checks, recycledAt, polls, readyAtPoll;
        Exception unlockError;

        @Override public long verify(int user) {
            checks++;
            return ProfileStart.identity(user, 11, checks == recycledAt ? 17 : 16, CLONE, 0, false);
        }
        @Override public boolean unlocked(int user) {
            if (user == 0) return owner;
            polls++;
            return ready || (readyAfterCache && unlocks > 0) || (readyAtPoll > 0 && polls >= readyAtPoll);
        }
        @Override public boolean start(int user) {
            assertEquals(11, user); starts++;
            if (autoUnlock) ready = true;
            return startAccepted;
        }
        @Override public boolean unlockCached(int user) throws Exception {
            assertEquals(11, user); unlocks++;
            if (unlockError != null) throw unlockError;
            if (cached && unlockReady) ready = true;
            return cached;
        }
    }

    @Test public void unlockedProfileDoesNotRestartOrRequestCredentials() throws Exception {
        Device device = new Device(); device.ready = true;
        ProfileStart.start(11, device, 0);
        assertEquals(0, device.starts); assertEquals(0, device.unlocks);
    }

    @Test public void securedProfileStartsThenUsesCachedChallengeOnce() throws Exception {
        Device device = new Device();
        ProfileStart.start(11, device, 0);
        assertEquals(1, device.starts); assertEquals(1, device.unlocks);
        assertEquals(4, device.checks); assertTrue(device.ready);
    }

    @Test public void automaticUnlockDoesNotRequireACachedCredential() throws Exception {
        Device device = new Device(); device.autoUnlock = true;
        ProfileStart.start(11, device, 0);
        assertEquals(0, device.unlocks);
        device = new Device(); device.cached = false; device.readyAfterCache = true;
        ProfileStart.start(11, device, 0);
        assertEquals(1, device.unlocks);
        device = new Device(); device.cached = false; device.readyAtPoll = 4;
        ProfileStart.start(11, device, 5_000);
        assertEquals(4, device.polls); assertEquals(1, device.unlocks);
    }

    @Test public void missingCredentialAndSlowUnlockAreNotSuccess() {
        Device device = new Device(); device.cached = false;
        Exception error = assertThrows(IllegalStateException.class, () -> ProfileStart.start(11, device, 0));
        assertTrue(error.getMessage().contains("PIN, pattern or password"));
        assertEquals(1, device.unlocks);
        Device slow = new Device(); slow.unlockReady = false;
        error = assertThrows(IllegalStateException.class, () -> ProfileStart.start(11, slow, 0));
        assertTrue(error.getMessage().contains("did not become ready"));
        assertEquals(1, slow.unlocks);
    }

    @Test public void ownerLockAndStartFailurePreventCredentialRequests() {
        Device locked = new Device(); locked.owner = false;
        assertThrows(IllegalStateException.class, () -> ProfileStart.start(11, locked, 0));
        assertEquals(0, locked.starts); assertEquals(0, locked.unlocks);
        Device denied = new Device(); denied.startAccepted = false;
        assertThrows(IllegalStateException.class, () -> ProfileStart.start(11, denied, 0));
        assertEquals(1, denied.starts); assertEquals(0, denied.unlocks);
    }

    @Test public void recycledProfileIsRejectedAtEveryTransition() {
        for (int changedAt : new int[] {2, 3, 4}) {
            Device device = new Device(); device.recycledAt = changedAt;
            Exception error = assertThrows(IllegalStateException.class, () -> ProfileStart.start(11, device, 0));
            assertTrue(error.getMessage().contains("identity changed"));
            assertEquals(changedAt == 2 ? 0 : 1, device.starts);
            assertEquals(changedAt == 4 ? 1 : 0, device.unlocks);
        }
    }

    @Test public void invalidIdentityNeverStartsOrUnlocks() {
        assertEquals(16, ProfileStart.identity(11, 11, 16, CLONE, 0, false));
        assertThrows(IllegalStateException.class, () -> ProfileStart.identity(0, 0, 0, CLONE, 0, false));
        assertThrows(IllegalStateException.class, () -> ProfileStart.identity(11, 12, 16, CLONE, 0, false));
        assertThrows(IllegalStateException.class, () -> ProfileStart.identity(11, 11, -1, CLONE, 0, false));
        for (String type : new String[] {null, "profile.CLONE", "android.os.usertype.profile.MANAGED", "android.os.usertype.profile.PRIVATE"}) {
            assertThrows(IllegalStateException.class, () -> ProfileStart.identity(11, 11, 16, type, 0, false));
        }
        assertThrows(IllegalStateException.class, () -> ProfileStart.identity(11, 11, 16, CLONE, 10, false));
        assertThrows(IllegalStateException.class, () -> ProfileStart.identity(11, 11, 16, CLONE, 0, true));
        Device device = new Device();
        assertThrows(IllegalArgumentException.class, () -> ProfileStart.start(-1, device, 0));
        assertEquals(0, device.checks); assertEquals(0, device.starts); assertEquals(0, device.unlocks);
    }

    @Test public void missingAndroidApiFailsWithoutPretendingReady() {
        Device device = new Device(); device.unlockError = new NoSuchMethodException("unsupported firmware");
        assertThrows(NoSuchMethodException.class, () -> ProfileStart.start(11, device, 0));
        assertFalse(device.ready); assertEquals(1, device.unlocks);
    }

    @Test public void startupCommandQuotesTheInstalledApkPath() {
        String previous = ProfileStart.apkPath;
        try {
            ProfileStart.apkPath = "/data/app/apk's base.apk";
            assertEquals("CLASSPATH='/data/app/apk'\\''s base.apk' app_process /system/bin com.lixingchi.appclone.ProfileStart 11",
                    ProfileStart.command(11));
            assertThrows(IllegalStateException.class, () -> ProfileStart.command(0));
            ProfileStart.apkPath = null;
            assertThrows(IllegalStateException.class, () -> ProfileStart.command(11));
        } finally { ProfileStart.apkPath = previous; }
    }
}
