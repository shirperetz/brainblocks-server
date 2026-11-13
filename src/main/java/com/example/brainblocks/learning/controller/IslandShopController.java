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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * 2026-11-13: the child's 3D island - coins, what is built, buying, upgrading, land, treasure chests,
 * and (when an island is full) more islands in other seasons ("isle" = which one, 0 = the first).
 */
@RestController
public class IslandShopController {

    private final IslandShopService shop;
    private final AccessService access;

    public IslandShopController(IslandShopService shop, AccessService access) {
        this.shop = shop;
        this.access = access;
    }

    @GetMapping("/api/learn/profiles/{id}/my-island")
    public Map<String, Object> state(@PathVariable Long id, @RequestParam(defaultValue = "0") int isle,
                                     @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        if (!id.equals(me)) access.requireCanSee(me, id); // the child, or her teacher / the admin (look only)
        return shop.state(id, isle);
    }

    private static void mine(Long id, Long me) {
        if (!id.equals(me)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found");
    }

    private static int num(Map<String, Object> body, String key, int none) {
        Object s = body.get(key);
        return s instanceof Number n ? n.intValue() : none;
    }

    private static int slotOf(Map<String, Object> body) {
        return num(body, "slot", -1);
    }

    private static int isleOf(Map<String, Object> body) {
        return num(body, "isle", 0);
    }

    @PostMapping("/api/learn/profiles/{id}/my-island/build")
    public Map<String, Object> build(@PathVariable Long id, @RequestBody Map<String, Object> body,
                                     @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        mine(id, me);
        return shop.build(id, isleOf(body), slotOf(body), String.valueOf(body.get("kind")));
    }

    @PostMapping("/api/learn/profiles/{id}/my-island/upgrade")
    public Map<String, Object> upgrade(@PathVariable Long id, @RequestBody Map<String, Object> body,
                                       @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        mine(id, me);
        return shop.upgrade(id, isleOf(body), slotOf(body));
    }

    @PostMapping("/api/learn/profiles/{id}/my-island/remove")
    public Map<String, Object> remove(@PathVariable Long id, @RequestBody Map<String, Object> body,
                                      @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        mine(id, me);
        return shop.remove(id, isleOf(body), slotOf(body));
    }

    @PostMapping("/api/learn/profiles/{id}/my-island/land")
    public Map<String, Object> land(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body,
                                    @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        mine(id, me);
        return shop.buyLand(id, body == null ? 0 : isleOf(body));
    }

    @PostMapping("/api/learn/profiles/{id}/my-island/new-isle")
    public Map<String, Object> newIsle(@PathVariable Long id, @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        mine(id, me);
        return shop.buyIsle(id);
    }

    @PostMapping("/api/learn/profiles/{id}/my-island/chest")
    public Map<String, Object> chest(@PathVariable Long id, @RequestBody Map<String, Object> body,
                                     @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        mine(id, me);
        return shop.openChest(id, String.valueOf(body.get("isleKey")));
    }
}
