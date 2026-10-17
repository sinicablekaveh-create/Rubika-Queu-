package ir.sinicable.rubikaqueue;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Parses the review-only result packet exported by HooshikaDev. */
final class HooshikaHandoff {
    static final String MIME = "application/vnd.hooshika.rubika-results+json";
    static final String SCHEMA = "hooshika.rubika.handoff.v1";
    static final int MAX_BYTES = 65_536;
    static final int MAX_ITEMS = 40;

    static final class Item {
        final String name;
        final String kind;
        final String phone;
        final String url;
        final String source;

        Item(String name, String kind, String phone, String url, String source) {
            this.name = name;
            this.kind = kind;
            this.phone = phone;
            this.url = url;
            this.source = source;
        }

        boolean canQueueWithConsent() {
            return "business_contact".equals(kind) && !phone.isEmpty();
        }

        boolean isGroup() {
            return "public_group".equals(kind) || "public_channel".equals(kind);
        }
    }

    static final class Packet {
        final List<Item> items;
        final int duplicatesRemoved;

        Packet(List<Item> items, int duplicatesRemoved) {
            this.items = items;
            this.duplicatesRemoved = duplicatesRemoved;
        }
    }

    static Packet parse(String payload) throws JSONException {
        if (payload == null || payload.length() > MAX_BYTES
                || payload.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES)
            throw new IllegalArgumentException("Handoff packet is missing or too large");
        JSONObject root = new JSONObject(payload);
        if (!SCHEMA.equals(root.optString("schema")))
            throw new IllegalArgumentException("Unsupported Hooshika handoff schema");
        JSONArray rows = root.optJSONArray("items");
        if (rows == null || rows.length() < 1 || rows.length() > MAX_ITEMS)
            throw new IllegalArgumentException("Handoff must contain 1–40 items");

        Map<String, Item> unique = new LinkedHashMap<>();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null) throw new IllegalArgumentException("Invalid handoff item");
            String kind = row.optString("kind");
            if (!("business_contact".equals(kind) || "public_group".equals(kind)
                    || "public_channel".equals(kind)))
                throw new IllegalArgumentException("Unknown handoff item type");

            String rawPhone = row.optString("phone", "").trim();
            String phone = rawPhone.isEmpty() ? "" : Phones.normalize(rawPhone);
            if (!rawPhone.isEmpty() && phone.isEmpty())
                throw new IllegalArgumentException("Invalid business phone number");
            String url = row.optString("url", "").trim();
            if (url.length() > 800) throw new IllegalArgumentException("Handoff URL is too long");
            if ("business_contact".equals(kind)) {
                if (phone.isEmpty() && !validHttps(url))
                    throw new IllegalArgumentException("Business result has no public contact destination");
                if (!url.isEmpty() && !validHttps(url))
                    throw new IllegalArgumentException("Business source must use public HTTPS");
            } else {
                url = publicRubikaUrl(url);
                if (url.isEmpty()) throw new IllegalArgumentException("Group result is not a public Rubika link");
            }

            String name = clean(row.optString("name", ""), 140);
            String source = clean(row.optString("source", ""), 80);
            Item item = new Item(name, kind, phone, url, source);
            String key = item.isGroup() ? "g:" + url
                    : "b:" + (phone.isEmpty() ? url : phone);
            unique.putIfAbsent(key, item);
        }
        return new Packet(new ArrayList<>(unique.values()), rows.length() - unique.size());
    }

    private static boolean validHttps(String value) {
        try {
            URI uri = new URI(value);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                    && uri.getUserInfo() == null && (uri.getPort() == -1 || uri.getPort() == 443);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String publicRubikaUrl(String value) {
        try {
            URI uri = new URI(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || !"rubika.ir".equalsIgnoreCase(uri.getHost()) || uri.getUserInfo() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443)) return "";
            String path = uri.getRawPath();
            if (path == null || !path.matches("/(?:[a-zA-Z0-9_\\-]{3,64}|(?:joinc|joing)/[a-zA-Z0-9_\\-]{3,96})/?"))
                return "";
            String key = path.replaceAll("^/|/$", "").toLowerCase(Locale.ROOT);
            if ("install-android".equals(key) || "install-ios".equals(key) || "rules".equals(key)
                    || "faq".equals(key) || "blog".equals(key) || "about".equals(key)
                    || "jobs".equals(key) || "support".equals(key) || "logo".equals(key)
                    || "contests".equals(key) || "botapi".equals(key) || "home".equals(key)
                    || "joinc".equals(key) || "joing".equals(key)) return "";
            return "https://rubika.ir" + (path.endsWith("/") ? path.substring(0, path.length() - 1) : path);
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String clean(String value, int limit) {
        String safe = value == null ? "" : value.replaceAll("[\\p{Cntrl}]", " ").trim();
        return safe.substring(0, Math.min(limit, safe.length()));
    }

    private HooshikaHandoff() { }
}
