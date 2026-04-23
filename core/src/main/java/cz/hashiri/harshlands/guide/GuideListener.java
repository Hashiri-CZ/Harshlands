/*
    Copyright (C) 2026  Hashiri_
    GNU GPL v3.
 */
package cz.hashiri.harshlands.guide;

import cz.hashiri.harshlands.HLPlugin;
import org.bukkit.event.Listener;

/**
 * Stub — full implementation added in Task 8.
 */
public class GuideListener implements Listener {

    private final HLPlugin plugin;
    private final GuideModule module;

    public GuideListener(HLPlugin plugin, GuideModule module) {
        this.plugin = plugin;
        this.module = module;
    }
}
