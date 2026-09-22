package io.depguard.shared;

import io.hypersistence.tsid.TSID;

public final class IdGenerator {

    private IdGenerator() {}

    public static String generate() {
        return TSID.Factory.getTsid().toString();
    }
}
