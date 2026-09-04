package com.cmacgm.gbs.rst.api.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RstSharePointPropertiesTest {

    @Test
    void derivesChildFoldersFromRoot() {
        RstSharePointProperties folders = new RstSharePointProperties("4.RST/2.UAT");
        assertEquals("4.RST/2.UAT/Daily", folders.dailyFolder());
        assertEquals("4.RST/2.UAT/Monthly", folders.monthlyFolder());
        assertEquals("4.RST/2.UAT/Template", folders.templateFolder());
        assertEquals("4.RST/2.UAT/Manual", folders.manualFolder());
        assertEquals("4.RST/2.UAT/Process", folders.processFolder());
    }

    @Test
    void blankRoot_usesUatDefault() {
        assertEquals("4.RST/2.UAT/Template", new RstSharePointProperties(null).templateFolder());
    }
}
