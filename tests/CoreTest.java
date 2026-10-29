package com.lixingchi.appclone;

import org.junit.Test;

/** Keep the device-free contract checks runnable through the Android Gradle build. */
public final class CoreTest {
    @Test public void labels() { LabelsTest.main(new String[0]); }
    @Test public void diagnostics() { DiagnosticsTest.main(new String[0]); }
    @Test public void lifecycle() { ProfileLifecycleTest.main(new String[0]); }
    @Test public void shell() throws Exception { ShellTest.main(new String[0]); }
    @Test public void bootPolicy() { BootPolicyTest.main(new String[0]); }
}
