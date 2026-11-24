package com.fentai.ushop.arcmenu;

import cn.superiormc.ultimateshop.api.ArcMenuRenderer;
import cn.superiormc.ultimateshop.gui.AbstractGUI;
import cn.superiormc.ultimateshop.gui.GUIStatus;
import cn.superiormc.ultimateshop.gui.InvGUI;
import cn.superiormc.ultimateshop.managers.MenuStatusManager;
import cn.superiormc.ultimateshop.objects.ObjectShop;
import cn.superiormc.ultimateshop.objects.buttons.ObjectItem;
import cn.superiormc.ultimateshop.objects.menus.ObjectMenu;

import com.fentai.arcmenu.api.ArcMenuApi;
import com.fentai.arcmenu.api.ArcMenuCapability;
import com.fentai.arcmenu.api.ArcMenuHistoryPolicy;
import com.fentai.arcmenu.api.ArcMenuMenuException;
import com.fentai.arcmenu.api.ArcMenuMenuRegistration;
import com.fentai.arcmenu.api.ArcMenuOpenOutcome;
import com.fentai.arcmenu.api.ArcMenuOpenOutcomeKind;
import com.fentai.arcmenu.api.ArcMenuOpenRequest;
import com.fentai.arcmenu.api.ArcMenuRegionContext;
import com.fentai.arcmenu.api.ArcMenuRegionResult;
import com.fentai.arcmenu.api.ArcMenuRegistration;
import com.fentai.arcmenu.api.ArcMenuSessionCloseReason;
import com.fentai.arcmenu.api.ArcMenuSessionClosedEvent;
import com.fentai.arcmenu.api.ArcMenuSessionSnapshot;
import com.fentai.arcmenu.api.ArcMenuStateScope;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shows UltimateShop's ArcMenu menus as ArcMenu menus registered from code: every screen is generated as menu YAML,
 * so ArcMenu renders, animates and handles it exactly like a menu file. Each player owns three registrations, kept
 * until they leave: two page slots that alternate so a new screen opens with its transition, and one for the product
 * details dialog.
 */
final class OreRenderer implements ArcMenuRenderer, Listener, AutoCloseable {

    private static final String PREFIX = "ushoparc:";

    private static final String DETAILS = "d";

    /**
     * Live values (amount, totals, labels, countdown) are the player's ArcMenu META values under this prefix. They
     * are written before a screen opens, so its first frame and its Arcitx box already show them.
     */
    private static final String KEY = "ushoparc-";

    private static final List<String> KEYS = List.of("qty", "buy-total", "sell-total", "label0", "label1", "timer");

    static String value(String key) {
        return "{meta:" + KEY + key + "}";
    }

    private final UShopArcMenuPlugin plugin;

    private final ArcMenuApi api;

    private final Map<UUID, Viewer> viewers = new ConcurrentHashMap<>();

    private final Map<UUID, Deque<String>> recent = new ConcurrentHashMap<>();

    /** One player's registered menus and business handlers, and the screen currently shown. */
    private final class Viewer {

        final Player player;

        final String base;

        final Map<String, ArcMenuMenuRegistration> menus = new HashMap<>();

        final Map<String, Map<String, OreScene.Click>> handlers = new ConcurrentHashMap<>();

        final Map<String, Map<String, ArcMenuRegistration>> regions = new HashMap<>();

        OreSession session;

        /** Menu shown (or being opened) and its session token once opened. */
        String shown;

        UUID token;

        int pending;

        ScheduledTask clock;

        /** The screen a button left for an UltimateShop inventory menu (the sell-all chest). */
        OreSession resume;

        /** The inventory menu being opened after our screen closed; its open event passes through. */
        InvGUI handOff;

        Viewer(Player player) {
            this.player = player;
            this.base = PREFIX + player.getUniqueId() + "/";
        }

        boolean owns(String menuId) {
            return menuId != null && menuId.startsWith(base);
        }
    }

    /** Keeps the Ore canvas known to ArcMenu for the plugin's lifetime; see {@link #registerCanvas()}. */
    private ArcMenuMenuRegistration canvas;

    OreRenderer(UShopArcMenuPlugin plugin, ArcMenuApi api) {
        this.plugin = plugin;
        this.api = api;
        registerCanvas();
    }

