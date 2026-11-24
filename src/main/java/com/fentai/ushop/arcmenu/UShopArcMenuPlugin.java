package com.fentai.ushop.arcmenu;

import cn.superiormc.ultimateshop.gui.arc.ArcMenuBridge;

import com.fentai.arcmenu.api.ArcMenuApi;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Registers the Ore style renderer with UltimateShop's ArcMenu bridge. */
public final class UShopArcMenuPlugin extends JavaPlugin {

    private static UShopArcMenuPlugin instance;

    private OreLang lang;

    private OreRenderer renderer;

    static UShopArcMenuPlugin instance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        lang = new OreLang(this);
        ArcMenuApi api = Bukkit.getServicesManager().load(ArcMenuApi.class);
        if (api == null) {
            getLogger().severe("ArcMenu API service is unavailable; disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        if (ArcMenuBridge.arcMenuBridge == null) {
            getLogger().severe("This UltimateShop build has no ArcMenu bridge; disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        renderer = new OreRenderer(this, api);
        try {
            ArcMenuBridge.arcMenuBridge.registerRenderer(this, renderer);
            getServer().getPluginManager().registerEvents(renderer, this);
        } catch (LinkageError olderUltimateShop) {
            getLogger().severe("UltimateShop has no ArcMenuRenderer hook; update UltimateShop. Disabling.");
            renderer.close();
            renderer = null;
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (renderer != null) {
            try {
                ArcMenuBridge.arcMenuBridge.unregisterRenderer(this);
            } catch (LinkageError ignored) {
                // UltimateShop without the hook never registered us.
            }
            renderer.close();
            renderer = null;
        }
        instance = null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            lang.reload();
            sender.sendMessage(OreLang.color(lang.get(sender instanceof Player player ? player : null, "reloaded",
                "&aUltimateShop-ArcMenu reloaded.")));
            return true;
        }
        return false;
    }

    OreLang lang() {
        return lang;
    }

    OreRenderer renderer() {
        return renderer;
    }
}
