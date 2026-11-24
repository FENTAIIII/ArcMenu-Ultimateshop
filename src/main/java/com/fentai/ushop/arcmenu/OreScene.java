package com.fentai.ushop.arcmenu;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * One Ore UI menu document: a line-by-line port of the approved design generator's component library (ore.py).
 * Design coordinates are a 320x180 grid (top-left origin, y down), emitted in ArcMenu coordinates (centred origin,
 * y up) with the corrections measured on ArcMenu's default shop: rectangles lose ~8.5% of their height from the top,
 * text is centred on offset.x with offset.y at its bottom, frames are exact. Layers follow depth-mode layered.
 */
final class OreScene {

    static final double W = 320;
    static final double H = 180;
    private static final double RK = .915;
    private static final double BLOCK_Z = .1;
    static final String LOCAL_ID = "ore";

    // Bedrock Ore UI palette, as used by ArcMenu's default shop.
    static final String OUT = "#1E1E1F";
    static final String WIN = "#48494A";
    static final String WIN_HI = "#6D6E70";
    static final String WIN_LO = "#313233";
    static final String BODY = "#E6E8EB";
    static final String SIDE = "#D9DADD";
    static final String CARD = "#D0D1D4";
    static final String HI = "#F4F4F5";
    static final String LO = "#58585A";
    static final String TILE = "#B9BABD";
    static final String LINE = "#C4C5C9";
    static final String INK = "#1B1C1F";
    static final String MUTE = "#7C7E84";
    static final String MONEY = "#14935A";
    static final String G = "#3C8527";
    static final String G_HI = "#6CC349";
    static final String G_LO = "#1D4D13";
    static final String G_HOV = "#52A535";
    static final String R = "#C33636";
    static final String R_HI = "#E26A6A";
    static final String R_LO = "#6E1616";
    static final String R_HOV = "#D84B4B";
    static final String GOLD = "#E0A526";
    static final String SELL = "#B5651D";
    static final String CARD_HOV = "#DCDDE0";
    static final String GREY_HOV = "#E1E2E5";
    static final String WHITE = "#FFFFFF";
    static final String HINT = "#9A9B9E";
    static final String DISABLED = "#A9AAAD";

    enum Style {
        GREEN(G, G_HI, G_LO, G_HOV, WHITE), GREY(CARD, HI, LO, GREY_HOV, INK), RED(R, R_HI, R_LO, R_HOV, WHITE),
        DARK(WIN, WIN_HI, WIN_LO, "#5A5B5D", WHITE), LIGHT(HI, WHITE, LO, WHITE, INK);

        final String face;
        final String hi;
        final String lo;
        final String hover;
        final String text;

        Style(String face, String hi, String lo, String hover, String text) {
            this.face = face;
            this.hi = hi;
            this.lo = lo;
            this.hover = hover;
            this.text = text;
        }
    }

    /**
     * How a text node is measured, as the design generator did it. LOC is localized text (the design referenced it
     * through a language placeholder: CJK size floor, centred, k .62); DYN is runtime content (placeholders, session
     * values); RAW is literal text measured from its characters.
     */
    enum Mode { RAW, LOC, DYN }

    /** A region's business action; input is the committed Arcitx value for a submit region, otherwise null. */
    @FunctionalInterface
    interface Click {
        void accept(ClickType type, String input);
    }

    private record Group(String id, List<String> prefixes, double delay, double dx, double dy, double move,
                         double[][] pop) {
    }

    private Map<String, Object> front = new LinkedHashMap<>();

    private final Map<String, Object> back = new LinkedHashMap<>();

    private final Map<String, Object> tracks = new LinkedHashMap<>();

    private final List<Group> groups = new ArrayList<>();

    private final Map<String, Click> handlers = new LinkedHashMap<>();

    private final String transition;

    OreScene(String transition) {
        this.transition = transition;
    }

    Map<String, Click> handlers() {
        return handlers;
    }

    // ------------------------------------------------------------------ primitives

