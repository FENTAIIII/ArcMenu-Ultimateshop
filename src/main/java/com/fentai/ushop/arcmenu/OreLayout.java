package com.fentai.ushop.arcmenu;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.fentai.ushop.arcmenu.OreScene.BODY;
import static com.fentai.ushop.arcmenu.OreScene.CARD;
import static com.fentai.ushop.arcmenu.OreScene.CARD_HOV;
import static com.fentai.ushop.arcmenu.OreScene.Click;
import static com.fentai.ushop.arcmenu.OreScene.DISABLED;
import static com.fentai.ushop.arcmenu.OreScene.G;
import static com.fentai.ushop.arcmenu.OreScene.GOLD;
import static com.fentai.ushop.arcmenu.OreScene.G_HI;
import static com.fentai.ushop.arcmenu.OreScene.HI;
import static com.fentai.ushop.arcmenu.OreScene.HINT;
import static com.fentai.ushop.arcmenu.OreScene.INK;
import static com.fentai.ushop.arcmenu.OreScene.LINE;
import static com.fentai.ushop.arcmenu.OreScene.LO;
import static com.fentai.ushop.arcmenu.OreScene.MONEY;
import static com.fentai.ushop.arcmenu.OreScene.MUTE;
import static com.fentai.ushop.arcmenu.OreScene.Mode;
import static com.fentai.ushop.arcmenu.OreScene.OUT;
import static com.fentai.ushop.arcmenu.OreScene.R;
import static com.fentai.ushop.arcmenu.OreScene.SELL;
import static com.fentai.ushop.arcmenu.OreScene.SIDE;
import static com.fentai.ushop.arcmenu.OreScene.Style;
import static com.fentai.ushop.arcmenu.OreScene.TILE;
import static com.fentai.ushop.arcmenu.OreScene.WHITE;
import static com.fentai.ushop.arcmenu.OreScene.WIN;
import static com.fentai.ushop.arcmenu.OreScene.WIN_HI;
import static com.fentai.ushop.arcmenu.OreScene.WIN_LO;
import static com.fentai.ushop.arcmenu.OreScene.escape;
import static com.fentai.ushop.arcmenu.OreScene.ink;
import static com.fentai.ushop.arcmenu.OreScene.opts;

/**
 * The approved Ore UI screens, ported line by line from the design generator (gen_ushop.py) and fed with view data,
 * so a generated screen matches the reviewed design node for node. Nothing here touches UltimateShop.
 */
final class OreLayout {

    static final int PER = 9;
    static final int SIDE_PER = 8;
    static final int RESULTS_PER = 6;
    static final int TILES = 15;
    private static final double TIP_Z = 4.75;

    interface Lang {
        String get(String key, String fallback, String... replacements);

        /** The text in every loaded language, for slots sized to the widest one like the design. */
        List<String> all(String key, String fallback, String... replacements);
    }

    /** A sidebar shop or a hub tile. count < 0 hides the counter. */
    record Entry(String name, String shortName, Material icon, int count, boolean active, Click open) {
    }

    record Quick(String id, Material icon, List<String> tooltip, boolean active, Click open) {
    }

    /** One product card; prices are display text (null when not traded that way). */
    record Product(Material material, int amount, String name, String shopName, String shopShort, String buy,
                   String sell, boolean selected, Click details, Click buyClick, Click sellClick, Click remove,
                   Click move) {
    }

    static final class Frame {
        String head = "";
        String balance;
        Click back;
        Click close;
        String activeQuick;
        List<Quick> quick = List.of();
        List<Entry> nav = List.of();
        int navTotal;
        int navPage;
        int navPages = 1;
        Click navUp;
        Click navDown;
        Click search;
        Material favouriteIcon = Material.NAME_TAG;
        Click favourites;
    }

    static final class Pager {
        int page;
        int pages = 1;
        Click prev;
        Click next;
    }

    static final class Featured {
        String name;
        Material icon;
        String timer;
        Click open;
    }

    static final class Detail {
        Product product;
        String buyUnit;
        String sellUnit;
        String limitTag;
        boolean sellable;
        int max = 64;
        boolean selectable = true;
        /** Runtime contents ({session:...}) of the amount, totals and action labels. */
        String qty;
        String buyTotal;
        String sellTotal;
        String buyLabel;
        String sellLabel;
        String balance;
        /** The three info lines under the name; null shows the language file's md-lore lines. */
        List<String> info;
        /** YAML actions that copy the new amount into the amount box after a change. */
        List<String> syncAmount = List.of();
        Click close;
        Click minus;
        Click plus;
        Click ok;
        Click[] chips = new Click[5];
        int[] chipValues = {1, 8, 16, 32, 64};
        Click buy;
        Click sell;
        Click favourite;
        Click buyMax;
        Click sellAll;
    }

    static final class Search {
        String keyword = "";
        List<String> recent = List.of();
        Click submit;
        Click clear;
        /** Hot and recent keywords and filter chips search through this. */
        java.util.function.Function<String, Click> searchFor;
        List<String[]> filters = List.of();
        int activeFilter;
        java.util.function.IntFunction<Click> filter;
        int shopCount;
    }

    private final OreScene m;

    private final Lang lang;

    OreLayout(OreScene scene, Lang lang) {
        this.m = scene;
        this.lang = lang;
    }

    // ------------------------------------------------------------------ i18n

    /** Localized text, ellipsized to maxWidth at its rendered size (localized nodes get the CJK size floor). */
    private String t(String key, String fallback, double size, double maxWidth, String... replacements) {
        return OreScene.fit(lang.get(key, fallback, replacements), Math.max(size, 3.4), maxWidth);
    }

    private String t(String key, String fallback, String... replacements) {
        return lang.get(key, fallback, replacements);
    }

    private static String fit(String text, double size, double maxWidth) {
        return OreScene.fit(text, Math.max(size, 3.4), maxWidth);
    }

    private double widest(String key, String fallback, double size, String... replacements) {
        double result = 0;
        for (String text : lang.all(key, fallback, replacements)) {
            result = Math.max(result, ink(text, Math.max(size, 3.4)));
        }
        return result;
    }

    private static String c(String color) {
        return "&" + color;
    }

    /** Node content as text() writes it, for hover content tracks. */
    private static String content(String color, String text) {
        return "&" + color + escape(text);
    }

    private static List<String> tip(String... lines) {
        return List.of(lines);
    }

