package com.example.brainblocks.config;

/** Test access to the package-private "own device" rule. */
public final class LearnerAuthConfigAccess {
    private LearnerAuthConfigAccess() {}

    public static boolean ownDevice(String ip) {
        return LearnerAuthConfig.ownDevice(ip);
    }
}
