package com.cmacgm.gbs.rst.api.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class MicrosoftGraphPathsTest {

    @Test
    void siteIdFromWebUrl_usesHostnameAndPath() {
        assertEquals(
                "cmacgmgroup.sharepoint.com:/sites/CMA-SharedKPIAutomation",
                MicrosoftGraphPaths.siteIdFromWebUrl(
                        "https://cmacgmgroup.sharepoint.com/sites/CMA-SharedKPIAutomation"));
    }

    @Test
    void folderPath_joinsPrefixAndDataInput() {
        assertEquals(
                "3.Production/Data Input/MyHR",
                MicrosoftGraphPaths.folderPath("3.Production", MicrosoftGraphPaths.MY_HR_FOLDER));
    }

    @Test
    void encodeDrivePath_encodesSpaces() {
        assertEquals(
                "3.Production/Data%20Input/MyHR",
                MicrosoftGraphPaths.encodeDrivePath("3.Production/Data Input/MyHR"));
    }

    @Test
    void encodeDrivePath_encodesUatRstOutputFolder() {
        assertEquals(
                "4.RST/2.UAT/rst-graph-write-probe.txt",
                MicrosoftGraphPaths.encodeDrivePath(
                        MicrosoftGraphPaths.folderPath("4.RST/2.UAT", "rst-graph-write-probe.txt")));
    }

    @Test
    void encodeDrivePath_rejectsBlank() {
        assertThrows(IllegalArgumentException.class, () -> MicrosoftGraphPaths.encodeDrivePath("  "));
    }
}