    static Map<String, Object> map(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            result.put((String) pairs[i], pairs[i + 1]);
        }
        return result;
    }

    private static Map<String, Object> offset(double x, double y, double z) {
        return map("x", x, "y", y, "z", z);
    }

    void rect(String id, double l, double t, double w, double h, String color, double z) {
        rect(id, l, t, w, h, color, z, -1);
    }

    void rect(String id, double l, double t, double w, double h, String color, double z, int opacity) {
        double hs = h / RK;
        double bottom = H / 2 - (t + h);
        Map<String, Object> node = map("type", "rectangle", "width", w, "height", hs, "color", color,
            "offset", offset(l + w / 2 - W / 2, bottom + hs / 2, z));
        if (opacity >= 0) {
            node.put("opacity", opacity);
        }
        front.put(id, node);
    }

    void frame(String id, double l, double t, double w, double h, String color, double z, double thickness) {
        front.put(id, map("type", "frame", "width", w, "height", h, "thickness", thickness, "color", color,
            "offset", offset(l + w / 2 - W / 2, H / 2 - (t + h / 2), z)));
    }

    /** Text: color is a hex colour (without &), x the left edge / centre / right edge by align ('l', 'c', 'r'). */
    void text(String id, String color, String content, double x, double cy, double size, double z, char align,
              Mode mode, int update) {
        String body = mode == Mode.DYN ? content : escape(content);
        String plain = strip(body);
        double rendered = mode == Mode.LOC ? Math.max(size, 3.4) : floorSize(plain, size);
        double width = mode == Mode.RAW && !plain.contains("%") && !plain.contains("{") ? ink(plain, rendered) : 0;
        double cx = align == 'l' ? x + width / 2 : align == 'r' ? x - width / 2 : x;
        double k = mode == Mode.RAW && cjk(plain) ? .507 : .62;
        Map<String, Object> node = map("type", "text", "content", "&" + color + body, "size", rendered,
            "alignment", "center", "line-width", 400, "offset", offset(cx - W / 2, H / 2 - cy - k * rendered, z));
        if (update > 0) {
            node.put("update", update);
        }
        front.put(id, node);
    }

    void text(String id, String color, String content, double x, double cy, double size, double z, Mode mode) {
        text(id, color, content, x, cy, size, z, 'c', mode, 0);
    }

    /** Cube models get the inventory look (GUI context, turned 180, flat); other items render as sprites. */
    void item(String id, Material material, double cx, double cy, double size, double z, boolean forceFlat) {
        boolean block = !forceFlat && BLOCKS.contains(material.getKey().getKey());
        Map<String, Object> node = map("type", "item", "material", material.getKey().toString(),
            "context", block ? "GUI" : "FIXED", "offset", offset(cx - W / 2, H / 2 - cy, z),
            "scale", map("x", size, "y", size, "z", block ? BLOCK_Z : size));
        if (block) {
            node.put("rotation", map("y", 180.0));
        }
        front.put(id, node);
    }

    void item(String id, Material material, double cx, double cy, double size) {
        item(id, material, cx, cy, size, 2, false);
    }

    Map<String, Object> scaleOf(String id, double size) {
        @SuppressWarnings("unchecked")
        Map<String, Object> node = (Map<String, Object>) front.get(id);
        boolean block = node != null && "GUI".equals(node.get("context"));
        return map("x", size, "y", size, "z", block ? BLOCK_Z : size);
    }

    /** Hit area; with an action the region becomes clickable and calls it with the click type. */
    void region(String id, double l, double t, double w, double h, List<String> tooltip, Click action) {
        region(id, l, t, w, h, tooltip, action, List.of());
    }

    /** As region(), with YAML actions that run after the business action (for example arcitx-set). */
    void region(String id, double l, double t, double w, double h, List<String> tooltip, Click action,
                List<String> after) {
        Map<String, Object> node = map("x", l + w / 2 - W / 2, "y", H / 2 - (t + h / 2), "width", w, "height", h);
        if (tooltip != null && !tooltip.isEmpty()) {
            node.put("tooltip", tooltip);
        }
        if (action != null) {
            List<String> actions = new ArrayList<>();
            actions.add("sound: UI_BUTTON_CLICK-0.6-1.2");
            actions.addAll(after);
            node.put("actions", map("all", actions));
            handlers.put(id, action);
        }
        back.put(id, node);
    }

    /** Arcitx text input bound to a text node, committed by the submit region. */
    void input(String id, double l, double t, double w, double h, String textNode, String submit, String title,
               int maxLength, Map<String, Object> display, List<String> tooltip) {
        Map<String, Object> node = map("x", l + w / 2 - W / 2, "y", H / 2 - (t + h / 2), "width", w, "height", h,
            "type", "Arcitx", "text", textNode, "submit", submit, "title", escape(title), "initial", "",
            "max-length", maxLength, "display", display);
        if (tooltip != null && !tooltip.isEmpty()) {
            node.put("tooltip", tooltip);
        }
        back.put(id, node);
    }

    /** Raw ArcMenu text node in ArcMenu coordinates, for hand-placed elements such as the brand mark. */
    void rawText(String id, String content, double x, double y, double z, double size, double scale) {
        front.put(id, map("type", "text", "content", content, "size", size, "alignment", "center", "line-width", 400,
            "offset", offset(x, y, z), "scale", map("x", scale, "y", scale)));
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> node(String id) {
        return (Map<String, Object>) front.get(id);
    }

    // ------------------------------------------------------------------ animation

    String track(String name, String target, String property, List<Object[]> frames, int duration, String loop,
                 String trigger, String easing) {
        List<Object> keyframes = new ArrayList<>();
        for (Object[] frame : frames) {
            keyframes.add(map("at", frame[0], "value", frame[1]));
        }
        tracks.put(name, map("target", target, "property", property, "duration", duration, "easing", easing,
            "loop", loop, "trigger", trigger, "keyframes", keyframes));
        return name;
    }

    /** A repeating colour/scale loop started on open: values spread evenly over the duration. */
    void loop(String name, String target, String property, int duration, Object... values) {
        List<Object[]> frames = new ArrayList<>();
        for (int i = 0; i < values.length; i++) {
            frames.add(new Object[]{values.length == 1 ? 0 : (double) i / (values.length - 1), values[i]});
        }
        track(name, target, property, frames, duration, "repeat", "open", "linear");
    }

    /** On hover the targets animate from -> to; leaving restores them. Each pair: target, property, from, to, ticks. */
    void hover(String regionId, Object... pairs) {
        List<String> names = new ArrayList<>();
        for (int i = 0; i + 4 < pairs.length; i += 5) {
            String target = (String) pairs[i];
            names.add(track("us-" + LOCAL_ID + "-" + target, target, (String) pairs[i + 1],
                List.of(new Object[]{0, pairs[i + 2]}, new Object[]{1, pairs[i + 3]}), (Integer) pairs[i + 4],
                "once", "api", "ease-out"));
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> region = (Map<String, Object>) back.get(regionId);
        region.put("on-hover", names.stream().map(n -> "animate: " + n).toList());
        region.put("on-leave", names.stream().map(n -> "stop-animation: " + n).toList());
    }

    /** Groups every frontend node whose id starts with a prefix and slides it in from (dx, dy) after delay ticks. */
    void entrance(String groupId, double delay, double dx, double dy, double move, String... prefixes) {
        groups.add(new Group(groupId, List.of(prefixes), delay, dx, dy, move, null));
    }

    /** Groups the nodes and pops them in with the given (at, scale) keyframes. */
    void pop(String groupId, double move, double[][] frames, String... prefixes) {
        groups.add(new Group(groupId, List.of(prefixes), 0, 0, 0, move, frames));
    }

    void clearGroups() {
        groups.clear();
    }

    // ------------------------------------------------------------------ Ore components

    void bevel(String id, double l, double t, double w, double h, String face, String hi, String lo, double z,
               double thickness, double hiH, double loH) {
        rect(id + "-bg", l, t, w, h, face, z);
        rect(id + "-hi", l, t, w, hiH, hi, z + .25);
        rect(id + "-lo", l, t + h - loH, w, loH, lo, z + .25);
        frame(id + "-frame", l, t, w, h, OUT, z + .5, thickness);
    }

    void bevel(String id, double l, double t, double w, double h, String face, String hi, String lo, double z) {
        bevel(id, l, t, w, h, face, hi, lo, z, .5, .8, 1.2);
    }

    void inset(String id, double l, double t, double w, double h, double z, String face, String edge) {
        rect(id + "-bg", l, t, w, h, face, z);
        rect(id + "-sh", l, t, w, .8, TILE.equals(face) ? "#8E8F92" : DISABLED, z + .25);
        frame(id + "-frame", l, t, w, h, edge, z + .5, .4);
    }

    void inset(String id, double l, double t, double w, double h, double z) {
        inset(id, l, t, w, h, z, HI, LO);
    }

    /** Button options, mirroring the design generator's keyword arguments. */
    static final class Button {
        double z = -1;
        double size = 3.8;
        List<String> tooltip;
        Click action;
        boolean hover = true;
        String labelColor;
        Material icon;
        double iconSize;
        double zt = 3;
        double iconDx;
        int labelUpdate;
        Mode mode = Mode.LOC;
        List<String> after = List.of();

        Button z(double value) {
            z = value;
            return this;
        }

        Button size(double value) {
            size = value;
            return this;
        }

        Button tooltip(List<String> value) {
            tooltip = value;
            return this;
        }

        Button action(Click value) {
            action = value;
            return this;
        }

        Button hover(boolean value) {
            hover = value;
            return this;
        }

        Button labelColor(String value) {
            labelColor = value;
            return this;
        }

        Button icon(Material value, double iconSize) {
            icon = value;
            this.iconSize = iconSize;
            return this;
        }

        Button zt(double value) {
            zt = value;
            return this;
        }

        Button iconDx(double value) {
            iconDx = value;
            return this;
        }

        Button labelUpdate(int value) {
            labelUpdate = value;
            return this;
        }

        Button mode(Mode value) {
            mode = value;
            return this;
        }

        Button after(List<String> value) {
            after = value;
            return this;
        }
    }

    static Button opts() {
        return new Button();
    }

    void button(String id, double l, double t, double w, double h, String label, Style style, Button o) {
        bevel(id, l, t, w, h, style.face, style.hi, style.lo, o.z, .4, .6, 1.0);
        if (o.icon != null) {
            item(id + "-icon", o.icon, l + w / 2 + o.iconDx, t + h / 2 - .3, o.iconSize > 0 ? o.iconSize : h * .62,
                o.zt - .5, false);
        }
        if (label != null) {
            double shift = o.icon != null && o.iconDx != 0 ? (o.iconSize > 0 ? o.iconSize : 0) * .45 : 0;
            text(id + "-text", o.labelColor != null ? o.labelColor : style.text, label, l + w / 2 + shift,
                t + h / 2 - .4, o.size, o.zt, 'c', o.mode, o.labelUpdate);
        }
        region(id, l, t, w, h, o.tooltip, o.action, o.after);
        if (o.hover) {
            if (style == Style.GREY || style == Style.LIGHT) {
                hover(id, id + "-bg", "color", style.face, style.hover, 2, id + "-frame", "color", OUT, G, 2);
            } else {
                hover(id, id + "-bg", "color", style.face, style.hover, 2);
            }
        }
    }

    // ------------------------------------------------------------------ text metrics (ore.py)

    private static final Map<Integer, Integer> ADV = new HashMap<>();

    static {
        for (char c : "ABCDEFGHJKLMNOPQRSUVWXYZabcdeghjmnopqrsuvwxyz0123456789$/?#%&+-=_".toCharArray()) {
            ADV.put((int) c, 6);
        }
        for (char c : "i!.,:;|'".toCharArray()) {
            ADV.put((int) c, 2);
        }
        ADV.put((int) 'l', 3);
        ADV.put((int) 'I', 4);
        ADV.put((int) 't', 4);
        ADV.put((int) ' ', 4);
        ADV.put((int) 'f', 5);
        ADV.put((int) 'k', 5);
        ADV.put((int) '<', 5);
        ADV.put((int) '>', 5);
        ADV.put((int) '×', 6);
    }

    private static final Pattern CODES = Pattern.compile("&#[0-9A-Fa-f]{6}|&[0-9a-fk-or]");

    static String strip(String text) {
        return CODES.matcher(text).replaceAll("");
    }

    private static boolean cjk(int c) {
        return c > 0x2E80;
    }

    static boolean cjk(String text) {
        return text.codePoints().anyMatch(OreScene::cjk);
    }

    /** Rendered width in design units, with the generator's glyph table. */
    static double ink(String text, double size) {
        String plain = strip(text);
        if (plain.isEmpty()) {
            return 0;
        }
        double px = plain.codePoints().map(c -> cjk(c) ? 9 : ADV.getOrDefault(c, 6)).sum();
        px -= cjk(plain.codePointBefore(plain.length())) ? 2.5 : 1;
        return px * .1106 * size;
    }

    /** Below ~3.4 (CJK) / 2.8 (Latin) glyphs smear at normal GUI resolutions. */
    private static double floorSize(String plain, double size) {
        return Math.max(size, cjk(plain) ? 3.4 : 2.8);
    }

    /** Ellipsizes text so it renders within maxWidth at the node's rendered size. */
    static String fit(String text, double size, double maxWidth) {
        if (maxWidth <= 0 || ink(text, size) <= maxWidth) {
            return text;
        }
        String result = text;
        while (!result.isEmpty() && ink(result + "…", size) > maxWidth) {
            result = result.substring(0, result.offsetByCodePoints(result.length(), -1));
        }
        return result + "…";
    }

    /** Keeps names and lore literal: ArcMenu expands {..} expressions and %..% placeholders inside text. */
    static String escape(String text) {
        return text == null ? "" : text.replace("{", "(").replace("}", ")").replace("%", "％");
    }

    private static final Set<String> BLOCKS = loadBlocks();

    private static Set<String> loadBlocks() {
        Set<String> result = new HashSet<>();
        try (InputStream input = OreScene.class.getResourceAsStream("/block-items.txt")) {
            if (input != null) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
                for (String line; (line = reader.readLine()) != null; ) {
                    line = line.trim();
                    if (!line.isEmpty() && !line.startsWith("#")) {
                        result.add(line.toLowerCase(Locale.ROOT));
                    }
                }
            }
        } catch (IOException ignored) {
            // Every item then renders as a sprite.
        }
        return result;
    }

    // ------------------------------------------------------------------ documents

    /** Turns this page into the static backdrop of a dialog: sunk 3 layers, no strips, regions, hovers or entrances. */
    void sink() {
        front.keySet().removeIf(id -> !id.startsWith("win")
            && (id.endsWith("-hi") || id.endsWith("-lo") || id.endsWith("-sh")));
        for (Object value : front.values()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> offset = (Map<String, Object>) ((Map<String, Object>) value).get("offset");
            offset.put("z", ((Number) offset.get("z")).doubleValue() - 3);
        }
        back.clear();
        handlers.clear();
        tracks.values().removeIf(track -> !"open".equals(((Map<?, ?>) track).get("trigger")));
        groups.clear();
    }

    private void applyGroups() {
        for (Group group : groups) {
            List<String> members = front.keySet().stream()
                .filter(id -> group.prefixes().stream().anyMatch(id::startsWith)).toList();
            if (members.isEmpty()) {
                continue;
            }
            Map<String, Object> children = new LinkedHashMap<>();
            members.forEach(id -> children.put(id, front.get(id)));
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : front.entrySet()) {
                if (entry.getKey().equals(members.get(0))) {
                    result.put(group.id(), map("type", "group", "children", children, "offset", map("x", 0, "y", 0)));
                }
                if (!children.containsKey(entry.getKey())) {
                    result.put(entry.getKey(), entry.getValue());
                }
            }
            front = result;
            // Half-even like the design generator (Python round), so cascades end on the same tick.
            int total = (int) Math.rint(group.delay() + group.move());
            String name = "us-" + LOCAL_ID + "-" + group.id() + "-in";
            if (group.pop() != null) {
                List<Object[]> frames = new ArrayList<>();
                for (double[] frame : group.pop()) {
                    frames.add(new Object[]{frame[0], map("x", frame[1], "y", frame[1])});
                }
                track(name, group.id(), "scale", frames, total, "once", "open", "ease-out");
            } else {
                double hold = group.delay() / total;
                Map<String, Object> start = map("x", group.dx(), "y", group.dy(), "z", 0);
                List<Object[]> frames = new ArrayList<>();
                frames.add(new Object[]{0, start});
                if (hold > 0) {
                    frames.add(new Object[]{Math.round(hold * 1000) / 1000.0, start});
                }
                frames.add(new Object[]{1, map("x", 0, "y", 0, "z", 0)});
                track(name, group.id(), "offset", frames, total, "once", "open", "ease-out");
            }
        }
        groups.clear();
    }

    String menu() {
        applyGroups();
        Map<String, Object> doc = map("schema-version", 1, "id", LOCAL_ID, "permission", "",
            "canvas", map("width", W, "height", H, "pixels-per-block", 42.7, "distance", 3.0, "depth-mode", "layered"),
            "frontend", front, "backend", back);
        return Json.write(doc);
    }

    /** Call after {@link #menu()}, which adds the entrance tracks. */
    String animations() {
        Map<String, Object> exit = map("duration", 6, "easing", "ease-in", "offset", offset(0, 0, 0),
            "scale", map("x", .02, "y", .02));
        Map<String, Object> transitions = map(
            "us-rise", map("enter", map("duration", 8, "easing", "ease-out", "offset", offset(0, -10, 0),
                "scale", map("x", .94, "y", .94)), "exit", exit),
            "us-soft", map("enter", map("duration", 5, "easing", "ease-out", "offset", offset(0, -2, 0),
                "scale", map("x", .99, "y", .99)), "exit", exit),
            "us-modal", map("enter", map("duration", 1, "easing", "linear", "offset", offset(0, 0, 0)), "exit", exit));
        Map<String, Object> binding = map("tracks", tracks.keySet().stream().sorted().toList(), "transition", transition);
        return Json.write(map("schema-version", 1, "transitions", transitions, "tracks", tracks,
            "menus", map(LOCAL_ID, binding)));
    }

    /** Frontend, backend and tracks for the layout check. */
    Map<String, Object> document() {
        applyGroups();
        return map("frontend", front, "backend", back, "tracks", tracks);
    }
}