    // ------------------------------------------------------------------ shell

    void shell(Frame f) {
        m.rect("win-outline", 8, 4, 304, 166, OUT, -9);
        m.rect("win", 8.8, 4.8, 302.4, 164.4, WIN, -8.5);
        m.rect("win-hi", 8.8, 4.8, 302.4, .8, WIN_HI, -8);
        m.rect("win-lo", 8.8, 167.6, 302.4, 1.6, WIN_LO, -8);
        m.rect("body-outline", 12, 22, 296, 144.6, OUT, -7.5);
        m.rect("body", 12.8, 22.8, 294.4, 143, BODY, -7);
        mark();
        m.text("head-title", WHITE, fit(f.head, 4.4, 96), 160, 13.4, 4.4, 3, Mode.LOC);
        if (f.balance != null) {
            m.rect("bal-outline", 214, 7, 54, 12, OUT, -6);
            m.rect("bal", 214.8, 7.8, 52.4, 10.4, HI, -5.5);
            m.rect("bal-bar", 214.8, 7.8, 1.6, 10.4, G, -5);
            m.text("bal-label", MUTE, t("bal", "Balance"), 241.5, 10.4, 2.4, 3, Mode.LOC);
            m.text("bal-value", MONEY, f.balance, 241.5, 15.6, 3.8, 3, 'c', Mode.DYN, 20);
            m.region("bal-hit", 214, 7, 54, 12, tip("&f" + escape(t("bal-tip", "Current balance")),
                "&7" + f.balance), null);
            m.loop("us-bal-bar", "bal-bar", "color", 40, G, G_HI, G);
        }
        if (f.back != null) {
            m.button("back", 271, 7, 15, 12, "‹", Style.GREY, opts().z(-6).size(5.2).mode(Mode.RAW)
                .tooltip(tip("&f" + escape(t("back", "Back")))).action(f.back));
        }
        double closeX = 289 + 4.875;
        m.button("close", closeX, 7, 15, 12, "×", Style.GREY, opts().z(-6).size(5.2).mode(Mode.RAW).hover(false)
            .tooltip(tip("&f" + escape(t("close", "Close")))).action(f.close));
        m.hover("close", "close-bg", "color", CARD, R, 2, "close-text", "content", content(INK, "×"),
            content(WHITE, "×"), 1);
        sidebar(f);
    }

