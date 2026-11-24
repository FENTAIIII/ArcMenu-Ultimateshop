package com.fentai.ushop.arcmenu;

import cn.superiormc.ultimateshop.api.ShopHelper;
import cn.superiormc.ultimateshop.gui.ProductUIModel;
import cn.superiormc.ultimateshop.gui.inv.CommonGUI;
import cn.superiormc.ultimateshop.gui.inv.ShopGUI;
import cn.superiormc.ultimateshop.managers.CacheManager;
import cn.superiormc.ultimateshop.managers.ConfigManager;
import cn.superiormc.ultimateshop.methods.Product.BuyProductMethod;
import cn.superiormc.ultimateshop.methods.Product.SellProductMethod;
import cn.superiormc.ultimateshop.methods.ProductTradeStatus;
import cn.superiormc.ultimateshop.objects.ObjectShop;
import cn.superiormc.ultimateshop.objects.buttons.AbstractButton;
import cn.superiormc.ultimateshop.objects.buttons.ObjectItem;
import cn.superiormc.ultimateshop.objects.caches.ObjectCache;
import cn.superiormc.ultimateshop.objects.caches.ObjectUseTimesCache;
import cn.superiormc.ultimateshop.objects.menus.MenuSender;
import cn.superiormc.ultimateshop.objects.menus.ObjectFavouriteMenu;
import cn.superiormc.ultimateshop.objects.menus.ObjectMenu;
import cn.superiormc.ultimateshop.objects.menus.ObjectSearchMenu;
import cn.superiormc.ultimateshop.utils.ItemUtil;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.fentai.ushop.arcmenu.OreLayout.Detail;
import static com.fentai.ushop.arcmenu.OreLayout.Entry;
import static com.fentai.ushop.arcmenu.OreLayout.Featured;
import static com.fentai.ushop.arcmenu.OreLayout.Frame;
import static com.fentai.ushop.arcmenu.OreLayout.Pager;
import static com.fentai.ushop.arcmenu.OreLayout.Product;
import static com.fentai.ushop.arcmenu.OreLayout.Quick;
import static com.fentai.ushop.arcmenu.OreLayout.Search;
import static com.fentai.ushop.arcmenu.OreScene.Click;

/** Feeds an UltimateShop menu's live data and actions into the approved layouts. */
final class OreScreens {

    private final OreSession session;

    private final Player player;

    private final OreLayout.Lang lang;

    private final ObjectMenu home;

    private final MenuSender sender;

    private List<Entry> shops;

    private OreScreens(OreSession session) {
        this.session = session;
        this.player = session.getPlayer();
        this.sender = MenuSender.of(player, "arcmenu");
        OreLang texts = session.renderer.plugin().lang();
        this.lang = new OreLayout.Lang() {
            @Override
            public String get(String key, String fallback, String... replacements) {
                return texts.get(player, key, fallback, replacements);
            }

            @Override
            public List<String> all(String key, String fallback, String... replacements) {
                return texts.all(key, fallback, replacements);
            }
        };
        this.home = ObjectMenu.commonMenus.get(setting("navigation-menu",
            ConfigManager.configManager.getString("menu.auto-open.menu", "main")));
    }

    // ------------------------------------------------------------------ entry points

    static OreScene page(OreSession session) {
        OreScreens screens = new OreScreens(session);
        OreScene scene = new OreScene(screens.transition());
        screens.draw(scene);
        return scene;
    }

    /** The page as the dialog's backdrop, then the dialog; live values are the renderer's META values. */
    static OreScene details(OreSession session) {
        OreScreens screens = new OreScreens(session);
        OreScene scene = new OreScene("us-modal");
        List<ObjectItem> shown = screens.draw(scene);
        new OreLayout(scene, screens.lang).detail(screens.detail(), shown.indexOf(session.selected),
            (type, input) -> session.closeDetails());
        return scene;
    }

    private String transition() {
        if (session.search()) {
            return session.keyword.isBlank() ? "us-rise" : "us-soft";
        }
        return session.shop != null && !session.favourites() ? "us-soft" : "us-rise";
    }

