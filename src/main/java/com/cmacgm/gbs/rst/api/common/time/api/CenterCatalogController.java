package com.cmacgm.gbs.rst.api.common.time.api;

import java.util.List;

import com.cmacgm.gbs.rst.api.common.time.CenterZones;
import com.cmacgm.gbs.rst.api.common.time.CenterZones.CenterZone;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Canonical GBS Center → IANA timezone catalog for the web client.
 */
@RestController
@RequestMapping("/api/v1/centers")
public class CenterCatalogController {

    /**
     * @return configured Centers and their IANA zones
     */
    @GetMapping
    public List<CenterZone> list() {
        return CenterZones.catalog();
    }
}
