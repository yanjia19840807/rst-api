package com.cmacgm.gbs.rst.api.mail.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MailNotificationServiceTests {

    @Test
    void bodyEscapesHtmlAndIncludesComments() {
        String html = MailNotificationService.body(
                "Your Exercise was returned.", null, "<script>alert(1)</script>");

        assertThat(html).contains("Your Exercise was returned.");
        assertThat(html).contains("&lt;script&gt;alert(1)&lt;/script&gt;");
        assertThat(html).doesNotContain("<script>alert(1)</script>");
    }
}
