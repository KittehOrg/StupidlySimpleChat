/*
 * Copyright 2013 Matt Baxter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http:www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.kitteh.sschat;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class StupidlySimple extends JavaPlugin implements Listener {

    private class Prefix implements Comparable<Prefix> {

        private final String name;
        private final int priority;
        private final String prefix;
        private final String permission;

        public Prefix(String name, String prefix, int priority) {
            this.name = name;
            this.prefix = prefix;
            this.priority = priority;
            this.permission = "sschat." + name.toLowerCase();
        }

        @Override
        public int compareTo(Prefix p) {
            final int diff = p.getPriority() - this.priority;
            if (diff == 0) {
                return this.name.compareTo(p.getName());
            }
            return diff;
        }

        @Override
        public boolean equals(Object o) {
            if ((o == null) || !(o instanceof Prefix)) {
                return false;
            }
            final Prefix p = (Prefix) o;
            return this.name.equals(p.getName()) && (this.priority == p.getPriority()) && this.prefix.equals(p.getPrefix());
        }

        public String getName() {
            return this.name;
        }

        public String getPrefix() {
            return this.prefix;
        }

        public int getPriority() {
            return this.priority;
        }

        public boolean hasPermission(Player player) {
            return player.hasPermission(this.permission);
        }
    }

    private String format = "<%name%&r> %message%";
    private final List<Prefix> prefixes = new ArrayList<Prefix>();

    private final Map<String, String> assigned = new ConcurrentHashMap<String, String>();

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        final String format = this.assigned.get(event.getPlayer().getName());
        if (format != null) {
            event.setFormat(format.replace("%name%", event.getPlayer().getDisplayName()));
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        this.loadConfig();
        sender.sendMessage("Reloaded SSChat");
        return true;
    }

    @Override
    public void onEnable() {
        this.loadConfig();
        this.getServer().getPluginManager().registerEvents(this, this);
        this.getServer().getScheduler().runTaskTimer(this, new Runnable() {

            @Override
            public void run() {
                for (final Player player : StupidlySimple.this.getServer().getOnlinePlayers()) {
                    StupidlySimple.this.setPrefix(player);
                }
            }

        }, 1, 5);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        this.setPrefix(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        final String name = event.getPlayer().getName();
        this.getServer().getScheduler().runTask(this, new Runnable() {
            @Override
            public void run() {
                StupidlySimple.this.assigned.remove(name);
            }
        });
    }

    private void loadConfig() {
        final File file = new File(this.getDataFolder(), "config.yml");
        if (!file.exists()) {
            this.saveDefaultConfig();
        }
        this.reloadConfig();
        final FileConfiguration conf = this.getConfig();
        this.format = ChatColor.translateAlternateColorCodes('&', conf.getString("chatformat", this.format)).replace("%message", "%2$s");
        this.prefixes.clear();
        final ConfigurationSection prefixSection = conf.getConfigurationSection("prefixes");
        for (final String name : prefixSection.getKeys(false)) {
            final ConfigurationSection prefix = prefixSection.getConfigurationSection(name);
            this.prefixes.add(new Prefix(name, ChatColor.translateAlternateColorCodes('&', prefix.getString("prefix", "")), prefix.getInt("priority", 0)));
        }
        Collections.sort(this.prefixes);
    }

    private void setPrefix(Player player) {
        Prefix found = null;
        for (final Prefix prefix : this.prefixes) {
            if (prefix.hasPermission(player)) {
                found = prefix;
                break;
            }
        }
        String prefix;
        if (found == null) {
            prefix = "";
        } else {
            prefix = found.getPrefix();
        }
        this.assigned.put(player.getName(), this.format.replace("%prefix%", prefix));
    }

}