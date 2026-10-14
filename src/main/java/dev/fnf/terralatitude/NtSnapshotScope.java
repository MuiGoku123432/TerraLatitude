package dev.fnf.terralatitude;

import java.util.Objects;

/** Redirected calculator reads see this world's snapshot; unrelated NT callers retain globals. */
public final class NtSnapshotScope implements AutoCloseable {
    private static final ThreadLocal<NtSettings> ACTIVE = new ThreadLocal<>();
    private final NtSettings previous;
    private boolean closed;
    private NtSnapshotScope(NtSettings settings) {
        previous = ACTIVE.get();
        ACTIVE.set(Objects.requireNonNull(settings, "NT snapshot"));
    }
    public static NtSnapshotScope enter(NtSettings settings) { return new NtSnapshotScope(settings); }
    public static double number(String key, double original) {
        NtSettings settings = ACTIVE.get();
        return settings == null ? original : settings.number(key);
    }
    public static boolean active() { return ACTIVE.get() != null; }
    @Override public void close() {
        if (closed) return;
        closed = true;
        if (previous == null) ACTIVE.remove(); else ACTIVE.set(previous);
    }
}
