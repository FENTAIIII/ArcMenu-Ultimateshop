package com.fentai.ushop.arcmenu;

import cn.superiormc.ultimateshop.UltimateShop;
import cn.superiormc.ultimateshop.api.ShopHelper;
import cn.superiormc.ultimateshop.gui.AbstractGUI;
import cn.superiormc.ultimateshop.gui.GUIStatus;
import cn.superiormc.ultimateshop.gui.ProductUIModel;
import cn.superiormc.ultimateshop.managers.CacheManager;
import cn.superiormc.ultimateshop.managers.ConfigManager;
import cn.superiormc.ultimateshop.managers.LanguageManager;
import cn.superiormc.ultimateshop.managers.MenuStatusManager;
import cn.superiormc.ultimateshop.objects.ObjectShop;
import cn.superiormc.ultimateshop.objects.ObjectThingRun;
import cn.superiormc.ultimateshop.objects.buttons.AbstractButton;
import cn.superiormc.ultimateshop.objects.buttons.ObjectItem;
import cn.superiormc.ultimateshop.objects.caches.ObjectCache;
import cn.superiormc.ultimateshop.objects.caches.ObjectUseTimesCache;
import cn.superiormc.ultimateshop.objects.items.GiveResult;
import cn.superiormc.ultimateshop.objects.items.TakeResult;
import cn.superiormc.ultimateshop.objects.items.prices.ObjectPrices;
import cn.superiormc.ultimateshop.objects.menus.MenuSender;
import cn.superiormc.ultimateshop.objects.menus.ObjectFavouriteMenu;
import cn.superiormc.ultimateshop.objects.menus.ObjectMenu;
import cn.superiormc.ultimateshop.objects.menus.ObjectSearchMenu;
import cn.superiormc.ultimateshop.utils.CommonUtil;
import cn.superiormc.ultimateshop.utils.TextUtil;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One UltimateShop menu shown through generated ArcMenu menus. It is UltimateShop's GUI for that menu, so menu
 * status, cooldowns and close actions keep working; ArcMenu renders and animates everything.
 */
final class OreSession extends AbstractGUI {

    final OreRenderer renderer;

    final ObjectMenu menu;

    final ObjectShop shop;

    final boolean bypass;

    final ProductUIModel controls;

    String title = "";

    /** Buttons of the current frame by slot: the menu's UI buttons plus its regular buttons. */
    Map<Integer, AbstractButton> buttons = new LinkedHashMap<>();

    final Map<Integer, ItemStack> menuItems = new LinkedHashMap<>();

    int page;

    int navPage = -1;

    String keyword = "";

    /** Search result filter: 0 for every shop, otherwise the n-th shop with hits. */
    int filter;

    boolean editingFavourites;

    ObjectItem selected;

    int amount = 1;

    private boolean finished;

    OreSession(Player player, ObjectMenu menu, ObjectShop shop, boolean bypass, boolean editing, OreRenderer renderer) {
        super(player);
        this.renderer = renderer;
        this.menu = menu;
        this.shop = shop;
        this.bypass = bypass;
        this.editingFavourites = editing;
        this.controls = new ProductUIModel(menu, player, "arcmenu");
    }

    @Override
    public ObjectMenu getMenu() {
        return menu;
    }

    boolean search() {
        return menu instanceof ObjectSearchMenu;
    }

    boolean favourites() {
        return menu instanceof ObjectFavouriteMenu;
    }

    // ------------------------------------------------------------------ lifecycle

    boolean tryOpen(boolean reopen) {
        GUIStatus previous = MenuStatusManager.menuStatusManager.getGUIStatus(player);
        AbstractGUI replaced = MenuStatusManager.menuStatusManager.getOpeningGUI(player);
        if (!MenuStatusManager.menuStatusManager.canOpenGUI(player, this, reopen)) {
            return true;
        }
        if (!canDisplayMenu()) {
            restore(previous);
            return true;
        }
        prepare();
        player.closeInventory();
        if (renderer.showPage(this, true)) {
            if (replaced instanceof OreSession old && old != this) {
                old.finishGUI();
            }
            menu.doOpenAction(player, reopen);
            return true;
        }
        restore(previous);
        return false;
    }

