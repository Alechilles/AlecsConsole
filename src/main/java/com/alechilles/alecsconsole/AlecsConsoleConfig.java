package com.alechilles.alecsconsole;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

import java.util.Objects;

/** Runtime settings for the bounded live and historical log views. */
public final class AlecsConsoleConfig {
    public static final BuilderCodec<AlecsConsoleConfig> CODEC = BuilderCodec
            .builder(AlecsConsoleConfig.class, AlecsConsoleConfig::new)
            .append(new KeyedCodec<>("LiveCapacity", Codec.INTEGER),
                    (config, value) -> config.liveCapacity = value,
                    config -> config.liveCapacity)
            .add()
            .append(new KeyedCodec<>("PageSize", Codec.INTEGER),
                    (config, value) -> config.pageSize = value,
                    config -> config.pageSize)
            .add()
            .append(new KeyedCodec<>("SearchDebounceMs", Codec.INTEGER),
                    (config, value) -> config.searchDebounceMs = value,
                    config -> config.searchDebounceMs)
            .add()
            .append(new KeyedCodec<>("DiskResultLimit", Codec.INTEGER),
                    (config, value) -> config.diskResultLimit = value,
                    config -> config.diskResultLimit)
            .add()
            .append(new KeyedCodec<>("ClientLogDirectory", Codec.STRING),
                    (config, value) -> config.clientLogDirectory = value,
                    config -> config.clientLogDirectory)
            .add()
            .build();

    private int liveCapacity = 5_000;
    private int pageSize = 200;
    private int searchDebounceMs = 300;
    private int diskResultLimit = 2_000;
    private String clientLogDirectory;

    public int getLiveCapacity() {
        return liveCapacity;
    }

    public int getPageSize() {
        return pageSize;
    }

    public int getSearchDebounceMs() {
        return searchDebounceMs;
    }

    public int getDiskResultLimit() {
        return diskResultLimit;
    }

    public String getClientLogDirectory() {
        return clientLogDirectory;
    }

    public boolean validate() {
        int originalLiveCapacity = liveCapacity;
        int originalPageSize = pageSize;
        int originalSearchDebounceMs = searchDebounceMs;
        int originalDiskResultLimit = diskResultLimit;
        String originalClientLogDirectory = clientLogDirectory;
        liveCapacity = clamp(liveCapacity, 100, 50_000);
        pageSize = clamp(pageSize, 25, 500);
        searchDebounceMs = clamp(searchDebounceMs, 100, 2_000);
        diskResultLimit = clamp(diskResultLimit, 100, 20_000);
        clientLogDirectory = blankToNull(clientLogDirectory);
        return originalLiveCapacity != liveCapacity
                || originalPageSize != pageSize
                || originalSearchDebounceMs != searchDebounceMs
                || originalDiskResultLimit != diskResultLimit
                || !Objects.equals(originalClientLogDirectory, clientLogDirectory);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
