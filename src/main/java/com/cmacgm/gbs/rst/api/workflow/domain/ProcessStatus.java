package com.cmacgm.gbs.rst.api.workflow.domain;

/**
 * Case-level runtime state. A visit outcome lives on {@link TaskStatus}.
 * {@code OPEN} includes returned / withdrawn revision; {@code FINISHED} is
 * LTH approve, or an Exercise that was deleted.
 */
public enum ProcessStatus {
    OPEN,
    FINISHED;

    /**
     * Whether the case is still alive (not terminally approved).
     *
     * @return true when the process may still resume
     */
    public boolean isOpen() {
        return this == OPEN;
    }
}
