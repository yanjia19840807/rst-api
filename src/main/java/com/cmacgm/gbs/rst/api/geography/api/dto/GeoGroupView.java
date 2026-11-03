package com.cmacgm.gbs.rst.api.geography.api.dto;

import java.util.List;
import java.util.UUID;

/**
 * Region or personal Cluster shown in config and report filters.
 *
 * @param id group id
 * @param name display name
 * @param countries customer countries in this group
 */
public record GeoGroupView(UUID id, String name, List<String> countries) {
}
