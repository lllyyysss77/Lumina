package com.lumina.service;

import com.lumina.entity.Setting;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 系统级开关标志的缓存读取服务。
 *
 * <p>用于 relay 热路径（{@code /v1/**}, {@code /v1beta/**}）快速判断
 * "自用模式" 等系统开关，避免每次请求都查 DB。</p>
 *
 * <p>缓存策略：TTL 失效 + 写操作显式失效。TTL 保证即使未显式失效，
 * 更改也会在短时间内生效；显式失效让 UI 修改立即可见。</p>
 */
@Slf4j
@Service
public class SystemFlagService {

    /** 自用模式（开启后 /v1/** 和 /v1beta/** 跳过 API Key 校验） */
    public static final String KEY_SELF_USE_MODE = "self_use_mode";

    /** TTL：即便没显式失效缓存，最长 10 秒后也会重新读库 */
    private static final long CACHE_TTL_MS = 10_000L;

    @Autowired
    private SettingService settingService;

    private final AtomicReference<CachedFlag> selfUseModeCache = new AtomicReference<>();

    /**
     * 判断自用模式是否开启。命中缓存时 O(1)，未命中时读 DB 并写缓存。
     */
    public boolean isSelfUseModeEnabled() {
        CachedFlag cached = selfUseModeCache.get();
        long now = System.currentTimeMillis();
        if (cached != null && now - cached.loadedAt < CACHE_TTL_MS) {
            return cached.value;
        }
        boolean fresh = loadFromDb(KEY_SELF_USE_MODE);
        selfUseModeCache.set(new CachedFlag(fresh, now));
        return fresh;
    }

    /**
     * 写入自用模式开关，同时失效本地缓存。
     */
    public void setSelfUseModeEnabled(boolean enabled) {
        Setting setting = new Setting();
        setting.setSettingKey(KEY_SELF_USE_MODE);
        setting.setSettingValue(Boolean.toString(enabled));
        setting.setUpdatedAt(LocalDateTime.now());
        if (settingService.getById(KEY_SELF_USE_MODE) == null) {
            setting.setCreatedAt(LocalDateTime.now());
            settingService.save(setting);
        } else {
            settingService.updateById(setting);
        }
        selfUseModeCache.set(new CachedFlag(enabled, System.currentTimeMillis()));
        log.info("Self-use mode updated: enabled={}", enabled);
    }

    /**
     * 显式失效缓存。调用方（例如通用 setting 批量更新接口）如果不确定是否
     * 触及此 key，也可以安全地调此方法。
     */
    public void invalidate() {
        selfUseModeCache.set(null);
    }

    private boolean loadFromDb(String key) {
        try {
            Setting setting = settingService.getById(key);
            if (setting == null || setting.getSettingValue() == null) {
                return false;
            }
            return Boolean.parseBoolean(setting.getSettingValue().trim());
        } catch (Exception e) {
            log.warn("Failed to load system flag '{}', defaulting to false", key, e);
            return false;
        }
    }

    private record CachedFlag(boolean value, long loadedAt) {}
}