    private void restore(GUIStatus previous) {
        if (MenuStatusManager.menuStatusManager.getOpeningGUI(player) == this) {
            MenuStatusManager.menuStatusManager.setGUIStatus(player, previous);
        }
    }

    boolean canDisplayMenu() {
        return bypass || menu.getCondition().getAllBoolean(new ObjectThingRun(player));
    }

    boolean current() {
        return !finished && MenuStatusManager.menuStatusManager.getOpeningGUI(player) == this;
    }

    @Override
    public void constructGUI() {
        if (!current()) {
            return;
        }
        if (!canDisplayMenu()) {
            closeGUI();
            return;
        }
        prepare();
        if (selected != null) {
            renderer.showDetails(this, false);
        } else {
            renderer.showPage(this, false);
        }
    }

    @Override
    public void openGUI(boolean reopen) {
        tryOpen(reopen);
    }

    @Override
    public void closeGUI() {
        renderer.close(player);
        finishGUI();
    }

    @Override
    public void finishGUI() {
        if (finished) {
            return;
        }
        finished = true;
        super.finishGUI();
        if (ConfigManager.configManager.getLong("menu.cooldown.reopen", 3L) <= 0
            && MenuStatusManager.menuStatusManager.getOpeningGUI(player) == this) {
            MenuStatusManager.menuStatusManager.removeGUIStatus(player);
        }
    }

    /** Collects this menu's buttons, display items and title. */
    void prepare() {
        if (shop != null) {
            ObjectCache playerCache = CacheManager.cacheManager.getObjectCache(player);
            ObjectCache serverCache = CacheManager.cacheManager.serverCache;
            if (playerCache != null && serverCache != null) {
                for (ObjectItem item : shop.getProductListNotHidden(player)) {
                    ObjectUseTimesCache cache = playerCache.getUseTimesCache().get(item);
                    if (cache != null) {
                        cache.refreshTimes();
                    }
                    cache = serverCache.getUseTimesCache().get(item);
                    if (cache != null) {
                        cache.refreshTimes();
                    }
                }
            }
        }
        MenuSender sender = MenuSender.of(player, "arcmenu");
        Map<Integer, AbstractButton> regular = new LinkedHashMap<>(menu.getMenu(sender));
        menuItems.clear();
        for (Map.Entry<Integer, AbstractButton> entry : regular.entrySet()) {
            menuItems.put(entry.getKey(), entry.getValue().getDisplayItem(player, 1).getItemStack());
        }
        buttons = new LinkedHashMap<>(menu.getUiMenu(sender));
        buttons.putAll(regular);
        String raw = menu.getString("title", shop != null ? shop.getShopDisplayName() : "Shop");
        if (shop != null) {
            raw = raw.replace("{shop-name}", shop.getShopDisplayName())
                .replace("{shop-id}", shop.getShopName().replace("_", "-"));
        }
        title = plain(raw);
    }

    /** A fresh session on the same page, to come back after another UltimateShop menu took over. */
    OreSession resume() {
        OreSession next = new OreSession(player, menu, shop, bypass, editingFavourites, renderer);
        next.page = page;
        next.navPage = navPage;
        next.keyword = keyword;
        next.filter = filter;
        return next;
    }

    // ------------------------------------------------------------------ clicks and trades

    void click(int slot, ClickType type) {
        AbstractButton button = buttons.get(slot);
        if (button == null || !button.isVisibleInMenu("arcmenu") || !button.canDisplay(MenuSender.of(player, "arcmenu"))) {
            return;
        }
        if (button instanceof ObjectItem product && !visible(product)) {
            return;
        }
        button.clickEvent(type, player);
        refreshAfterAction();
    }

    void run(ObjectItem product, ProductUIModel.Action action, int buyAmount, int sellAmount) {
        if (!visible(product)) {
            return;
        }
        if (action.id().equals("select-amount")) {
            openDetails(product);
            return;
        }
        action.execute(product, player, buyAmount, sellAmount);
        refreshAfterAction();
    }

    /** Buttons and trades can open another menu; otherwise prices, limits and stock change. */
    void refreshAfterAction() {
        if (current()) {
            constructGUI();
        }
    }