    /** Logo text with a thin black outline (ArcMenu text has no outline: eight offset copies sit behind it). */
    private void mark() {
        String text = "&lULTIMATESHOP";
        double size = 5.2;
        double scale = 1.6;
        double x = -111.408;
        double y = 71.326;
        double px = .1106 * size * scale * .5;
        double[][] offsets = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}, {-1, -1}, {-1, 1}, {1, -1}, {1, 1}};
        for (int k = 0; k < offsets.length; k++) {
            m.rawText("mark-o" + k, "&#000000" + text, x + offsets[k][0] * px, y + offsets[k][1] * px, 2.75, size,
                scale);
        }
        m.rawText("mark", "&#98FB98" + text, x, y, 3, size, scale);
    }

    private void sidebar(Frame f) {
        m.rect("side", 12.8, 22.8, 69.2, 143, SIDE, -6.5);
        m.rect("side-line", 82, 22.8, .8, 143, OUT, -6);
        double qw = (79 - 16 - 4) / 3.0;
        for (int i = 0; i < Math.min(3, f.quick.size()); i++) {
            Quick q = f.quick.get(i);
            double l = 16 + i * (qw + 2);
            String id = "qa-" + q.id();
            m.button(id, l, 26, qw, 14, null, q.active() ? Style.LIGHT : Style.GREY,
                opts().z(-5).icon(q.icon(), 8.5).tooltip(q.tooltip()).action(q.open()));
            if (q.active()) {
                m.rect(id + "-on", l + 3, 38.2, qw - 6, .8, G, -4);
            }
        }
        m.text("side-label", MUTE, t("side-shops", "Shops", 2.8, 40), 44, 46, 2.8, 3, Mode.LOC);
        m.text("side-count", MUTE, String.valueOf(f.navTotal), 79, 46, 2.8, 3, 'r', Mode.RAW, 0);
        for (int i = 0; i < Math.min(SIDE_PER, f.nav.size()); i++) {
            Entry s = f.nav.get(i);
            double tp = 50 + i * 11.4;
            boolean on = s.active();
            String p = "nav" + i;
            m.bevel(p, 16, tp, 63, 10.2, on ? HI : "#E9EAEC", WHITE, on ? LO : DISABLED, -5, .4, .5, .9);
            if (on) {
                m.rect(p + "-bar", 16.4, tp + .4, 1.6, 9.4, G, -4);
            }
            m.item(p + "-icon", s.icon(), 23, tp + 5.1, 6.4);
            String name = fit(s.shortName(), 3.2, 41);
            m.text(p + "-name", on ? G : INK, name, 49.5, tp + 5, 3.2, 3, Mode.LOC);
            if (s.count() >= 0) {
                m.text(p + "-n", MUTE, String.valueOf(s.count()), 76.5, tp + 5.2, 2.5, 3, 'r', Mode.RAW, 0);
            }
            if (!on) {
                m.region(p, 16, tp, 63, 10.2, entryTip(s, false), s.open());
                m.hover(p, p + "-frame", "color", OUT, G, 2, p + "-bg", "color", "#E9EAEC", WHITE, 2,
                    p + "-icon", "scale", m.scaleOf(p + "-icon", 6.4), m.scaleOf(p + "-icon", 7.8), 3,
                    p + "-name", "content", content(INK, name), content(G, name), 1);
            }
        }
        for (int i = 0; i < SIDE_PER; i++) {
            m.entrance("nav" + i + "-g", 1 + i * .8, -9, 0, 6, "nav" + i + "-");
        }
        m.text("side-page", MUTE, (f.navPage + 1) + " / " + f.navPages, 47.5, 156.6, 3, 3, Mode.RAW);
        pageButton("side-up", 16, 151, 10, 10, "‹", -5, 4.4, f.navPage > 0 ? f.navUp : null,
            "prev", "Previous page");
        pageButton("side-down", 69, 151, 10, 10, "›", -5, 4.4, f.navPage < f.navPages - 1 ? f.navDown : null,
            "next", "Next page");
    }

    private List<String> entryTip(Entry s, boolean enter) {
        List<String> lines = new ArrayList<>();
        lines.add("&f" + escape(s.name()));
        if (s.count() >= 0) {
            lines.add("&7" + escape(t("n-items", "{amount} items", "amount", String.valueOf(s.count()))));
        }
        if (enter) {
            lines.add("&e" + escape(t("enter", "Click to open")));
        }
        return lines;
    }

    private void pageButton(String id, double l, double t, double w, double h, String label, double z, double size,
                            Click action, String key, String fallback) {
        boolean ok = action != null;
        m.button(id, l, t, w, h, label, Style.GREY, opts().z(z).size(size).mode(Mode.RAW)
            .labelColor(ok ? INK : DISABLED).hover(ok)
            .tooltip(ok ? tip("&f" + escape(t(key, fallback))) : null).action(action));
    }

    void pager(Pager p) {
        m.text("page", MUTE, String.format("%02d / %02d", p.page + 1, p.pages), 88, 155.6, 3.4, 3, 'l', Mode.RAW, 0);
        double x = 88 + ink("00 / 00", 3.4) + 4;
        for (int i = 0; i < p.pages; i++) {
            boolean current = i == p.page;
            m.rect("dot-" + i, x + i * 6, 155, current ? 4.4 : 2.4, 1.2, current ? G : LINE, -1);
            if (current) {
                m.loop("us-dot-" + i, "dot-" + i, "color", 30, G, G_HI, G);
            }
            x += current ? 2 : 0;
        }
        pageButton("pg-prev", 280, 150, 11, 10, "‹", -1, 4.6, p.page > 0 ? p.prev : null, "prev", "Previous page");
        pageButton("pg-next", 293, 150, 11, 10, "›", -1, 4.6, p.page < p.pages - 1 ? p.next : null, "next",
            "Next page");
    }

    void hint(String key, String fallback, double cx, double maxWidth) {
        m.text("hint", HINT, t(key, fallback, 2.6, maxWidth), cx, 155.6, 2.6, 3, Mode.LOC);
    }

    void hint(String key, String fallback) {
        hint(key, fallback, 200, 140);
    }

    void divider() {
        m.rect("div-bottom", 86, 147, 218, .6, LINE, -1);
    }

    // ------------------------------------------------------------------ title row

    void titleRow(Material icon, String name, String stats) {
        m.inset("tr-name", 86, 26.5, 88, 9.5, -2, "#F4F4F5", LO);
        m.item("title-icon", icon, 92, 31.25, 7);
        m.text("title", INK, fit(name, 4, 72), 137, 31.2, 4, 3, Mode.LOC);
        m.inset("tr-stats", 177, 26.5, 46, 9.5, -2, "#DDDEE1", LINE);
        m.text("subtitle", MUTE, fit(stats, 2.8, 42), 200, 31.2, 2.8, 3, Mode.LOC);
        m.rect("div-top", 86, 39.4, 218, .6, LINE, -1);
    }

    void searchPill(Frame f) {
        if (f.search != null) {
            m.inset("sp", 226, 26.5, 66, 9.5, -2);
            m.item("sp-icon", Material.SPYGLASS, 231, 31.25, 5.5);
            String label = t("sp", "Search all items…", 3, 52);
            m.text("sp-text", HINT, label, 263, 31.2, 3, 3, Mode.LOC);
            m.region("sp", 226, 26.5, 66, 9.5, tip("&f" + escape(t("sp-tip", "Search items")),
                "&7" + escape(t("sp-tip2", "Name or item ID"))), f.search);
            m.hover("sp", "sp-frame", "color", LO, G, 2, "sp-text", "content", content(HINT, label),
                content(INK, label), 1);
        }
        if (f.favourites != null) {
            m.button("fav-top", 294, 26.5, 10, 9.5, null, Style.GREY, opts().z(-2).icon(f.favouriteIcon, 5.6)
                .tooltip(tip("&f" + escape(t("qa-fav", "Favourites")))).action(f.favourites));
        }
    }

    // ------------------------------------------------------------------ product card

    void card(int k, Product it, double l, double tp, double w, double h, boolean tag, boolean edit) {
        String p = "c" + k;
        m.entrance(p + "-g", 2 + (k / 3 + k % 3) * 1.2, 0, -7, 7, p + "-");
        m.bevel(p, l, tp, w, h, CARD, HI, LO, -2);
        m.inset(p + "-tile", l + 3, tp + 4, 24, 24, -1, TILE, LO);
        double icy = tag ? tp + 14.5 : tp + 16;
        double isz = tag ? 13 : 15;
        m.item(p + "-icon", it.material(), l + 15, icy, isz);
        if (tag) {
            m.rect(p + "-tag", l + 3.4, tp + 23, 23.2, 4.6, "#C6D9EE", 0);
            m.text(p + "-tag-text", "#2F5D8A", fit(it.shopShort(), 2.4, 21), l + 15, tp + 25.3, 2.4, 3, Mode.LOC);
        }
        if (it.amount() > 1) {
            m.text(p + "-amt", WHITE, "×" + it.amount(), l + 24.5, tp + 7, 2.6, 3, 'r', Mode.RAW, 0);
        }
        double x0 = l + 30;
        double x1 = l + w - 3;
        double xm = (x0 + x1) / 2;
        m.text(p + "-name", INK, fit(it.name(), 3.6, x1 - x0), xm, tp + 6.6, 3.6, 3, Mode.LOC);
        double bw = (x1 - x0 - 1.6) / 2;
        String name = escape(it.name());
        if (edit) {
            m.loop("us-" + OreScene.LOCAL_ID + "-" + p + "-pulse", p + "-frame", "color", 24, OUT, R, OUT);
            m.text(p + "-shop", MUTE, fit(it.shopName(), 3, x1 - x0), xm, tp + 13.4, 3, 3, Mode.LOC);
            if (it.move() != null) {
                m.region(p, l, tp, w, 18, tip("&f" + name, "&7" + escape(t("fav-move-tip",
                    "Left: move forward  ·  Right: move back"))), it.move());
            }
            m.button(p + "-rm", x0, tp + 19.5, x1 - x0, 8.5, t("unfav", "Remove"), Style.RED, opts().size(3)
                .tooltip(tip("&c" + escape(t("unfav-tip", "Remove from favourites")))).action(it.remove()));
            return;
        }
        if (it.buy() != null) {
            m.text(p + "-buy-price", G, it.buy(), x0 + bw / 2, tp + 14, 3.2, 3, Mode.RAW);
        }
        if (it.sell() != null) {
            m.text(p + "-sell-price", SELL, it.sell(), x1 - bw / 2, tp + 14, 3.2, 3, Mode.RAW);
        }
        String rule = "&7━━━━━━━━━━━━";
        List<String> lore = tip("&f" + name, "&8" + escape(it.shopName()), rule,
            it.buy() != null ? "&7" + escape(t("price-buy", "Buy price")) + "  &a" + escape(it.buy())
                : "&7" + escape(t("no-buy", "Not for sale")),
            it.sell() != null ? "&7" + escape(t("price-sell", "Sell price")) + "  &6" + escape(it.sell())
                : "&7" + escape(t("no-sell", "Cannot be sold")),
            rule, "&e" + escape(t("card-click", "Click for details / custom amount")));
        m.region(p, l, tp, w, 18, lore, it.details());
        m.hover(p, p + "-frame", "color", OUT, G, 2, p + "-bg", "color", CARD, CARD_HOV, 2,
            p + "-icon", "scale", m.scaleOf(p + "-icon", isz), m.scaleOf(p + "-icon", isz * 1.2), 3);
        String customKeys = "&7" + escape(t("custom-keys", "Right: custom amount"));
        if (it.buy() != null) {
            m.button(p + "-buy", x0, tp + 19.5, bw, 8.5, t("buy", "Buy"), Style.GREEN, opts().size(3)
                .tooltip(tip("&a" + escape(t("buy", "Buy")) + " " + name,
                    "&7" + escape(t("buy-keys", "Left ×1  |  Shift+Left ×64")), customKeys))
                .action(it.buyClick()));
        } else {
            m.bevel(p + "-buy", x0, tp + 19.5, bw, 8.5, "#C2C3C6", "#D4D5D8", HINT, -1, .4, .6, 1);
            m.text(p + "-buy-text", HINT, t("buy", "Buy"), x0 + bw / 2, tp + 23.35, 3, 3, Mode.LOC);
        }
        if (it.sell() != null) {
            m.button(p + "-sell", x1 - bw, tp + 19.5, bw, 8.5, t("sell", "Sell"), Style.GREY, opts().size(3)
                .tooltip(tip("&6" + escape(t("sell", "Sell")) + " " + name,
                    "&7" + escape(t("sell-keys", "Left ×1  |  Shift+Left all")), customKeys))
                .action(it.sellClick()));
        }
    }

    void cards(List<Product> products, double top, boolean tag, boolean edit) {
        double cw = (218 - 6) / 3.0;
        for (int k = 0; k < products.size(); k++) {
            int r = k / 3;
            int cc = k % 3;
            card(k, products.get(k), 86 + cc * (cw + 3), top + r * 35, cw, 32, tag, edit);
        }
    }

    // ------------------------------------------------------------------ screens

    /** A shop page: products as cards, nine per page. */
    void shop(Frame f, Material icon, String name, int total, Pager pager, List<Product> products) {
        shell(f);
        titleRow(icon, name, t("stats-shop", "{amount} items · {page}/{pages}", "amount", String.valueOf(total),
            "page", String.valueOf(pager.page + 1), "pages", String.valueOf(pager.pages)));
        searchPill(f);
        cards(products, 43, false, false);
        divider();
        pager(pager);
        hint("hint-shop", "Left: buy  ·  Right: custom  ·  Shift: bulk");
    }

    /** The hub: an optional featured banner over up to fifteen tiles. */
    void hub(Frame f, String name, int shops, Featured featured, List<Entry> tiles, Pager pager) {
        shell(f);
        titleRow(Material.EMERALD, name, t("stats-main", "{amount} shops", "amount", String.valueOf(shops)));
        searchPill(f);
        double top = 43;
        if (featured != null) {
            m.entrance("daily-g", 1, -14, 0, 7, "daily");
            m.bevel("daily", 86, 43, 218, 20, "#3A3B3D", "#5A5B5D", "#232425", -2);
            m.rect("daily-accent", 86.6, 43.6, 1.8, 18.8, GOLD, -1);
            m.loop("us-daily-accent", "daily-accent", "color", 30, GOLD, "#FFE08A", GOLD);
            m.inset("daily-tile", 91, 45.5, 15, 15, -1, "#2A2B2C", OUT);
            m.item("daily-icon", featured.icon, 98.5, 53, 10);
            m.text("daily-title", WHITE, fit(featured.name, 4.2, 100), 161.5, 49.3, 4.2, 3, Mode.LOC);
            m.text("daily-sub", "#B8B9BC", t("daily-sub", "Resets 00:00 · bonus sell prices", 3, 104), 161.5, 56.6,
                3, 3, Mode.LOC);
            m.text("daily-timer-label", HINT, t("daily-timer", "Resets in", 2.4, 34), 236, 48.8, 2.4, 3, Mode.LOC);
            m.text("daily-timer", GOLD, featured.timer, 236, 55.6, 4.4, 3, Mode.DYN);
            m.button("daily-go", 260, 47, 40, 12, t("go", "Open", 3.6, 34), Style.GREEN, opts().size(3.6)
                .tooltip(tip("&f" + escape(t("daily-go-tip", "Open the daily shop")))).action(featured.open));
            top = 66;
        }
        tiles(tiles, top);
        divider();
        if (pager.pages > 1) {
            pager(pager);
        } else {
            m.text("foot-l", HINT, "UltimateShop  ·  ArcMenu Ore UI", 88, 155.6, 2.6, 3, 'l', Mode.RAW, 0);
        }
        hint("hint-main", "Click a shop  ·  Shift+F closes", 240, 120);
    }

    private void tiles(List<Entry> tiles, double top) {
        double tw = (218 - 4 * 3) / 5.0;
        double th = 25.33;
        for (int i = 0; i < Math.min(TILES, tiles.size()); i++) {
            int r = i / 5;
            int cc = i % 5;
            double l = 86 + cc * (tw + 3);
            double tp = top + r * (th + 2);
            Entry s = tiles.get(i);
            String p = "tile" + i;
            m.entrance(p + "-g", 2 + (r + cc) * 1.1, 0, -7, 7, p + "-");
            m.bevel(p, l, tp, tw, th, CARD, HI, LO, -2);
            m.inset(p + "-slot", l + tw / 2 - 7.5, tp + 2.5, 15, 13, -1, TILE, LO);
            m.item(p + "-icon", s.icon(), l + tw / 2, tp + 9, 9.5);
            if (s.count() >= 0) {
                m.text(p + "-n", MUTE, String.valueOf(s.count()), l + tw - 2.5, tp + 4.6, 2.5, 3, 'r', Mode.RAW, 0);
            }
            String name = fit(s.shortName(), 3.2, tw - 4);
            m.text(p + "-name", INK, name, l + tw / 2, tp + 19.8, 3.2, 3, Mode.LOC);
            m.region(p, l, tp, tw, th, entryTip(s, true), s.open());
            m.hover(p, p + "-frame", "color", OUT, G, 2, p + "-bg", "color", CARD, CARD_HOV, 2,
                p + "-icon", "scale", m.scaleOf(p + "-icon", 9.5), m.scaleOf(p + "-icon", 12), 3,
                p + "-name", "content", content(INK, name), content(G, name), 1);
        }
    }

    // ------------------------------------------------------------------ detail dialog

    /**
     * The product dialog over the page it was opened from. Call after drawing that page into the same scene; the
     * page becomes a static backdrop, with the selected card outlined.
     */
    void detail(Detail d, int selectedCard, Click outside) {
        m.sink();
        if (selectedCard >= 0 && m.node("c" + selectedCard + "-frame") != null) {
            m.node("c" + selectedCard + "-frame").put("color", G);
        }
        Product it = d.product;
        double zd = .5;
        double zo = 1;
        double zw = 1.25;
        double zl = 1.5;
        double zb = 1.75;
        double zp = 2;
        double za = 2.75;
        double zi = 3.75;
        double zt = 4.5;
        m.rect("dim", 8, 4, 304, 166, "#000000", zd, 150);
        m.pop("md-g", 7, new double[][]{{0, .9}, {.6, 1.03}, {1, 1}}, "md-", "dq-");
        double l0 = 70;
        double t0 = 26;
        double wd = 180;
        double hd = 130;
        double mr = l0 + wd;
        List<String> outTip = tip("&7" + escape(t("md-shield", "Click outside to close")));
        double[][] strips = {{8, 4, 304, t0 - 4}, {8, t0 + hd, 304, 170 - t0 - hd}, {8, t0, l0 - 8, hd},
            {mr, t0, 312 - mr, hd}};
        for (int k = 0; k < strips.length; k++) {
            m.region("md-out" + k, strips[k][0], strips[k][1], strips[k][2], strips[k][3], outTip, outside);
        }
        m.rect("md-outline", l0, t0, wd, hd, OUT, zo);
        m.rect("md-win", l0 + .8, t0 + .8, wd - 1.6, hd - 1.6, WIN, zw);
        m.rect("md-win-hi", l0 + .8, t0 + .8, wd - 1.6, .8, WIN_HI, zl);
        m.rect("md-line", l0 + .8, t0 + 13.2, wd - 1.6, .8, OUT, zl);
        m.rect("md-body", l0 + .8, t0 + 14, wd - 1.6, hd - 14.8, BODY, zb);
        m.item("md-h-icon", it.material(), l0 + 8, t0 + 7.2, 7, zi, false);
        m.text("md-h-title", WHITE, t("md-title", "Trade · {name}", 3.8, wd - 44, "name", it.name()), l0 + wd / 2,
            t0 + 7.2, 3.8, zt, Mode.LOC);
        m.button("md-close", mr - 13, t0 + 2.4, 10.5, 9, "×", Style.DARK, opts().z(zp).zt(zt).size(4.6)
            .mode(Mode.RAW).hover(false).tooltip(tip("&f" + escape(t("close", "Close")))).action(d.close));
        m.hover("md-close", "md-close-bg", "color", WIN, R, 2);
        // Left column: the turning showcase (z 28 so the 3D model clears the panels).
        m.inset("md-tile", l0 + 5, t0 + 18, 58, 58, zp, TILE, LO);
        m.item("md-icon", it.material(), l0 + 34, t0 + 47, 36, 28, true);
        List<Object[]> spin = new ArrayList<>();
        for (int k = 0; k < 5; k++) {
            spin.add(new Object[]{k / 4.0, OreScene.map("x", 0, "y", 90 * k, "z", 0)});
        }
        m.track("us-md-spin", "md-icon", "rotation", spin, 100, "repeat", "open", "linear");
        m.rect("md-glow", l0 + 5.4, t0 + 74.6, 57.2, 1, G, za);
        m.loop("us-md-glow", "md-glow", "color", 24, G, G_HI, G);
        m.text("md-name", INK, fit(it.name(), 4.6, 56), l0 + 34, t0 + 82.5, 4.6, zt, Mode.LOC);
        m.text("md-shop", MUTE, fit(it.shopName(), 3, 56), l0 + 34, t0 + 89.2, 3, zt, Mode.LOC);
        List<String[]> tags = new ArrayList<>();
        if (d.limitTag != null) {
            tags.add(new String[]{"tag-limit", "Limit {limit}", "#D3EBC9", "#2D6A1C", d.limitTag});
        }
        if (d.sellable) {
            tags.add(new String[]{"tag-sell", "Sellable", "#F3DFC6", "#8A4A10", ""});
        }
        double[] widths = new double[tags.size()];
        double sum = 0;
        for (int k = 0; k < tags.size(); k++) {
            widths[k] = widest(tags.get(k)[0], tags.get(k)[1], 2.4, "limit", tags.get(k)[4]) + 4;
            sum += widths[k];
        }
        double tx = l0 + 34 - (sum + 2 * (tags.size() - 1)) / 2;
        for (int k = 0; k < tags.size(); k++) {
            String[] tag = tags.get(k);
            m.rect("md-tag" + k, tx, t0 + 93.5, widths[k], 6, tag[2], za);
            m.text("md-tag" + k + "-text", tag[3], t(tag[0], tag[1], "limit", tag[4]), tx + widths[k] / 2,
                t0 + 96.5, 2.4, zt, Mode.LOC);
            tx += widths[k] + 2;
        }
        m.inset("md-lore", l0 + 5, t0 + 103, 58, 23, zp);
        String[] lore = {"Goes to your inventory", "Drops if inventory full", "Shift+click: quick trade"};
        for (int k = 0; k < lore.length; k++) {
            String line = d.info != null ? (k < d.info.size() ? fit(d.info.get(k), 3, 54) : "")
                : t("md-lore" + k, lore[k], 3, 54);
            m.text("md-lore" + k, MUTE, line, l0 + 34, t0 + 108.3 + k * 6.4, 3, zt, Mode.LOC);
        }
        // Right column: unit prices, amount, totals and actions.
        double x0 = l0 + 68;
        double x1 = mr - 5;
        double pw = (x1 - x0 - 3) / 2;
        String[][] units = {{"unit-buy", "Buy price", d.buyUnit, G}, {"unit-sell", "Sell price", d.sellUnit, SELL}};
        for (int k = 0; k < 2; k++) {
            double pl = x0 + k * (pw + 3);
            String id = "md-p" + k;
            m.bevel(id, pl, t0 + 18, pw, 17, CARD, HI, LO, zp);
            m.rect(id + "-bar", pl + .5, t0 + 18.5, 1.4, 16, units[k][3], za);
            m.text(id + "-label", MUTE, t(units[k][0], units[k][1], 3, pw - 6), pl + pw / 2 + .7, t0 + 22.8, 3, zt,
                Mode.LOC);
            m.text(id + "-val", units[k][3], units[k][2] != null ? units[k][2] : "—", pl + pw / 2 + .7, t0 + 29.8,
                4.6, zt, Mode.RAW);
        }
        double qt = t0 + 44;
        String max = String.valueOf(d.max);
        amountButton("md-minus", x0, qt, "−", tip("&f" + escape(t("minus", "Amount -1")), "&7Shift: -10"), d.minus,
            d.selectable, d.syncAmount, zp, zt);
        amountButton("md-plus", x1 - 11, qt, "+", tip("&f" + escape(t("plus", "Amount +1")), "&7Shift: +10"), d.plus,
            d.selectable, d.syncAmount, zp, zt);
        double okw = 17;
        double il = x0 + 13;
        double iw = x1 - x0 - 26 - okw - 2;
        m.text("md-q-label", INK, t("qty", "Amount", 3, iw), il + iw / 2, t0 + 40.3, 3, zt, Mode.LOC);
        m.inset("dq-box", il, qt, iw, 13, zp);
        m.rect("dq-focus", il + .4, qt + 11.8, iw - .8, .8, LINE, za);
        m.text("dq-text", INK, d.qty, il + iw / 2, qt + 6.5, 5.4, zt, Mode.DYN);
        if (d.selectable) {
            Map<String, Object> display = new LinkedHashMap<>();
            display.put("before", c(INK) + d.qty);
            display.put("editing", c(G) + "{input}{caret}");
            display.put("draft", c(MUTE) + "{input}");
            display.put("complete", c(INK) + "{input}");
            m.input("dq", il, qt, iw, 13, "dq-text", "md-ok", t("qty-title", "Enter an amount (1-{max})", "max", max),
                Math.max(1, max.length()), display, tip("&f" + escape(t("qty", "Amount")),
                    "&7" + escape(t("qty-tip1", "Click and type a number")),
                    "&7" + escape(t("qty-tip2", "Enter or OK to apply"))));
        }
        m.button("md-ok", il + iw + 2, qt, okw, 13, t("ok", "OK", 3.4, okw - 2), Style.GREEN, opts().z(zp).zt(zt)
            .size(3.4).tooltip(tip("&f" + escape(t("ok-tip", "Apply the typed amount")))).action(d.ok)
            .after(d.syncAmount));
        if (d.selectable) {
            m.hover("dq", "dq-box-frame", "color", LO, G, 2, "dq-focus", "color", LINE, G, 3);
        }
        double cw = (x1 - x0 - 8) / 5;
        for (int k = 0; k < 5; k++) {
            int n = d.chipValues[k];
            m.button("md-chip" + k, x0 + k * (cw + 2), t0 + 60, cw, 8.5, String.valueOf(n), Style.LIGHT,
                opts().z(zp).zt(zt).size(3).mode(Mode.RAW).tooltip(tip("&f×" + n)).action(d.chips[k])
                    .after(d.syncAmount));
        }
        m.inset("md-sum", x0, t0 + 72, x1 - x0, 24, zp);
        double xm = (x0 + x1) / 2;
        String[][] rows = {{"sum-buy", "Buy total", G, d.buyTotal}, {"sum-sell", "Sell total", SELL, d.sellTotal},
            {"sum-bal", "Balance", MONEY, d.balance}};
        for (int k = 0; k < rows.length; k++) {
            double cy = t0 + 77 + k * 7;
            m.text("md-sum" + k + "-l", MUTE, t(rows[k][0], rows[k][1], 3, xm - x0 - 4), (x0 + xm) / 2, cy, 3, zt,
                Mode.LOC);
            m.text("md-sum" + k + "-v", rows[k][2], rows[k][3] == null ? "—" : rows[k][3], (xm + x1) / 2, cy, 3.2,
                zt, 'c', Mode.DYN, 5);
            if (k < 2) {
                m.rect("md-sum" + k + "-div", x0 + 3, cy + 3.2, x1 - x0 - 6, .4, "#DADBDE", za);
            }
        }
        double bw = (x1 - x0 - 3) / 2;
        String name = escape(it.name());
        tradeButton("md-buy", x0, t0 + 99, bw, Style.GREEN, d.buyLabel, d.buy, "&a", "buy", "Buy", name, d.buyTotal,
            zp, zt);
        tradeButton("md-sell", x1 - bw, t0 + 99, bw, Style.GREY, d.sellLabel, d.sell, "&6", "sell", "Sell", name,
            d.sellTotal, zp, zt);
        double ew = (x1 - x0 - 4) / 3;
        Object[][] extras = {{"fav", "Favourite", Material.NAME_TAG, "Toggle favourite", d.favourite},
            {"max", "Buy max", Material.CHEST, "Buy as many as fit", d.buyMax},
            {"all", "Sell all", Material.HOPPER, "Sell every one you carry", d.sellAll}};
        for (int k = 0; k < extras.length; k++) {
            String key = (String) extras[k][0];
            String label = t("x-" + key, (String) extras[k][1], 3, ew - 10);
            Click action = (Click) extras[k][4];
            m.button("md-x" + key, x0 + k * (ew + 2), t0 + 116.5, ew, 9.5, label, Style.GREY, opts().z(zp).zt(zt)
                .size(3).icon((Material) extras[k][2], 5).iconDx(-ew / 2 + 4.5).hover(action != null)
                .labelColor(action != null ? null : DISABLED)
                .tooltip(tip("&f" + escape(label), "&7" + escape(t("x-" + key + "-tip", (String) extras[k][3]))))
                .action(action));
        }
    }

    private void amountButton(String id, double l, double t, String label, List<String> tooltip, Click action,
                              boolean enabled, List<String> after, double zp, double zt) {
        m.button(id, l, t, 11, 13, label, Style.GREY, opts().z(zp).zt(zt).size(5.6).mode(Mode.RAW)
            .hover(enabled).labelColor(enabled ? null : DISABLED).tooltip(tooltip).action(enabled ? action : null)
            .after(after));
    }

    private void tradeButton(String id, double l, double t, double w, Style style, String label, Click action,
                             String color, String key, String fallback, String name, String total, double zp,
                             double zt) {
        boolean enabled = action != null;
        m.button(id, l, t, w, 14, label, style, opts().z(zp).zt(zt).size(3.8).mode(Mode.DYN).labelUpdate(5)
            .hover(enabled).labelColor(enabled ? null : DISABLED)
            .tooltip(tip(color + escape(t(key, fallback)) + " " + name,
                "&7" + escape(t("total", "Total")) + ": " + color + (total == null ? "—" : total)))
            .action(action));
    }

    // ------------------------------------------------------------------ search

    private void keyCell(String id, double l, double tp, double w, double h, String key, String fallback) {
        m.rect(id, l, tp, w, h, "#D6D7DA", -2);
        m.text(id + "-text", MUTE, t(key, fallback, 3, w - 3), l + w / 2, tp + h / 2 - .2, 3, 3, Mode.LOC);
    }

    /** A chip as wide as its widest translation, like the design; returns its width. */
    private double chip(String id, double l, double tp, double h, String text, double width, Style style, double size,
                        String labelColor, List<String> tooltip, Click action) {
        m.button(id, l, tp, width, h, text, style, opts().z(-2).size(size).labelColor(labelColor).tooltip(tooltip)
            .action(action));
        return width;
    }

    void searchBar(Search s, boolean result) {
        m.inset("q-box", 86, 43, 160, 13, -2);
        m.item("q-icon", Material.SPYGLASS, 93, 49.5, 7.5);
        m.rect("q-focus", 86.4, 55.2, 159.2, .8, LINE, -1);
        String placeholder = t("q-ph", "Type an item name…", 3.8, 140);
        String before;
        if (result) {
            before = c(INK) + escape(s.keyword);
            m.text("q-text", INK, escape(s.keyword), 172, 49.5, 3.8, 3, Mode.DYN);
        } else {
            before = content(HINT, placeholder);
            m.text("q-text", HINT, placeholder, 172, 49.5, 3.8, 3, Mode.LOC);
        }
        Map<String, Object> display = new LinkedHashMap<>();
        display.put("before", before);
        display.put("editing", c(INK) + "{input}" + c(G) + "{caret}");
        display.put("draft", c(INK) + "{input}");
        display.put("complete", c(INK) + "{input}");
        m.input("q", 86, 43, 160, 13, "q-text", "q-go", t("q-title", "Search items"), 16, display,
            tip("&f" + escape(t("q-tip1", "Click to type a keyword")), "&7" + escape(t("sp-tip2", "Name or item ID")),
                "&7" + escape(t("q-tip3", "Press Enter or Search"))));
        m.hover("q", "q-box-frame", "color", LO, G, 2, "q-focus", "color", LINE, G, 3);
        m.button("q-go", 249, 43, 27, 13, t("q-go", "Search", 3.6, 25), Style.GREEN, opts().z(-2).size(3.6)
            .action(s.submit).tooltip(tip("&f" + escape(t("q-go", "Search")))));
        m.button("q-clear", 279, 43, 25, 13, t("q-clear", "Clear", 3.6, 23), Style.GREY, opts().z(-2).size(3.6)
            .action(s.clear).tooltip(tip("&f" + escape(t("q-clear-tip", "Clear the keyword")))));
    }

    /** Hot keywords of every language at one index, for the design's widest-locale chip widths. */
    private List<List<String>> hotKeywords() {
        List<List<String>> columns = new ArrayList<>();
        for (String list : lang.all("hot-keywords", "Stone,Log,Wool,Redstone,Glass,Concrete")) {
            String[] words = list.split("\\s*,\\s*");
            for (int i = 0; i < words.length; i++) {
                if (columns.size() <= i) {
                    columns.add(new ArrayList<>());
                }
                columns.get(i).add(words[i]);
            }
        }
        return columns;
    }

    private List<String> hotWords() {
        return List.of(t("hot-keywords", "Stone,Log,Wool,Redstone,Glass,Concrete").split("\\s*,\\s*"));
    }

    private double hotWidth(int index, double size) {
        List<List<String>> columns = hotKeywords();
        double result = 0;
        if (index < columns.size()) {
            for (String word : columns.get(index)) {
                result = Math.max(result, ink(word, Math.max(size, 3.4)));
            }
        }
        return result + 8;
    }

    private List<String> hotTip(String word) {
        return tip("&f" + escape(t("hot-tip", "Search \"{keyword}\"", "keyword", word)));
    }

    /** The search page before a keyword is entered. */
    void search(Frame f, String name, Search s) {
        shell(f);
        titleRow(Material.HOPPER, name, t("stats-search", "{amount} shops", "amount", String.valueOf(s.shopCount)));
        searchBar(s, false);
        keyCell("hot-label", 86, 61, 30, 10, "hot", "Trending");
        double x = 119;
        List<String> hot = hotWords();
        for (int k = 0; k < hot.size(); k++) {
            double w = hotWidth(k, 3.2);
            if (x + w > 304) {
                break;
            }
            x += chip("hot" + k, x, 61, 10, hot.get(k), w, Style.LIGHT, 3.2, null, hotTip(hot.get(k)),
                s.searchFor.apply(hot.get(k))) + 2.5;
        }
        keyCell("recent-label", 86, 74, 30, 10, "recent", "Recent");
        for (int k = 0; k < Math.min(3, s.recent.size()); k++) {
            double l = 119 + k * 62;
            String word = fit(s.recent.get(k), 3.2, 56);
            String id = "recent" + k;
            m.text(id + "-text", INK, word, l + 29, 79, 3.2, 3, Mode.LOC);
            m.region(id, l, 74, 58, 10, tip("&f" + escape(t("recent-tip", "Search \"{keyword}\" again", "keyword",
                s.recent.get(k)))), s.searchFor.apply(s.recent.get(k)));
            m.hover(id, id + "-text", "content", content(INK, word), content(G, word), 1);
        }
        m.inset("ill", 86, 90, 218, 54, -2, "#DDDEE1", LINE);
        m.item("ill-icon", Material.COMPASS, 195, 107, 16);
        Map<String, Object> at = OreScene.map("x", 195 - OreScene.W / 2, "y", OreScene.H / 2 - 107, "z", 2);
        Map<String, Object> up = OreScene.map("x", 195 - OreScene.W / 2, "y", OreScene.H / 2 - 107 + 2, "z", 2);
        m.track("us-ill-icon", "ill-icon", "offset", List.of(new Object[]{0, at}, new Object[]{.5, up},
            new Object[]{1, at}), 40, "repeat", "open", "linear");
        m.text("ill-text", MUTE, t("ill", "Type a keyword to find any item", 3.4, 200), 195, 124, 3.4, 3, Mode.LOC);
        m.text("ill-sub", HINT, t("ill-sub", "Buy or sell straight from the results", 3, 200), 195, 131.5, 3, 3,
            Mode.LOC);
        divider();
        hint("hint-search", "Enter = search  ·  Clear = show all");
    }

    /** Search results: shop filter chips over six cards per page. */
    void results(Frame f, String name, Search s, int hits, int shops, List<Product> products, Pager pager) {
        shell(f);
        titleRow(Material.HOPPER, name, t("stats-results", "{amount} items · {shops} shops", "amount",
            String.valueOf(hits), "shops", String.valueOf(shops)));
        searchBar(s, true);
        double x = 86;
        for (int k = 0; k < s.filters.size(); k++) {
            String[] filter = s.filters.get(k);
            boolean on = k == s.activeFilter;
            String label = filter[0] + "  " + filter[1];
            double w = (k == 0 ? widestAll(filter[1]) : ink(label, 3.4)) + 8;
            if (x + w > 304) {
                break;
            }
            chip("flt" + k, x, 59.5, 9.5, label, w, on ? Style.LIGHT : Style.GREY, 3.4, on ? G : INK,
                tip("&f" + escape(t("flt-tip", "Only {shop}", "shop", filter[0]))),
                s.filter.apply(k));
            if (on) {
                m.rect("flt" + k + "-on", x + 2, 67.6, w - 4, .8, G, -1);
            }
            x += w + 2;
        }
        cards(products, 72, true, false);
        divider();
        pager(pager);
        hint("hint-results", "Left: buy  ·  Right: custom  ·  Shift: bulk");
    }

    private double widestAll(String count) {
        double result = 0;
        for (String word : lang.all("flt-all", "All")) {
            result = Math.max(result, ink(word + "  " + count, 3.4));
        }
        return result;
    }

    /** No results for the keyword: suggestions from the hot keywords. */
    void noResults(Frame f, String name, Search s) {
        shell(f);
        titleRow(Material.HOPPER, name, t("stats-empty", "0 items"));
        searchBar(s, true);
        empty(Material.BARRIER, t("empty-title", "No results for \"{keyword}\"", "keyword", s.keyword),
            t("empty-sub", "Try another keyword, or start here", 3, 200));
        List<String> hot = hotWords();
        int n = Math.min(4, hot.size());
        double[] widths = new double[n];
        double sum = 0;
        for (int k = 0; k < n; k++) {
            widths[k] = hotWidth(k, 3.2);
            sum += widths[k];
        }
        double x = 195 - (sum + 2.5 * (n - 1)) / 2;
        for (int k = 0; k < n; k++) {
            chip("sug" + k, x, 119, 10, hot.get(k), widths[k], Style.LIGHT, 3.2, null, hotTip(hot.get(k)),
                s.searchFor.apply(hot.get(k)));
            x += widths[k] + 2.5;
        }
        divider();
    }

    private void empty(Material icon, String title, String subtitle) {
        m.inset("empty", 86, 60, 218, 84, -2, "#DDDEE1", LINE);
        m.item("empty-icon", icon, 195, 82, 18);
        m.loop("us-empty-icon", "empty-icon", "scale", 30, m.scaleOf("empty-icon", 18), m.scaleOf("empty-icon", 20),
            m.scaleOf("empty-icon", 18));
        m.text("empty-title", INK, title, 195, 102, 4.4, 3, Mode.LOC);
        m.text("empty-sub", MUTE, subtitle, 195, 110, 3, 3, Mode.LOC);
    }

    // ------------------------------------------------------------------ favourites

    void favourites(Frame f, String name, int total, boolean edit, Click toggle, Click clear,
                    List<Product> products, Pager pager) {
        shell(f);
        titleRow(Material.NAME_TAG, name, edit ? t("stats-fav-edit", "Editing")
            : t("stats-fav", "{amount} saved", "amount", String.valueOf(total)));
        if (edit) {
            m.button("fav-clear", 228, 26.5, 37, 10, t("fav-clear", "Clear all", 3.4, 34), Style.RED, opts().z(-2)
                .size(3.4).action(clear).tooltip(tip("&c" + escape(t("fav-clear-tip", "Remove every favourite")))));
            m.button("fav-done", 268, 26.5, 36, 10, t("fav-done", "Done", 3.4, 33), Style.GREEN, opts().z(-2)
                .size(3.4).action(toggle).tooltip(tip("&f" + escape(t("fav-done-tip", "Stop editing")))));
        } else if (toggle != null) {
            m.button("fav-edit", 268, 26.5, 36, 10, t("fav-edit", "Edit", 3.4, 33), Style.GREY, opts().z(-2)
                .size(3.4).action(toggle).tooltip(tip("&f" + escape(t("fav-edit-tip", "Edit favourites")))));
        }
        if (products.isEmpty()) {
            empty(Material.NAME_TAG, t("fav-empty", "No favourites yet"),
                t("hint-fav", "Add items with \"Favourite\" in details", 3, 200));
        }
        cards(products, 43, !edit, edit);
        divider();
        pager(pager);
        if (edit) {
            hint("hint-fav-edit", "\"Remove\" deletes  ·  \"Done\" saves");
        } else {
            hint("hint-fav", "Add items with \"Favourite\" in details");
        }
    }
}
