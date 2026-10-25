package io.me.exposed.snapshot;

public interface ExposedSnapshotHolder {

    /** Creates the snapshot on first use. */
    ExposedSnapshot mexposed$snapshot();

    /** The snapshot if one was ever requested, otherwise null. */
    ExposedSnapshot mexposed$peekSnapshot();
}
