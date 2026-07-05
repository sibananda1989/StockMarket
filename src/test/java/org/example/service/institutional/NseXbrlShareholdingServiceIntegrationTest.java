package org.example.service.institutional;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test: parses a real NSE XBRL filing downloaded from nsearchives.
 */
class NseXbrlShareholdingServiceIntegrationTest {

    private final NseXbrlShareholdingService service;
    private static final String XBRL_FILE = "/tmp/xbrl_test.xml";

    NseXbrlShareholdingServiceIntegrationTest() {
        service = new NseXbrlShareholdingService(null, null, null, null, new ObjectMapper());
    }

    @Test
    @Disabled("Requires /tmp/xbrl_test.xml — manually download from NSE archives before running")
    void parseRealXbrl_ShouldExtractCategories() throws Exception {
        String xml = Files.readString(Paths.get(XBRL_FILE));
        assertNotNull(xml);
        assertTrue(xml.length() > 1000, "XBRL file seems too small: " + xml.length());

        Map<String, BigDecimal> categories = service.parseXbrlCategories(xml);
        assertNotNull(categories);
        assertFalse(categories.isEmpty(), "Should extract at least some categories");

        System.out.println("\n=== Real XBRL Parsing Results ===");
        categories.forEach((key, value) -> System.out.println("  " + key + " = " + value + "%"));

        // FII should be present in a valid shareholding filing
        assertTrue(categories.containsKey("FII"), "Should contain FII category");
        System.out.println("  => DII (BANK + AIF + PENSION) = "
            + sum(categories.get("BANK"), categories.get("AIF"), categories.get("PENSION")));
    }

    private BigDecimal sum(BigDecimal... values) {
        BigDecimal total = BigDecimal.ZERO;
        boolean hasValue = false;
        for (BigDecimal v : values) {
            if (v != null) {
                total = total.add(v);
                hasValue = true;
            }
        }
        return hasValue ? total : null;
    }
}
