package com.example.brainblocks.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** 2026-11-25: which files the browser may keep, and for how long. */
class HtmlNoCacheFilterTest {

    private String cacheFor(String path) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", path);
        MockHttpServletResponse res = new MockHttpServletResponse();
        new HtmlNoCacheFilter().doFilter(req, res, new MockFilterChain());
        return res.getHeader("Cache-Control");
    }

    @Test
    void eachKindOfFile() throws Exception {
        assertThat(cacheFor("/assets/index-abc.js")).contains("immutable");
        assertThat(cacheFor("/mascot/idle-01.webp")).isEqualTo("public, max-age=604800");
        assertThat(cacheFor("/audio/island/hello.mp3")).isEqualTo("public, max-age=604800");
        assertThat(cacheFor("/")).isEqualTo("no-cache");
        assertThat(cacheFor("/privacy")).isEqualTo("no-cache");
        assertThat(cacheFor("/api/ping")).isNull();
    }
}