    /** Draws the current page; returns the products shown as cards, in card order. */
    private List<ObjectItem> draw(OreScene scene) {
        OreLayout layout = new OreLayout(scene, lang);
        Frame frame = frame();
        if (session.search()) {
            return search(layout, frame);
        }
        if (session.favourites()) {
            return favourites(layout, frame);
        }
        List<Map.Entry<Integer, AbstractButton>> content = placed(session.buttons, "content", true);
        List<ObjectItem> products = new ArrayList<>();
        for (Map.Entry<Integer, AbstractButton> entry : content) {
            if (entry.getValue() instanceof ObjectItem item && session.visible(item)) {
                products.add(item);
            }
        }
        if (session.shop != null || !products.isEmpty() && products.size() == content.size()) {
            Pager pager = pager(products.size(), OreLayout.PER);
            List<ObjectItem> page = slice(products, pager, OreLayout.PER);
            layout.shop(frame, shopIcon(), session.shop != null ? session.plain(session.shop.getShopDisplayName())
                : session.title, products.size(), pager, page.stream().map(this::product).toList());
            return page;
        }
        List<Entry> tiles = new ArrayList<>();
        for (Map.Entry<Integer, AbstractButton> entry : content) {
            int slot = entry.getKey();
            tiles.add(entry(entry.getValue(), icon(entry.getValue(), slot, true),
                (type, input) -> session.click(slot, type), false));
        }
        Pager pager = pager(tiles.size(), OreLayout.TILES);
        int shopCount = (int) tiles.stream().filter(tile -> tile.count() >= 0).count();
        layout.hub(frame, session.title, shopCount, featured(), slice(tiles, pager, OreLayout.TILES), pager);
        return List.of();
    }

    // ------------------------------------------------------------------ frame

