package com.cmacgm.gbs.rst.api.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RstSharePointPropertiesTest {

    @Test
    void derivesChildFoldersFromRoot() {
        RstSharePointProperties folders = new RstSharePointProperties("2.UAT/Data Output/RST");
        assertEquals("2.UAT/Data Output/RST/Daily", folders.dailyFolder());
        assertEquals("2.UAT/Data Output/RST/Monthly", folders.monthlyFolder());
        assertEquals("2.UAT/Data Output/RST/Template", folders.templateFolder());
        assertEquals("2.UAT/Data Output/RST/Manual", folders.manualFolder());
        assertEquals("2.UAT/Data Output/RST/Process", folders.processFolder());
    }

    @Test
    void blankRoot_usesUatDefault() {
        assertEquals("2.UAT/Data Output/RST/Template", new RstSharePointProperties(null).templateFolder());
    }
}
