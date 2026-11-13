package com.example.brainblocks.learning.controller;

import com.example.brainblocks.config.LearnerAuthConfig;
import com.example.brainblocks.learning.service.AccessService;
import com.example.brainblocks.learning.service.IslandShopService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/** 2026-11-13: the child's 3D island - coins, what is built, buying, upgrading, land, treasure chests. */
@RestController
public class IslandShopController {

    private final IslandShopService shop;
    private final AccessService access;

    public IslandShopController(IslandShopService shop, AccessService access) {
        this.shop = shop;
        this.access = access;
    }

    @GetMapping("/api/learn/profiles/{id}/my-island")
    public Map<String, Object> state(@PathVariable Long id, @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        if (!id.equals(me)) access.requireCanSee(me, id); // the child, or her teacher / the admin (look only)
        return shop.state(id);
    }

    private static void mine(Long id, Long me) {
        if (!id.equals(me)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found");
    }

    private static int slotOf(Map<String, Object> body) {
        Object s = body.get("slot");
        return s instanceof Number n ? n.intValue() : -1;
    }

    @PostMapping("/api/learn/profiles/{id}/my-island/build")
    public Map<String, Object> build(@PathVariable Long id, @RequestBody Map<String, Object> body,
                                     @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        mine(id, me);
        return shop.build(id, slotOf(body), String.valueOf(body.get("kind")));
    }

    @PostMapping("/api/learn/profiles/{id}/my-island/upgrade")
    public Map<String, Object> upgrade(@PathVariable Long id, @RequestBody Map<String, Object> body,
                                       @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        mine(id, me);
        return shop.upgrade(id, slotOf(body));
    }

    @PostMapping("/api/learn/profiles/{id}/my-island/remove")
    public Map<String, Object> remove(@PathVariable Long id, @RequestBody Map<String, Object> body,
                                      @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        mine(id, me);
        return shop.remove(id, slotOf(body));
    }

    @PostMapping("/api/learn/profiles/{id}/my-island/land")
    public Map<String, Object> land(@PathVariable Long id, @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        mine(id, me);
        return shop.buyLand(id);
    }

    @PostMapping("/api/learn/profiles/{id}/my-island/chest")
    public Map<String, Object> chest(@PathVariable Long id, @RequestBody Map<String, Object> body,
                                     @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        mine(id, me);
        return shop.openChest(id, String.valueOf(body.get("isleKey")));
    }
}
