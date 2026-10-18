package com.lixingchi.appclone;

/**
 * Target facts for enabling the AOSP clone-profile subsystem.
 *
 * <p>AOSP Android 13+ already ships the clone-profile user type
 * ({@value #CLONE_TYPE}), but the stock limits are small: the CLONE type is
 * capped at a few profiles in total and one per parent, the device supports one
 * user, and only three users may run at once. These hooks raise all four so the
 * device behaves like Moto's app clone.
 */
public final class ClonePolicy {
    public static final String CLONE_TYPE = "android.os.usertype.profile.CLONE";
    /**
     * Clone quota/count and running-user budget. A large positive number, not the
     * AOSP -1 "unlimited" sentinel: Android 17 computes
     * {@code getMaxAllowed() - currentOfType} and never checks the sentinel.
     */
    public static final int LIMIT = 21472;
    public static final int MIN_SDK = 33;

    private ClonePolicy() {}

    public static boolean supported(int sdk) {
        return sdk >= MIN_SDK;
    }

    public static boolean isCloneType(String userTypeName) {
        return CLONE_TYPE.equals(userTypeName);
    }
}
