package com.cmacgm.gbs.rst.api.toolkit.api.dto;

import java.util.List;

public record ToolkitListView(List<ToolkitResponse> items, List<String> pl3Names) {
}
