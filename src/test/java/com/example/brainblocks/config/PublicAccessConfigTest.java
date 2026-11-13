package com.example.brainblocks.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/** 2026-11-11 public link: per-visitor limits, and whose address counts. */
class PublicAccessConfigTest {

    @Test
    void limiterAllowsMaxPerWindow() {
        var l = new PublicAccessConfig.Limiter(3, 60_000);
        assertThat(l.allow("a", 0)).isTrue();
        assertThat(l.allow("a", 1)).isTrue();
        assertThat(l.allow("a", 2)).isTrue();
        assertThat(l.allow("a", 3)).isFalse();
        assertThat(l.allow("b", 3)).isTrue();          // another visitor
        assertThat(l.allow("a", 60_001)).isTrue();     // the window moved on
    }

    @Test
    void forwardedAddressIsTrustedOnlyFromLoopback() {
        var viaFunnel = new MockHttpServletRequest();
        viaFunnel.setRemoteAddr("127.0.0.1");
        // 2026-11-13: a visitor may send their own list; the proxy adds the real address LAST.
        viaFunnel.addHeader("X-Forwarded-For", "9.9.9.9, 203.0.113.7");
        assertThat(PublicAccessConfig.visitor(viaFunnel)).isEqualTo("203.0.113.7");

        var direct = new MockHttpServletRequest();
        direct.setRemoteAddr("198.51.100.2");
        direct.addHeader("X-Forwarded-For", "1.2.3.4"); // a forged header from outside is ignored
        assertThat(PublicAccessConfig.visitor(direct)).isEqualTo("198.51.100.2");
    }
}