    /**
     * ArcMenu refreshes its resources the first time a registered menu brings a canvas it has not seen, which made
     * the first shop open of a server run wait. One permanent menu with the Ore canvas, registered while the server
     * starts, means player menus never bring a new canvas.
     */
    private void registerCanvas() {
        OreScene scene = new OreScene("us-rise");
        scene.rect("canvas", 0, 0, 1, 1, OreScene.OUT, 0);
        try {
            canvas = api.menus().register(plugin, PREFIX + "canvas", scene.menu());
        } catch (RuntimeException error) {
            plugin.getLogger().warning("Could not register the Ore canvas: " + error.getMessage());
        }
    }

    UShopArcMenuPlugin plugin() {
        return plugin;
    }

    List<String> recent(Player player) {
        Deque<String> words = recent.get(player.getUniqueId());
        return words == null ? List.of() : List.copyOf(words);
    }

    void remember(Player player, String keyword) {
        if (keyword.isBlank()) {
            return;
        }
        Deque<String> words = recent.computeIfAbsent(player.getUniqueId(), id -> new ArrayDeque<>());
        words.remove(keyword);
        words.addFirst(keyword);
        while (words.size() > 3) {
            words.removeLast();
        }
    }

    // ------------------------------------------------------------------ UltimateShop hook

    @Override
    public boolean handles(ObjectMenu menu, Player player) {
        return api.capabilities().containsAll(Set.of(ArcMenuCapability.REGISTERED_MENUS,
            ArcMenuCapability.INLINE_TEXT_INPUT))
            && !plugin.getConfig().getStringList("excluded-menus").contains(menu.getName());
    }

    @Override
    public boolean open(Player player, ObjectMenu menu, ObjectShop shop, boolean bypass, boolean reopen,
                        boolean editing) {
        return new OreSession(player, menu, shop, bypass, editing, this).tryOpen(reopen);
    }

