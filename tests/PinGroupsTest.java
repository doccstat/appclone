package com.lixingchi.appclone;

import java.util.Arrays;
import java.util.List;

/** Standalone check for app-first pin grouping; run by {@code build.py --self-check}. */
public final class PinGroupsTest {
    public static void main(String[] args) {
        Apps.App maps = new Apps.App("Maps", "com.example.maps", "MapsActivity");
        Apps.App sameMaps = new Apps.App("Maps", "com.example.maps", "MapsActivity");
        Apps.App mail = new Apps.App("Mail", "com.example.mail", "MailActivity");
        List<CloneApps.Group> groups = CloneApps.groups(Arrays.asList(
                new CloneApps.Target(11, "Work", maps),
                new CloneApps.Target(10, "Personal", sameMaps),
                new CloneApps.Target(11, "Work", mail)));

        check(groups.size() == 2, "apps are grouped");
        check("Mail".equals(groups.get(0).app.label), "apps sort by label");
        check("Maps".equals(groups.get(1).app.label), "Maps is second");
        check(groups.get(1).targets.size() == 2, "Maps has two profiles");
        check("Personal".equals(groups.get(1).targets.get(0).name), "profiles sort by name");
        check("Work".equals(groups.get(1).targets.get(1).name), "Work is second");
        check(groups.get(0).targets.size() == 1, "Mail has one profile");
        System.out.println("ok PinGroupsTest");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private PinGroupsTest() {}
}
