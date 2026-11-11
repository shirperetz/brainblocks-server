package com.example.brainblocks.learning.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A setting the admin changes from the game itself (2026-11-11: the join code). */
@Entity
@Table(name = "app_setting")
public class AppSetting {

    @Id
    @Column(name = "setting_key", length = 40)
    private String key;

    @Column(name = "setting_value", length = 200)
    private String value;

    protected AppSetting() {}

    public AppSetting(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public String getKey() { return key; }
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
}
