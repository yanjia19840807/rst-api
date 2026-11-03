package com.cmacgm.gbs.rst.api.geography.api.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Creates or replaces a Region or Cluster.
 *
 * @param name group name
 * @param countries customer countries from the fixed catalog
 */
public record SaveGeoGroupRequest(
        @NotBlank @Size(max = 120) String name,
        @NotNull List<@NotBlank @Size(max = 120) String> countries) {
}
