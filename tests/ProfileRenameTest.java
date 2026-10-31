package com.lixingchi.appclone;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.LEGACY)
@org.robolectric.annotation.SQLiteMode(org.robolectric.annotation.SQLiteMode.Mode.LEGACY)
@org.robolectric.annotation.ConscryptMode(org.robolectric.annotation.ConscryptMode.Mode.OFF)
public final class ProfileRenameTest {
    private static final class Device implements ProfileRename.Device {
        String name = "Original", type = "android.os.usertype.profile.CLONE";
        long serial = 42;
        int parent = 0, writes, reads, changedAt = -1;
        boolean ephemeral, ignoreWrite, denied;
        public long verify(int user) {
            reads++;
            return ProfileStart.identity(user, 11, reads == changedAt ? 43 : serial, type, parent, ephemeral);
        }
        public String name(int user) { assertEquals(11, user); return name; }
        public void name(int user, String value) {
            assertEquals(11, user);
            writes++;
            if (denied) throw new SecurityException("root denied");
            if (!ignoreWrite) name = value;
        }
    }

    @Test public void namesAreVerbatimAndCommandArgumentsAreQuoted() throws Exception {
        Device device = new Device();
        String name = "  Work's : $(id); 中文 🚀  ";
        ProfileRename.rename(11, 42, name, device);
        assertEquals(name, device.name);
        assertEquals(1, device.writes);
        assertEquals(3, device.reads);
        ProfileStart.apkPath = "/data/app/clone's/base.apk";
        String command = ProfileRename.command(11, 42, name);
        assertTrue(command.contains("com.lixingchi.appclone.ProfileRename 11 42 " + Clones.quote(name)));
        assertTrue(command.startsWith("CLASSPATH=" + Clones.quote(ProfileStart.apkPath)));
    }

    @Test public void invalidNamesAndIdentitiesNeverWrite() {
        for (String name : new String[] {null, "", "one\ntwo", "one\rtwo", "one\0two", new String(new char[101]).replace('\0', 'x')}) {
            Device device = new Device();
            assertThrows(IllegalArgumentException.class, () -> ProfileRename.rename(11, 42, name, device));
            assertEquals(0, device.reads);
            assertEquals(0, device.writes);
        }
        for (int id : new int[] {-1, 0, 10, 12}) {
            Device device = new Device();
            assertThrows(RuntimeException.class, () -> ProfileRename.rename(id, 42, "New", device));
            assertEquals(0, device.writes);
        }
        Device device = new Device();
        assertThrows(IllegalArgumentException.class, () -> ProfileRename.rename(11, -1, "New", device));
        assertEquals(0, device.writes);
    }

    @Test public void checksTypeParentEphemeralAndSerialBeforeWrite() {
        for (int scenario = 0; scenario < 5; scenario++) {
            Device device = new Device();
            if (scenario == 0) device.type = "android.os.usertype.profile.MANAGED";
            if (scenario == 1) device.parent = 10;
            if (scenario == 2) device.ephemeral = true;
            if (scenario == 3) device.serial = 43;
            if (scenario == 4) device.changedAt = 2;
            assertThrows(IllegalStateException.class, () -> ProfileRename.rename(11, 42, "New", device));
            assertEquals(0, device.writes);
        }
    }

    @Test public void failureAndUnverifiedReadbackNeverReplayTheWrite() {
        for (int scenario = 0; scenario < 3; scenario++) {
            Device device = new Device();
            device.ignoreWrite = scenario == 0;
            device.denied = scenario == 1;
            device.changedAt = scenario == 2 ? 3 : -1;
            assertThrows(RuntimeException.class, () -> ProfileRename.rename(11, 42, "New", device));
            assertEquals(1, device.writes);
        }
    }

    @Test public void sameNameIsANoOpAndMaximumLengthIsAccepted() throws Exception {
        Device device = new Device();
        ProfileRename.rename(11, 42, "Original", device);
        assertEquals(0, device.writes);
        assertEquals(1, device.reads);
        String max = new String(new char[100]).replace('\0', 'x');
        ProfileRename.rename(11, 42, max, device);
        assertEquals(max, device.name);
        ProfileRename.validate("   ");
    }

    @Test public void namedBinderApiExists() throws Exception {
        Class<?> api = Class.forName("android.os.IUserManager");
        assertNotNull(api.getMethod("setUserName", int.class, String.class));
        assertNotNull(api.getMethod("getUserInfo", int.class).getReturnType().getField("name"));
    }
}
