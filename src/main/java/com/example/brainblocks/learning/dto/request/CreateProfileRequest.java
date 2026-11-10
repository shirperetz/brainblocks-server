package com.example.brainblocks.learning.dto.request;

/**
 * Body for {@code POST /api/learn/profiles}. The child types their name and picks an avatar
 * on the character-selection screen.
 */
public class CreateProfileRequest {

    private String name;
    private String avatar;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
}