    private Frame frame() {
        Frame f = new Frame();
        f.head = session.title;
        f.balance = balance();
        f.close = (type, input) -> session.closeGUI();
        if (session.search() && !session.keyword.isBlank()) {
            f.back = (type, input) -> searchFor("");
        } else if (home != null && home != session.menu) {
            f.back = (type, input) -> CommonGUI.openGUI(player, home.getName(), false, false);
        }
        List<Map.Entry<Integer, AbstractButton>> own = placed(session.buttons, "sidebar", true);
        boolean current = !own.isEmpty() || home == null || home == session.menu;
        List<Map.Entry<Integer, AbstractButton>> quick = current ? own : placed(buttons(home), "sidebar", false);
        List<Quick> quicks = new ArrayList<>();
        for (int i = 0; i < quick.size(); i++) {
            AbstractButton button = quick.get(i).getValue();
            int slot = quick.get(i).getKey();
            ItemStack icon = icon(button, slot, current);
            Click open = current ? (type, input) -> session.click(slot, type) : (type, input) -> {
                button.clickEvent(type, player);
                session.refreshAfterAction();
            };
            String target = actionValue(button, "open_menu", "menu");
            ObjectMenu menu = target == null ? null : ObjectMenu.commonMenus.get(target);
            if (menu instanceof ObjectSearchMenu && f.search == null) {
                f.search = open;
            }
            if (menu instanceof ObjectFavouriteMenu && f.favourites == null) {
                f.favourites = open;
                f.favouriteIcon = icon.getType();
            }
            quicks.add(new Quick(String.valueOf(i), icon.getType(), tooltip(icon), menu == session.menu, open));
        }
        f.quick = quicks;
        List<Entry> list = shops();
        f.navTotal = list.size();
        f.navPages = Math.max(1, (list.size() + OreLayout.SIDE_PER - 1) / OreLayout.SIDE_PER);
        if (session.navPage < 0) {
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).active()) {
                    session.navPage = i / OreLayout.SIDE_PER;
                }
            }
        }
        session.navPage = Math.max(0, Math.min(session.navPage, f.navPages - 1));
        f.navPage = session.navPage;
        f.nav = list.subList(f.navPage * OreLayout.SIDE_PER,
            Math.min(list.size(), (f.navPage + 1) * OreLayout.SIDE_PER));
        f.navUp = (type, input) -> {
            session.navPage--;
            refresh();
        };
        f.navDown = (type, input) -> {
            session.navPage++;
            refresh();
        };
        return f;
    }

    private String balance() {
        String balance = setting("balance", "$%vault_eco_balance%");
        return balance.isBlank() || session.plain(balance).contains("%") ? null : balance;
    }

    /** The navigation menu's shop entries. */
    private List<Entry> shops() {
        if (shops != null) {
            return shops;
        }
        shops = new ArrayList<>();
        if (home == null) {
            return shops;
        }
        boolean current = home == session.menu;
        Map<Integer, AbstractButton> entries = current ? session.buttons : buttons(home);
        for (Map.Entry<Integer, AbstractButton> entry : placed(entries, "content", current)) {
            AbstractButton button = entry.getValue();
            if (!button.hasShopMenuAction()) {
                continue;
            }
            int slot = entry.getKey();
            Click open = current ? (type, input) -> session.click(slot, type) : (type, input) -> {
                button.clickEvent(type, player);
                session.refreshAfterAction();
            };
            shops.add(entry(button, icon(button, slot, current), open, true));
        }
        return shops;
    }

    private Entry entry(AbstractButton button, ItemStack icon, Click open, boolean markActive) {
        String shopId = actionValue(button, "shop_menu", "shop");
        ObjectShop shop = shopId == null ? null : ConfigManager.configManager.getShop(shopId);
        String shortName = session.plain(ItemUtil.getItemName(icon));
        String name = shop != null ? session.plain(shop.getShopDisplayName()) : shortName;
        int count = shop != null ? shop.getProductListNotHidden(player).size() : -1;
        boolean active = markActive && shop != null && session.shop != null
            && shop.getShopName().equals(session.shop.getShopName());
        return new Entry(name, shortName, icon.getType(), count, active, open);
    }

    private Material shopIcon() {
        for (Entry entry : shops()) {
            if (entry.active()) {
                return entry.icon();
            }
        }
        return Material.CHEST;
    }

    /** The sidebar name of a shop (its navigation button), or its display name. */
    private String shortName(ObjectShop shop) {
        String name = session.plain(shop.getShopDisplayName());
        for (Entry entry : shops()) {
            if (entry.name().equals(name)) {
                return entry.shortName();
            }
        }
        return name;
    }

    private Featured featured() {
        String id = setting("featured.shop", "daily");
        ObjectShop shop = id.isBlank() ? null : ConfigManager.configManager.getShop(id);
        if (shop == null) {
            return null;
        }
        Featured featured = new Featured();
        featured.name = session.plain(shop.getShopDisplayName());
        Material icon = Material.matchMaterial(setting("featured.icon", "CLOCK"));
        featured.icon = icon == null ? Material.CLOCK : icon;
        featured.timer = OreRenderer.value("timer");
        featured.open = (type, input) -> ShopGUI.openGUI(player, shop, false, false);
        return featured;
    }

    // ------------------------------------------------------------------ products

    private Product product(ObjectItem item) {
        return product(item, false);
    }

    private Product product(ObjectItem item, boolean favouriteEdit) {
        ItemStack stack = item.getDisplayItem(player);
        Material material = stack == null || stack.getType().isAir() ? Material.BARRIER : stack.getType();
        Click details = (type, input) -> session.openDetails(item);
        Click buy = (type, input) -> {
            if (type.isRightClick()) {
                session.openDetails(item);
            } else {
                trade(item, "buy", type == ClickType.SHIFT_LEFT ? 64 : item.getDefaultBuyAmount());
            }
        };
        Click sell = (type, input) -> {
            if (type.isRightClick()) {
                session.openDetails(item);
            } else if (type == ClickType.SHIFT_LEFT) {
                if (item.isEnableSellAll()) {
                    trade(item, "sell-all", 1);
                } else {
                    trade(item, "sell", 64);
                }
            } else {
                trade(item, "sell", item.getDefaultSellAmount());
            }
        };
        Click remove = favouriteEdit ? (type, input) -> editFavourite(null, item) : null;
        Click move = favouriteEdit ? (type, input) -> editFavourite(type, item) : null;
        return new Product(material, stack == null ? 1 : Math.max(1, stack.getAmount()),
            session.plain(item.getDisplayName(player, false)), session.plain(item.getShopObject().getShopDisplayName()),
            shortName(item.getShopObject()), item.getBuyPrice().empty ? null : session.buyPrice(item, 1),
            item.getRawSellPrice().empty ? null : session.sellPrice(item, 1), item == session.selected, details, buy,
            sell, remove, move);
    }

    private void trade(ObjectItem item, String action, int amount) {
        session.run(item, new ProductUIModel.Action(action, action, ClickType.LEFT), amount, amount);
    }

    private Detail detail() {
        ObjectItem item = session.selected;
        Detail d = new Detail();
        d.product = product(item);
        d.buyUnit = d.product.buy();
        d.sellUnit = d.product.sell();
        int limit = item.getPlayerBuyLimit(player);
        d.limitTag = limit > 0 ? String.valueOf(limit) : null;
        d.sellable = d.product.sell() != null;
        d.max = OreSession.maxAmount(item);
        d.selectable = OreSession.canSelectAmount(item);
        d.qty = OreRenderer.value("qty");
        d.buyTotal = d.product.buy() != null ? OreRenderer.value("buy-total") : null;
        d.sellTotal = d.product.sell() != null ? OreRenderer.value("sell-total") : null;
        d.buyLabel = OreRenderer.value("label0");
        d.sellLabel = OreRenderer.value("label1");
        d.balance = balance();
        d.info = info(item, limit);
        d.close = (type, input) -> session.closeDetails();
        if (d.selectable) {
            d.syncAmount = List.of("arcitx-set: dq " + OreRenderer.value("qty"));
            d.minus = (type, input) -> session.setAmount(session.amount - (type == ClickType.SHIFT_LEFT ? 10 : 1));
            d.plus = (type, input) -> session.setAmount(session.amount + (type == ClickType.SHIFT_LEFT ? 10 : 1));
            d.ok = (type, input) -> {
                String digits = input == null ? "" : input.replaceAll("[^0-9]", "");
                if (!digits.isEmpty()) {
                    session.setAmount(digits.length() > 9 ? d.max : Long.parseLong(digits));
                }
            };
            for (int k = 0; k < d.chips.length; k++) {
                int value = Math.min(d.chipValues[k], d.max);
                d.chipValues[k] = value;
                d.chips[k] = (type, input) -> session.setAmount(value);
            }
        }
        if (d.product.buy() != null) {
            d.buy = (type, input) -> trade(item, "buy", session.amount);
            d.buyMax = (type, input) -> trade(item, "buy", Math.max(1, Math.min(d.max, fits(item))));
        }
        if (d.product.sell() != null) {
            d.sell = (type, input) -> trade(item, "sell", session.amount);
            if (item.isEnableSellAll()) {
                d.sellAll = (type, input) -> trade(item, "sell-all", 1);
            }
        }
        ObjectFavouriteMenu favourites = favouriteMenu();
        if (favourites != null && item.isAllowFavourite()) {
            d.favourite = (type, input) -> toggleFavourite(favourites, item);
        }
        return d;
    }

    /** Plain statements of what the player can do now, from UltimateShop's own trade checks. */
    private List<String> info(ObjectItem item, int limit) {
        List<String> lines = new ArrayList<>();
        if (item.getBuyPrice().empty) {
            lines.add(lang.get("info-no-buy", "This item is not for sale"));
        } else {
            int buy = capacity(n -> BuyProductMethod.startBuy(item, player, false, true, n).getStatus(),
                Math.max(1, Math.min(MAX_CHECK, fits(item))));
            lines.add(buy > 0 ? lang.get("info-buy", "You can buy {amount}", "amount", String.valueOf(buy))
                : lang.get("info-buy-none", "You can't buy any right now"));
        }
        if (item.getRawSellPrice().empty) {
            lines.add(lang.get("info-no-sell", "This item can't be sold"));
        } else {
            int sell = capacity(n -> SellProductMethod.startSell(item, player, false, true, n).getStatus(), MAX_CHECK);
            lines.add(sell > 0 ? lang.get("info-sell", "You can sell {amount}", "amount", String.valueOf(sell))
                : lang.get("info-sell-none", "You have none to sell"));
        }
        if (limit > 0) {
            ObjectUseTimesCache cache = ShopHelper.getPlayerUseTimesCache(item, player);
            int left = Math.max(0, limit - (cache == null ? 0 : cache.getBuyUseTimes()));
            lines.add(lang.get("info-limit", "Purchase limit left: {amount}", "amount", String.valueOf(left)));
        } else {
            lines.add(lang.get("info-max", "Up to {amount} per trade", "amount",
                String.valueOf(OreSession.maxAmount(item))));
        }
        return lines;
    }

    private static final int MAX_CHECK = 36 * 64;

    /** The largest amount in 1..max the check accepts, or 0; trade checks are monotonic in the amount. */
    private static int capacity(java.util.function.IntFunction<ProductTradeStatus.Status> check, int max) {
        if (check.apply(1) != ProductTradeStatus.Status.DONE) {
            return 0;
        }
        int low = 1;
        int high = max;
        while (low < high) {
            int middle = (low + high + 1) >>> 1;
            if (check.apply(middle) == ProductTradeStatus.Status.DONE) {
                low = middle;
            } else {
                high = middle - 1;
            }
        }
        return low;
    }

    /** How many of the product's display item still fit into the player's inventory. */
    private int fits(ObjectItem item) {
        ItemStack stack = item.getDisplayItem(player);
        if (stack == null || stack.getType().isAir()) {
            return 1;
        }
        int per = Math.max(1, stack.getAmount());
        int room = 0;
        for (ItemStack slot : player.getInventory().getStorageContents()) {
            if (slot == null || slot.getType().isAir()) {
                room += stack.getMaxStackSize();
            } else if (slot.isSimilar(stack)) {
                room += Math.max(0, slot.getMaxStackSize() - slot.getAmount());
            }
        }
        return room / per;
    }

    /** Live values of the dialog: amount, totals and the two action labels. */
    static Map<String, String> amountValues(OreSession session) {
        ObjectItem item = session.selected;
        Map<String, String> values = new LinkedHashMap<>();
        values.put("qty", String.valueOf(session.amount));
        String buy = item.getBuyPrice().empty ? null : session.buyPrice(item, session.amount);
        String sell = item.getRawSellPrice().empty ? null : session.sellPrice(item, session.amount);
        values.put("buy-total", OreScene.escape(buy == null ? "—" : buy));
        values.put("sell-total", OreScene.escape(sell == null ? "—" : sell));
        values.put("label0", OreScene.escape(session.lang("md-buy", "Buy ×{amount}", "amount",
            String.valueOf(session.amount))));
        values.put("label1", OreScene.escape(session.lang("md-sell", "Sell ×{amount}", "amount",
            String.valueOf(session.amount))));
        return values;
    }

    /** Nodes showing those values; the amount box itself follows its Arcitx input (arcitx-set). */
    static final List<String> AMOUNT_NODES = List.of("md-sum0-v", "md-sum1-v", "md-buy-text", "md-sell-text");

    // ------------------------------------------------------------------ search

    private List<ObjectItem> search(OreLayout layout, Frame frame) {
        Search s = new Search();
        s.keyword = session.keyword;
        s.recent = session.renderer.recent(player);
        s.shopCount = ConfigManager.configManager.getShopList().size();
        s.submit = (type, input) -> searchFor(input == null ? session.keyword : input);
        s.clear = (type, input) -> searchFor("");
        s.searchFor = word -> (type, input) -> searchFor(word);
        String name = session.title;
        if (s.keyword.isBlank()) {
            layout.search(frame, name, s);
            return List.of();
        }
        List<ObjectItem> hits = ShopHelper.getTargetItems(s.keyword.trim(), player).stream()
            .filter(session::visible).toList();
        if (hits.isEmpty()) {
            layout.noResults(frame, name, s);
            return List.of();
        }
        List<ObjectShop> found = new ArrayList<>();
        for (ObjectItem hit : hits) {
            if (!found.contains(hit.getShopObject())) {
                found.add(hit.getShopObject());
            }
        }
        List<String[]> filters = new ArrayList<>();
        filters.add(new String[]{lang.get("flt-all", "All"), String.valueOf(hits.size())});
        for (ObjectShop shop : found) {
            long count = hits.stream().filter(hit -> hit.getShopObject() == shop).count();
            filters.add(new String[]{shortName(shop), String.valueOf(count)});
        }
        session.filter = Math.min(session.filter, filters.size() - 1);
        s.filters = filters;
        s.activeFilter = session.filter;
        s.filter = k -> (type, input) -> {
            session.filter = k;
            session.page = 0;
            refresh();
        };
        List<ObjectItem> filtered = session.filter == 0 ? hits
            : hits.stream().filter(hit -> hit.getShopObject() == found.get(session.filter - 1)).toList();
        Pager pager = pager(filtered.size(), OreLayout.RESULTS_PER);
        List<ObjectItem> page = slice(filtered, pager, OreLayout.RESULTS_PER);
        layout.results(frame, name, s, hits.size(), found.size(), page.stream().map(this::product).toList(), pager);
        return page;
    }

    private void searchFor(String keyword) {
        session.keyword = keyword == null ? "" : keyword.trim();
        session.page = 0;
        session.filter = 0;
        session.renderer.remember(player, session.keyword);
        refresh();
    }

    // ------------------------------------------------------------------ favourites

    private List<ObjectItem> favourites(OreLayout layout, Frame frame) {
        ObjectFavouriteMenu menu = (ObjectFavouriteMenu) session.menu;
        ObjectCache cache = CacheManager.cacheManager.getObjectCache(player);
        List<ObjectItem> products = cache == null ? List.of()
            : List.copyOf(cache.getResolvedFavouriteProducts(menu.getName()).values());
        boolean editable = menu.getEditModeButton() != null;
        boolean edit = editable && session.editingFavourites;
        Click toggle = editable ? (type, input) -> {
            session.editingFavourites = !session.editingFavourites;
            refresh();
        } : null;
        Click clear = (type, input) -> {
            if (cache != null) {
                for (ObjectItem item : products) {
                    cache.removeFavouriteProduct(menu.getName(), item);
                }
            }
            refresh();
        };
        Pager pager = pager(products.size(), OreLayout.PER);
        List<ObjectItem> page = slice(products, pager, OreLayout.PER);
        layout.favourites(frame, session.title, products.size(), edit, toggle, clear,
            page.stream().map(item -> product(item, edit)).toList(), pager);
        return page;
    }

    /** Removes the item (type null), or moves it one place: left click forward, right click back. */
    private void editFavourite(ClickType type, ObjectItem item) {
        ObjectCache cache = CacheManager.cacheManager.getObjectCache(player);
        if (cache == null) {
            return;
        }
        String name = session.menu.getName();
        List<ObjectItem> order = List.copyOf(cache.getResolvedFavouriteProducts(name).values());
        int index = order.indexOf(item);
        if (type == null) {
            if (cache.removeFavouriteProduct(name, item)) {
                session.sendRemoved(item);
            }
        } else if (index >= 0) {
            cache.moveFavouriteProduct(name, index, type.isRightClick() ? index + 1 : index - 1);
        }
        refresh();
    }

    private ObjectFavouriteMenu favouriteMenu() {
        if (session.menu instanceof ObjectFavouriteMenu menu) {
            return menu;
        }
        return ObjectMenu.commonMenus.values().stream().filter(ObjectFavouriteMenu.class::isInstance)
            .map(ObjectFavouriteMenu.class::cast).findFirst().orElse(null);
    }

    private void toggleFavourite(ObjectFavouriteMenu menu, ObjectItem item) {
        ObjectCache cache = CacheManager.cacheManager.getObjectCache(player);
        if (cache == null) {
            return;
        }
        if (cache.hasFavouriteProduct(menu.getName(), item)) {
            if (cache.removeFavouriteProduct(menu.getName(), item)) {
                session.sendRemoved(item);
            }
        } else if (cache.getResolvedFavouriteProductAmount(menu.getName()) < menu.getResultSlots().size()
            && cache.addFavouriteProduct(menu.getName(), item)) {
            session.sendAdded(item, menu.getName());
        }
    }

    // ------------------------------------------------------------------ data helpers

    private void refresh() {
        session.renderer.showPage(session, false);
    }

    private Pager pager(int total, int per) {
        Pager pager = new Pager();
        pager.pages = Math.max(1, (total + per - 1) / per);
        session.page = Math.max(0, Math.min(session.page, pager.pages - 1));
        pager.page = session.page;
        pager.prev = (type, input) -> {
            session.page--;
            refresh();
        };
        pager.next = (type, input) -> {
            session.page++;
            refresh();
        };
        return pager;
    }

    private static <T> List<T> slice(List<T> list, Pager pager, int per) {
        return list.subList(Math.min(list.size(), pager.page * per), Math.min(list.size(), (pager.page + 1) * per));
    }

    private Map<Integer, AbstractButton> buttons(ObjectMenu menu) {
        return new LinkedHashMap<>(menu.getUiMenu(sender));
    }

    /**
     * Visible buttons in one placement area (ui.placement.area), ordered by ui.placement.order. Products without a
     * placement belong to the content area; other buttons without one are not shown.
     */
    private List<Map.Entry<Integer, AbstractButton>> placed(Map<Integer, AbstractButton> buttons, String area,
                                                            boolean current) {
        List<Map.Entry<Integer, AbstractButton>> result = new ArrayList<>();
        for (Map.Entry<Integer, AbstractButton> entry : buttons.entrySet()) {
            AbstractButton button = entry.getValue();
            if (!button.isVisibleInMenu("arcmenu") || !button.canDisplay(sender)) {
                continue;
            }
            if (area.equals(placementArea(button, button instanceof ObjectItem))
                && !icon(button, entry.getKey(), current).getType().isAir()) {
                result.add(entry);
            }
        }
        result.sort(Comparator.comparingInt(entry -> placementOrder(entry.getValue(), entry.getKey())));
        return result;
    }

    private ItemStack icon(AbstractButton button, int slot, boolean current) {
        ItemStack stack = current && session.menuItems.containsKey(slot) ? session.menuItems.get(slot)
            : button.getDisplayItem(player, 1).getItemStack();
        return stack == null ? new ItemStack(Material.AIR) : stack;
    }

    private static String placementArea(AbstractButton button, boolean product) {
        ConfigurationSection settings = button.getButtonConfig();
        ConfigurationSection placement = settings == null ? null : settings.getConfigurationSection("ui.placement");
        if (placement == null) {
            return product ? "content" : null;
        }
        return placement.getBoolean("enabled", true) ? placement.getString("area", product ? "content" : null) : null;
    }

    private static int placementOrder(AbstractButton button, int slot) {
        ConfigurationSection settings = button.getButtonConfig();
        return settings == null ? slot : settings.getInt("ui.placement.order", slot);
    }

    private static String actionValue(AbstractButton button, String type, String key) {
        ConfigurationSection settings = button == null ? null : button.getButtonConfig();
        ConfigurationSection actions = settings == null ? null : settings.getConfigurationSection("actions");
        if (actions == null) {
            return null;
        }
        for (String id : actions.getKeys(false)) {
            ConfigurationSection action = actions.getConfigurationSection(id);
            if (action != null && type.equalsIgnoreCase(action.getString("type"))) {
                return action.getString(key);
            }
        }
        return null;
    }

    /** Item name and lore with their colours, for a native tooltip. */
    private List<String> tooltip(ItemStack stack) {
        List<String> lines = new ArrayList<>();
        lines.add("&f" + OreScene.escape(session.rich(ItemUtil.getItemName(stack))));
        for (String line : session.lore(stack)) {
            lines.add(OreScene.escape(line));
        }
        return lines;
    }

    private String setting(String key, String fallback) {
        return session.renderer.plugin().getConfig().getString(key, fallback);
    }
}
