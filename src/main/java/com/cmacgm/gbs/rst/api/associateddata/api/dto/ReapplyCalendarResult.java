package com.cmacgm.gbs.rst.api.associateddata.api.dto;

import java.util.List;

/**
 * Re-apply template response with initialization notices.
 */
public record ReapplyCalendarResult(CalendarView calendar, List<String> notices) {
}
