package com.example.brainblocks.learning.service;

import java.util.List;
import java.util.Map;

/** Test access to ClassIslandService's package-private rules. */
public final class ClassIslandServiceAccess {
    private ClassIslandServiceAccess() {}

    public static List<Map<String, Object>> buildings(int done) {
        return ClassIslandService.buildings(done);
    }

    public static Map<String, Object> next(int done) {
        return ClassIslandService.next(done);
    }
}
