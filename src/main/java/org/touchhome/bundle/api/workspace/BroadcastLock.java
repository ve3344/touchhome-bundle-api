package org.touchhome.bundle.api.workspace;

import java.util.concurrent.TimeUnit;

public interface BroadcastLock<T> {

    T getValue();

    /**
     * Await for events from lock.
     *
     * @return true if successfully returned, false in case of exception/interruption
     */
    default boolean await(WorkspaceBlock workspaceBlock) {
        return await(workspaceBlock, 0, null);
    }

    boolean await(WorkspaceBlock workspaceBlock, int timeout, TimeUnit timeUnit);

    // signal all broadcast locks with no value
    default void signalAll() {
        signalAll(null);
    }

    // signal all broadcast locks with specified value
    void signalAll(T value);

    void addReleaseListener(String key, Runnable listener);
}
