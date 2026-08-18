package com.cmacgm.gbs.rst.api.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.cmacgm.gbs.rst.api.graph.MicrosoftGraphModels.GraphDriveItem;

import tools.jackson.databind.json.JsonMapper;

class MicrosoftGraphModelsTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void driveItem_readsFolderAndDownloadUrl() {
        GraphDriveItem item = mapper.readValue(
                """
                {
                  "id": "abc",
                  "name": "MyHR.xlsx",
                  "webUrl": "https://example",
                  "size": 12,
                  "file": { "mimeType": "application/vnd.ms-excel" },
                  "@microsoft.graph.downloadUrl": "https://download"
                }
                """,
                GraphDriveItem.class);
        assertEquals("abc", item.id());
        assertTrue(item.isFile());
        assertFalse(item.isFolder());
        assertEquals("https://download", item.downloadUrl());
        assertEquals("application/vnd.ms-excel", item.file().mimeType());
    }
}
