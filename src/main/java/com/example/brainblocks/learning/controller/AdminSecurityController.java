package com.example.brainblocks.learning.controller;

import com.example.brainblocks.config.LearnerAuthConfig;
import com.example.brainblocks.learning.service.AccessService;
import com.example.brainblocks.learning.service.AccountService;
import com.example.brainblocks.learning.service.AdminSecondFactor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 2026-11-14: the admin's second step (passkey / backup code) and managing it. */
@RestController
@RequestMapping("/api/learn")
public class AdminSecurityController {

    private final AdminSecondFactor second;
    private final AccessService access;

    public AdminSecurityController(AdminSecondFactor second, AccessService access) {
        this.second = second;
        this.access = access;
    }

    @PostMapping("/auth/second/passkey")
    public AccountService.Session passkey(@RequestBody Map<String, String> b) {
        return second.withPasskey(b.get("ticket"), b.get("id"), b.get("clientData"), b.get("authData"), b.get("signature"));
    }

    @PostMapping("/auth/second/code")
    public AccountService.Session code(@RequestBody Map<String, String> b) {
        return second.withCode(b.get("ticket"), b.get("code"));
    }

    @GetMapping("/admin/security")
    public Map<String, Object> state(@RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        access.requireAdmin(me);
        return second.state(me);
    }

    @PostMapping("/admin/security/passkey-options")
    public Map<String, Object> options(@RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        access.requireAdmin(me);
        return second.registrationOptions(me);
    }

    @PostMapping("/admin/security/passkeys")
    public Map<String, Object> add(@RequestBody Map<String, Object> body, @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        access.requireAdmin(me);
        return second.register(me, body);
    }

    @DeleteMapping("/admin/security/passkeys/{id}")
    public Map<String, Object> remove(@PathVariable Long id, @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        access.requireAdmin(me);
        return second.removeKey(me, id);
    }

    @PostMapping("/admin/security/codes")
    public Map<String, Object> codes(@RequestBody Map<String, String> body, @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        access.requireAdmin(me);
        List<String> list = second.newCodes(me, body.get("password"));
        return Map.of("codes", list);
    }
}
