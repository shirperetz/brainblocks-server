package com.example.brainblocks.learning.repository;

import com.example.brainblocks.learning.model.AppSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppSettingRepository extends JpaRepository<AppSetting, String> {
}
