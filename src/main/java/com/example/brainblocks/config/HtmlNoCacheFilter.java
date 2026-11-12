package com.example.brainblocks.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 2026-11-12 (Shir: "after the 30-minute logout I log in and it sends me straight to the exercises"):
 * the page itself (index.html for every React route) went out with no cache rule, so a phone could keep
 * showing an older version for a while after an update - the morning's "log in -> home screen" fix
 * included. The page now always asks the server first ("no-cache" = revalidate, cheap with
 * Last-Modified); the files under /assets/ have a content hash in their name, so they may stay cached.
 */
@Component
public class HtmlNoCacheFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!path.startsWith("/api/")) {
            if (path.startsWith("/assets/")) {
                response.setHeader("Cache-Control", "public, max-age=31536000, immutable");
            } else {
                response.setHeader("Cache-Control", "no-cache");
            }
        }
        chain.doFilter(request, response);
    }
}
