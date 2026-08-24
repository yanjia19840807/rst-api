package com.cmacgm.gbs.rst.api.workflow.domain;

/**
 * Runtime state of the process. How it ended lives on {@link TaskStatus}.
 */
public enum ProcessStatus {
    OPEN,
    FINISHED;

    /**
     * Whether a review node is still waiting.
     *
     * @return true when the process is running
     */
    public boolean isOpen() {
        return this == OPEN;
    }
}
