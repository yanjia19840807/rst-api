package com.cmacgm.gbs.rst.api.timesheet.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TimesheetSharePointPropertiesTest {

    @Test
    void derivesChildFoldersFromRoot() {
        TimesheetSharePointProperties folders = new TimesheetSharePointProperties(null, null, "4.RST/2.UAT");
        assertEquals("4.RST/2.UAT/Daily", folders.dailyFolder());
        assertEquals("4.RST/2.UAT/Monthly", folders.monthlyFolder());
        assertEquals("4.RST/2.UAT/Template", folders.templateFolder());
        assertEquals("4.RST/2.UAT/Manual", folders.manualFolder());
    }

    @Test
    void blankRoot_usesUatDefault() {
        assertEquals("4.RST/2.UAT/Template", new TimesheetSharePointProperties(null, null, null).templateFolder());
    }
}