    @Override
    public boolean openInfo(Player player, ObjectItem item) {
        if (MenuStatusManager.menuStatusManager.getOpeningGUI(player) instanceof OreSession session
            && session.current()) {
            session.openDetails(item);
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ screens

    private Viewer viewer(OreSession session) {
        return viewers.computeIfAbsent(session.getPlayer().getUniqueId(), id -> new Viewer(session.getPlayer()));
    }

    /** Shows the menu page; animate opens it as a new screen, otherwise the shown page is rebuilt in place. */
    boolean showPage(OreSession session, boolean animate) {
        Viewer viewer = viewer(session);
        String shown = viewer.shown;
        if (shown == null) {
            ArcMenuSessionSnapshot current = api.sessions().current(viewer.player);
            shown = current == null ? null : current.getId();
        }
        String slot = viewer.owns(shown) ? shown.substring(viewer.base.length()) : null;
        boolean inPlace = !animate && slot != null && !slot.equals(DETAILS);
        String target = inPlace ? slot : "a".equals(slot) ? "b" : "a";
        long start = System.nanoTime();
        OreScene scene = OreScreens.page(session);
        return show(viewer, session, scene, target, !inPlace, start);
    }

    /** Shows the details dialog over the page; an open dialog is rebuilt in place. */
    boolean showDetails(OreSession session, boolean animate) {
        Viewer viewer = viewer(session);
        boolean inPlace = (viewer.base + DETAILS).equals(viewer.shown);
        long start = System.nanoTime();
        OreScene scene = OreScreens.details(session);
        return show(viewer, session, scene, DETAILS, !inPlace, start);
    }

    /** Live amount, totals and labels of the dialog, without rebuilding it. */
    void updateAmount(OreSession session) {
        Viewer viewer = viewers.get(session.getPlayer().getUniqueId());
        if (viewer == null || viewer.session != session) {
            return;
        }
        writeValues(viewer.player, session);
        for (String node : OreScreens.AMOUNT_NODES) {
            api.refresh(viewer.player, node);
        }
    }

    /** Writes the values the screen reads: the dialog's amount values and the featured countdown. */
    private void writeValues(Player player, OreSession session) {
        api.state().set(player, ArcMenuStateScope.META, KEY + "timer", countdown());
        if (session != null && session.selected != null) {
            OreScreens.amountValues(session).forEach((key, text) ->
                api.state().set(player, ArcMenuStateScope.META, KEY + key, text));
        }
    }

    private void clearValues(Player player) {
        for (String key : KEYS) {
            api.state().remove(player, ArcMenuStateScope.META, KEY + key);
        }
    }

    private String countdown() {
        LocalTime reset;
        try {
            reset = LocalTime.parse(plugin.getConfig().getString("featured.reset", "00:00"));
        } catch (DateTimeParseException error) {
            reset = LocalTime.MIDNIGHT;
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime next = now.toLocalDate().atTime(reset);
        if (!next.isAfter(now)) {
            next = next.plusDays(1);
        }
        long seconds = Duration.between(now, next).getSeconds();
        return String.format("%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
    }

    private boolean show(Viewer viewer, OreSession session, OreScene scene, String slot, boolean open, long start) {
        String id = viewer.base + slot;
        String menu = scene.menu();
        String animations = scene.animations();
        long built = System.nanoTime();
        writeValues(viewer.player, session);
        try {
            ArcMenuMenuRegistration registration = viewer.menus.get(id);
            if (registration == null || !registration.isRegistered()) {
                viewer.menus.put(id, api.menus().register(plugin, id, menu, animations));
            } else {
                registration.update(menu, animations, !open);
            }
        } catch (ArcMenuMenuException error) {
            plugin.getLogger().warning("Could not build " + session.getMenu().getName() + " for "
                + viewer.player.getName() + ":\n" + String.join("\n", error.getProblems()));
            return false;
        } catch (RuntimeException error) {
            plugin.getLogger().warning("Could not register " + id + ": " + error.getMessage());
            return false;
        }
        bindHandlers(viewer, id, scene.handlers());
        long registered = System.nanoTime();
        viewer.session = session;
        debug(id + (open ? " open" : " rebuild") + ": build " + ms(start, built) + " ms, register "
            + ms(built, registered) + " ms, " + menu.length() / 1024 + " KiB");
        if (!open) {
            return true;
        }
        String previous = viewer.shown;
        boolean ours = viewer.owns(previous);
        viewer.shown = id;
        viewer.pending++;
        boolean[] result = {true};
        boolean[] done = {false};
        ArcMenuOpenRequest request = api.menus().open(plugin, viewer.player, id, List.of(),
            ours ? ArcMenuHistoryPolicy.PRESERVE : ArcMenuHistoryPolicy.PUSH, outcome -> {
                done[0] = true;
                debug(id + " " + outcome.getKind() + " after " + ms(registered, System.nanoTime()) + " ms");
                result[0] = opened(viewer, id, previous, outcome);
            });
        if (!request.isAccepted() && !done[0]) {
            viewer.pending--;
            viewer.shown = previous;
            return false;
        }
        return result[0];
    }

    private boolean opened(Viewer viewer, String id, String previous, ArcMenuOpenOutcome outcome) {
        viewer.pending--;
        if (outcome.getKind() == ArcMenuOpenOutcomeKind.OPENED && outcome.getSession() != null) {
            if (!id.equals(viewer.shown)) {
                // A newer screen was requested meanwhile; it takes over when it opens.
                return true;
            }
            viewer.token = outcome.getSession().getToken();
            startClock(viewer);
            return true;
        }
        if (outcome.getKind() == ArcMenuOpenOutcomeKind.ERROR) {
            plugin.getLogger().warning("ArcMenu could not open " + id + ": " + outcome.getReason());
        }
        if (id.equals(viewer.shown)) {
            viewer.shown = previous;
        }
        if (!viewer.owns(viewer.shown) && viewer.pending == 0) {
            // The first screen never opened: nothing of ours is shown.
            finish(viewer);
        }
        return false;
    }

    /** Ticks the featured shop countdown once a second while one of our screens is shown. */
    private void startClock(Viewer viewer) {
        if (viewer.clock != null) {
            return;
        }
        viewer.clock = viewer.player.getScheduler().runAtFixedRate(plugin, task -> {
            if (viewer.session == null || viewer.shown == null) {
                return;
            }
            api.state().set(viewer.player, ArcMenuStateScope.META, KEY + "timer", countdown());
            api.refresh(viewer.player, "daily-timer");
        }, null, 20L, 20L);
    }

    // ------------------------------------------------------------------ regions

    /** Region handlers delegate to the latest scene's actions; region ids that disappeared are released. */
    private void bindHandlers(Viewer viewer, String id, Map<String, OreScene.Click> handlers) {
        viewer.handlers.put(id, Map.copyOf(handlers));
        Map<String, ArcMenuRegistration> registered = viewer.regions.computeIfAbsent(id, key -> new HashMap<>());
        registered.entrySet().removeIf(entry -> {
            if (handlers.containsKey(entry.getKey()) && entry.getValue().isRegistered()) {
                return false;
            }
            entry.getValue().unregister();
            return true;
        });
        for (String region : handlers.keySet()) {
            if (!registered.containsKey(region)) {
                registered.put(region, api.menus().registerRegionHandler(plugin, id, region,
                    context -> activate(viewer, id, context)));
            }
        }
    }

    private ArcMenuRegionResult activate(Viewer viewer, String id, ArcMenuRegionContext context) {
        OreScene.Click click = viewer.handlers.getOrDefault(id, Map.of()).get(context.getRegionId());
        if (click == null || viewer.session == null || !viewer.session.current() && !reclaim(viewer)) {
            return ArcMenuRegionResult.CONTINUE_YAML;
        }
        boolean shift = context.isSneaking();
        ClickType type = switch (context.getButton()) {
            case RIGHT -> shift ? ClickType.SHIFT_RIGHT : ClickType.RIGHT;
            case MIDDLE -> ClickType.MIDDLE;
            default -> shift ? ClickType.SHIFT_LEFT : ClickType.LEFT;
        };
        click.accept(type, context.getInputValue());
        return ArcMenuRegionResult.CONTINUE_YAML;
    }

    // ------------------------------------------------------------------ closing

    /** Closes the player's screen, if it is one of ours, and ends the shop session. */
    void close(Player player) {
        Viewer viewer = viewers.get(player.getUniqueId());
        if (viewer == null) {
            return;
        }
        ArcMenuSessionSnapshot current = api.sessions().current(player);
        if (current != null && viewer.owns(current.getId())) {
            api.close(player);
        }
        finish(viewer);
    }

    @EventHandler
    public void onSessionClosed(ArcMenuSessionClosedEvent event) {
        Player player = event.getPlayer();
        Viewer viewer = viewers.get(player.getUniqueId());
        if (viewer == null || viewer.session == null || !viewer.owns(event.getSession().getId())) {
            return;
        }
        debug(player.getName() + ": ArcMenu closed " + event.getSession().getId() + " (" + event.getReason() + ")");
        if (event.getReason() != ArcMenuSessionCloseReason.REPLACED) {
            if (event.getSession().getToken().equals(viewer.token)) {
                finish(viewer);
            }
            return;
        }
        // Replaced by our next screen, or by another menu: decide once the replacement settled.
        player.getScheduler().run(plugin, task -> {
            if (viewer.session == null || viewer.pending > 0) {
                return;
            }
            ArcMenuSessionSnapshot current = api.sessions().current(player);
            if (current == null || !viewer.owns(current.getId())) {
                finish(viewer);
            }
        }, () -> release(viewer));
    }

    /**
     * An inventory opened under an ArcMenu screen never shows on the client, so the sell-all chest would stay open
     * unseen and swallow every click. Like UltimateShop's own ArcMenu interface, close our screen first: hold the
     * inventory, close the screen, then open the inventory menu on the next tick. Its closing brings the page back.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        Viewer viewer = viewers.get(player.getUniqueId());
        if (viewer == null || viewer.session == null
            || !(MenuStatusManager.menuStatusManager.getOpeningGUI(player) instanceof InvGUI gui)
            || gui == viewer.handOff) {
            return;
        }
        ArcMenuSessionSnapshot current = api.sessions().current(player);
        if (current == null || !viewer.owns(current.getId())) {
            return;
        }
        event.setCancelled(true);
        viewer.resume = viewer.session;
        viewer.handOff = gui;
        debug(player.getName() + ": closing the screen for " + gui.getClass().getSimpleName());
        api.close(player);
        player.getScheduler().run(plugin, task -> {
            try {
                if (player.isOnline() && MenuStatusManager.menuStatusManager.getOpeningGUI(player) == gui) {
                    gui.openGUI(true);
                }
            } finally {
                viewer.handOff = null;
            }
        }, null);
    }

    /**
     * An UltimateShop inventory menu opened from our screen (the sell-all chest) becomes the player's current menu
     * and clears that status when it closes, leaving our screen shown but no longer current. Take it back.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        Viewer viewer = viewers.get(player.getUniqueId());
        if (viewer == null || viewer.resume == null && (viewer.session == null || viewer.shown == null)) {
            return;
        }
        player.getScheduler().run(plugin, task -> afterInventory(viewer), null);
    }

    /** Back from an inventory menu: take the shown screen back, or reopen the page the player left. */
    private void afterInventory(Viewer viewer) {
        Player player = viewer.player;
        if (!player.isOnline() || player.getOpenInventory().getType() != InventoryType.CRAFTING) {
            return;
        }
        OreSession resume = viewer.resume;
        viewer.resume = null;
        if (reclaim(viewer)) {
            debug(player.getName() + ": screen taken back after an inventory menu");
            return;
        }
        AbstractGUI opening = MenuStatusManager.menuStatusManager.getOpeningGUI(player);
        if (resume != null && (opening == null || opening instanceof InvGUI)) {
            debug(player.getName() + ": reopening " + resume.getMenu().getName() + " after an inventory menu");
            resume.resume().tryOpen(true);
        }
    }

    /** Makes the shown screen's session current again when no other UltimateShop menu is in front of it. */
    private boolean reclaim(Viewer viewer) {
        OreSession session = viewer.session;
        if (session == null) {
            return false;
        }
        if (session.current()) {
            return true;
        }
        Player player = viewer.player;
        if (player.getOpenInventory().getType() != InventoryType.CRAFTING) {
            return false;
        }
        ArcMenuSessionSnapshot current = api.sessions().current(player);
        if (current == null || !viewer.owns(current.getId())) {
            debug(player.getName() + ": cannot take the screen back, ArcMenu shows "
                + (current == null ? "nothing" : current.getId()));
            return false;
        }
        AbstractGUI opening = MenuStatusManager.menuStatusManager.getOpeningGUI(player);
        if (opening != null && !(opening instanceof InvGUI)) {
            return false;
        }
        MenuStatusManager.menuStatusManager.setGUIStatus(player, GUIStatus.of(session, GUIStatus.Status.CAN_REOPEN));
        // Selling changed the balance, stock and limits.
        session.constructGUI();
        return session.current();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Viewer viewer = viewers.get(event.getPlayer().getUniqueId());
        if (viewer != null) {
            release(viewer);
        }
        recent.remove(event.getPlayer().getUniqueId());
    }

    /** Ends the shown screen's shop session; the registrations stay for the player's next open. */
    private void finish(Viewer viewer) {
        if (viewer.session != null) {
            StackTraceElement caller = new Throwable().getStackTrace()[1];
            debug(viewer.player.getName() + ": shop session ended (" + caller.getMethodName() + ")");
        }
        if (viewer.clock != null) {
            viewer.clock.cancel();
            viewer.clock = null;
        }
        if (viewer.session != null && viewer.player.isOnline()) {
            clearValues(viewer.player);
        }
        OreSession session = viewer.session;
        viewer.session = null;
        viewer.shown = null;
        viewer.token = null;
        if (session != null) {
            session.finishGUI();
        }
    }

    /** Drops every registration of the player. */
    private void release(Viewer viewer) {
        finish(viewer);
        viewers.remove(viewer.player.getUniqueId(), viewer);
        viewer.regions.values().forEach(regions -> regions.values().forEach(ArcMenuRegistration::unregister));
        viewer.menus.values().forEach(ArcMenuRegistration::unregister);
    }

    @Override
    public void close() {
        List.copyOf(viewers.values()).forEach(this::release);
        if (canvas != null) {
            canvas.unregister();
        }
    }

    private void debug(String message) {
        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info(message);
        }
    }

    private static String ms(long from, long to) {
        return String.format(Locale.ROOT, "%.1f", (to - from) / 1e6);
    }
}
