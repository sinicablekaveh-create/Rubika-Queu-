package ir.sinicable.rubikaqueue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HooshikaHandoffTest {
    @Test public void parsesAndDeduplicatesBusinessPhoneWithoutInferringConsent() throws Exception {
        JSONArray items = new JSONArray()
                .put(new JSONObject().put("name", "فروشگاه برق").put("kind", "business_contact")
                        .put("phone", "۰۹۱۲۱۲۳۴۵۶۷").put("url", "https://example.org/store"))
                .put(new JSONObject().put("name", "تکراری").put("kind", "business_contact")
                        .put("phone", "+989121234567"));

        HooshikaHandoff.Packet packet = HooshikaHandoff.parse(packet(items));

        assertEquals(1, packet.items.size());
        assertEquals(1, packet.duplicatesRemoved);
        assertTrue(packet.items.get(0).canQueueWithConsent());
        assertFalse(packet.items.get(0).isGroup());
    }

    @Test public void acceptsOnlyPublicRubikaGroupLinksAndKeepsThemReviewOnly() throws Exception {
        JSONArray items = new JSONArray().put(new JSONObject()
                .put("name", "گروه برق").put("kind", "public_group")
                .put("url", "https://rubika.ir/joing/Invite_123?ref=share"));

        HooshikaHandoff.Item item = HooshikaHandoff.parse(packet(items)).items.get(0);

        assertEquals("https://rubika.ir/joing/Invite_123", item.url);
        assertTrue(item.isGroup());
        assertFalse(item.canQueueWithConsent());
    }

    @Test public void rejectsWrongSchemaAndNonRubikaGroupLinks() throws Exception {
        try {
            HooshikaHandoff.parse(new JSONObject().put("schema", "unknown").put("items", new JSONArray()).toString());
            throw new AssertionError("wrong schema accepted");
        } catch (IllegalArgumentException expected) { }

        JSONArray items = new JSONArray().put(new JSONObject()
                .put("name", "گروه").put("kind", "public_group")
                .put("url", "https://example.org/joing/token"));
        try {
            HooshikaHandoff.parse(packet(items));
            throw new AssertionError("non-Rubika destination accepted");
        } catch (IllegalArgumentException expected) { }
    }

    @Test public void rejectsInvalidPhoneAndOversizedPacket() throws Exception {
        JSONArray items = new JSONArray().put(new JSONObject()
                .put("name", "تماس").put("kind", "business_contact").put("phone", "12345"));
        try {
            HooshikaHandoff.parse(packet(items));
            throw new AssertionError("invalid phone accepted");
        } catch (IllegalArgumentException expected) { }

        try {
            HooshikaHandoff.parse(new String(new char[HooshikaHandoff.MAX_BYTES + 1]));
            throw new AssertionError("oversized packet accepted");
        } catch (IllegalArgumentException expected) { }
    }

    private static String packet(JSONArray items) throws Exception {
        return new JSONObject().put("schema", HooshikaHandoff.SCHEMA).put("items", items).toString();
    }
}
