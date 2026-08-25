package com.cmacgm.gbs.rst.api.common.workingdays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class WeekendCodeTests {

    @Test
    void acceptsExcelNumbersAndLegacyNames() {
        assertThat(WeekendCode.parse("1")).isEqualTo(WeekendCode.SATURDAY_SUNDAY);
        assertThat(WeekendCode.parse("SAT_SUN")).isEqualTo(WeekendCode.SATURDAY_SUNDAY);
        assertThat(WeekendCode.parse("11")).isEqualTo(WeekendCode.SUNDAY_ONLY);
        assertThat(WeekendCode.storedValue("FRI_SAT")).isEqualTo("7");
    }

    @Test
    void rejectsBlankAndUnknownCodes() {
        assertThatThrownBy(() -> WeekendCode.parse(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WeekendCode.parse(" "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WeekendCode.parse("99"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WeekendCode.parse("NONE"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
