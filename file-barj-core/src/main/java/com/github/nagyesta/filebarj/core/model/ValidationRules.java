package com.github.nagyesta.filebarj.core.model;

/**
 * Defines the different validation rules of the manifests.
 */
public interface ValidationRules {
    /**
     * Defines the rule set used for empty configurations and related entities as they are set up
     * before the backup could start.
     */
    interface Created extends ValidationRules {
    }

    /**
     * Defines the rule set used for configurations and related entities as they are persisted in
     * a valid state after the backup has finished. This is the same state which is expected when
     * the persisted manifests are read back from disk as we are preparing for a restore.
     */
    interface Persisted extends ValidationRules {
    }

    /**
     * Defines the rule set used for configurations and related entities as they are expected to be
     * ready for the restore to start.
     */
    interface ReadyForRestore extends ValidationRules {
    }
}