    boolean visible(ObjectItem item) {
        return canDisplayMenu()
            && item.isVisibleInMenu("arcmenu")
            && item.canDisplay(MenuSender.of(player, "arcmenu"))
            && item.getShopObject().getProductNotHidden(MenuSender.of(player, "arcmenu"), item) != null;
    }

    // ------------------------------------------------------------------ details dialog

    void openDetails(ObjectItem product) {
        if (product == null || !visible(product)) {
            return;
        }
        if (selected != product) {
            selected = product;
            amount = OreSession.clamp(product.getBuyPrice().empty ? product.getDefaultSellAmount()
                : product.getDefaultBuyAmount(), maxAmount(product));
        }
        renderer.showDetails(this, true);
    }

    void closeDetails() {
        selected = null;
        renderer.showPage(this, true);
    }

    static boolean canSelectAmount(ObjectItem product) {
        return product.getBuyMore() && product.getBuyMoreMenu() != null;
    }

    static int maxAmount(ObjectItem product) {
        return canSelectAmount(product)
            ? Math.max(1, product.getBuyMoreMenu().getSection().getInt("max-amount", 64))
            : Math.max(1, Math.max(product.getDefaultBuyAmount(), product.getDefaultSellAmount()));
    }

    static int clamp(long value, int maximum) {
        return (int) Math.max(1L, Math.min(Math.max(1, maximum), value));
    }

    /** Changes the amount and updates the dialog's amount, totals and button labels in place. */
    void setAmount(long value) {
        if (selected == null) {
            return;
        }
        amount = clamp(value, maxAmount(selected));
        renderer.updateAmount(this);
    }

    // ------------------------------------------------------------------ text and prices

    String plain(String text) {
        String parsed = TextUtil.parse(TextUtil.localizeVanillaNames(
            TextUtil.withPAPI(CommonUtil.parseLang(player, text == null ? "" : text), player), player));
        String stripped = ChatColor.stripColor(parsed);
        return stripped == null ? "" : stripped.trim();
    }

    /** Text with its colours as ArcMenu codes, for tooltips. */
    String rich(String text) {
        String parsed = TextUtil.parse(TextUtil.localizeVanillaNames(
            TextUtil.withPAPI(CommonUtil.parseLang(player, text == null ? "" : text), player), player));
        return parsed == null ? "" : parsed.replace('\u00a7', '&');
    }

    String lang(String key, String fallback, String... replacements) {
        return renderer.plugin().lang().get(player, key, fallback, replacements);
    }

    /** Price text of buying count items, in the shop's own currency format. */
    String buyPrice(ObjectItem product, int count) {
        if (product.getBuyPrice().empty) {
            return null;
        }
        ObjectUseTimesCache cache = ShopHelper.getPlayerUseTimesCache(product, player);
        TakeResult result = product.getBuyPrice().take(player.getInventory(), player,
            cache == null ? 0 : cache.getBuyUseTimes(), count, true);
        return result == null ? null : plain(ObjectPrices.getDisplayNameInLine(player, count,
            result.getResultMap(), product.getBuyPrice().getMode(), true));
    }

    /** Price text of selling count items, including the player's sell multiplier. */
    String sellPrice(ObjectItem product, int count) {
        if (product.getRawSellPrice().empty) {
            return null;
        }
        ObjectUseTimesCache cache = ShopHelper.getPlayerUseTimesCache(product, player);
        GiveResult result = product.getSellPrice().give(player, cache == null ? 0 : cache.getSellUseTimes(), count);
        return result == null ? null : plain(ObjectPrices.getDisplayNameInLine(player, count,
            result.getResultMapForSellMultiplierDisplay(player), product.getSellPrice().getMode(), true));
    }

    List<String> lore(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return List.of();
        }
        List<String> lore = UltimateShop.methodUtil.getItemLore(stack.getItemMeta());
        return lore == null ? List.of() : lore.stream().map(this::rich).toList();
    }

    void sendAdded(ObjectItem item, String menuName) {
        LanguageManager.languageManager.sendStringText(player, "favourite-added",
            "item", item.getDisplayName(player), "menu", menuName);
    }

    void sendRemoved(ObjectItem item) {
        LanguageManager.languageManager.sendStringText(player, "favourite-removed",
            "item", item.getDisplayName(player), "menu", menu.getName());
    }
}
