package org.example.service.institutional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.dto.BulkDealDTO;
import org.example.entity.BulkDeal;
import org.example.entity.Stock;
import org.example.repository.BulkDealRepository;
import org.example.repository.StockRepository;
import org.example.service.ApiRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BulkDealServiceTest {

    @Mock private BulkDealRepository bulkDealRepository;
    @Mock private StockRepository stockRepository;
    @Mock private ApiRateLimiter rateLimiter;
    @Mock private NseSessionManager sessionManager;
    @Mock private ClientClassifier classifier;
    @Mock private ObjectMapper objectMapper;

    private BulkDealService bulkDealService;
    private Stock testStock;
    private ObjectMapper realMapper;

    @BeforeEach
    void setUp() {
        bulkDealService = new BulkDealService(
                bulkDealRepository, stockRepository, rateLimiter,
                sessionManager, classifier, objectMapper);

        testStock = new Stock();
        testStock.setId(1L);
        testStock.setSymbol("RELIANCE");
        testStock.setName("Reliance Industries Ltd");

        realMapper = new ObjectMapper();
    }

    // ─── Reflection Helper ────────────────────────────────────────────────

    private Object invokePrivate(String methodName, Object... args) throws Exception {
        Method[] methods = BulkDealService.class.getDeclaredMethods();
        Method target = null;
        for (Method m : methods) {
            if (m.getName().equals(methodName) && m.getParameterCount() == args.length) {
                target = m;
                break;
            }
        }
        if (target == null) throw new NoSuchMethodException(methodName + " with " + args.length + " params");
        target.setAccessible(true);
        return target.invoke(bulkDealService, args);
    }

    // ═══════════════════════════════════════════════════════════════════
    // saveBulkDealFromJson
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void saveBulkDealFromJson_validRecord_savesAndReturnsTrue() throws Exception {
        String json = "{\"BD_SYMBOL\":\"RELIANCE\",\"BD_DT_DATE\":\"15-May-2026\","
                + "\"BD_CLIENT_NAME\":\"GOLDMAN SACHS INTERNATIONAL\","
                + "\"BD_BUY_SELL\":\"BUY\",\"BD_QTY_TRD\":5000,\"BD_TP_WATP\":2850.00}";
        JsonNode node = realMapper.readTree(json);

        when(stockRepository.findBySymbol("RELIANCE")).thenReturn(Optional.of(testStock));
        when(bulkDealRepository.existsByStockIdAndDealDateAndClientNameAndBuySell(
                anyLong(), any(), any(), any())).thenReturn(false);
        when(classifier.classify("GOLDMAN SACHS INTERNATIONAL")).thenReturn("FII");
        when(classifier.isInstitutional("GOLDMAN SACHS INTERNATIONAL")).thenReturn(true);

        boolean result = (boolean) invokePrivate("saveBulkDealFromJson", node);

        assertTrue(result);
        verify(bulkDealRepository).save(any(BulkDeal.class));
        verify(classifier).classify("GOLDMAN SACHS INTERNATIONAL");
    }

    @Test
    void saveBulkDealFromJson_unknownSymbol_skips() throws Exception {
        String json = "{\"BD_SYMBOL\":\"UNKNOWN\",\"BD_DT_DATE\":\"15-May-2026\","
                + "\"BD_CLIENT_NAME\":\"Client\",\"BD_BUY_SELL\":\"BUY\"}";
        JsonNode node = realMapper.readTree(json);

        when(stockRepository.findBySymbol("UNKNOWN")).thenReturn(Optional.empty());

        boolean result = (boolean) invokePrivate("saveBulkDealFromJson", node);
        assertFalse(result);
        verify(bulkDealRepository, never()).save(any());
    }

    @Test
    void saveBulkDealFromJson_missingRequiredFields_skips() throws Exception {
        String json = "{\"BD_SYMBOL\":\"RELIANCE\"}"; // No date/client/buySell
        JsonNode node = realMapper.readTree(json);

        when(stockRepository.findBySymbol("RELIANCE")).thenReturn(Optional.of(testStock));

        boolean result = (boolean) invokePrivate("saveBulkDealFromJson", node);
        assertFalse(result);
        verify(bulkDealRepository, never()).save(any());
    }

    @Test
    void saveBulkDealFromJson_duplicate_returnsFalse() throws Exception {
        String json = "{\"BD_SYMBOL\":\"RELIANCE\",\"BD_DT_DATE\":\"15-May-2026\","
                + "\"BD_CLIENT_NAME\":\"GOLDMAN SACHS\",\"BD_BUY_SELL\":\"BUY\","
                + "\"BD_QTY_TRD\":5000,\"BD_TP_WATP\":2850.00}";
        JsonNode node = realMapper.readTree(json);

        when(stockRepository.findBySymbol("RELIANCE")).thenReturn(Optional.of(testStock));
        when(bulkDealRepository.existsByStockIdAndDealDateAndClientNameAndBuySell(
                anyLong(), any(), any(), any())).thenReturn(true);

        boolean result = (boolean) invokePrivate("saveBulkDealFromJson", node);
        assertFalse(result);
        verify(bulkDealRepository, never()).save(any());
    }

    @Test
    void saveBulkDealFromJson_emptySymbol_skips() throws Exception {
        String json = "{\"BD_SYMBOL\":\"\",\"BD_DT_DATE\":\"15-May-2026\"}";
        JsonNode node = realMapper.readTree(json);

        boolean result = (boolean) invokePrivate("saveBulkDealFromJson", node);
        assertFalse(result);
        verify(stockRepository, never()).findBySymbol(any());
    }

    @Test
    void saveBulkDealFromJson_clientClassifiedAndInstitutionalFlagSet() throws Exception {
        String json = "{\"BD_SYMBOL\":\"RELIANCE\",\"BD_DT_DATE\":\"15-May-2026\","
                + "\"BD_CLIENT_NAME\":\"SBI MUTUAL FUND\",\"BD_BUY_SELL\":\"BUY\","
                + "\"BD_QTY_TRD\":10000,\"BD_TP_WATP\":150.00}";
        JsonNode node = realMapper.readTree(json);

        when(stockRepository.findBySymbol("RELIANCE")).thenReturn(Optional.of(testStock));
        when(bulkDealRepository.existsByStockIdAndDealDateAndClientNameAndBuySell(
                anyLong(), any(), any(), any())).thenReturn(false);
        when(classifier.classify("SBI MUTUAL FUND")).thenReturn("MF");
        when(classifier.isInstitutional("SBI MUTUAL FUND")).thenReturn(true);

        ArgumentCaptor<BulkDeal> captor = ArgumentCaptor.forClass(BulkDeal.class);
        invokePrivate("saveBulkDealFromJson", node);

        verify(bulkDealRepository).save(captor.capture());
        BulkDeal saved = captor.getValue();
        assertEquals("MF", saved.getClientCategory());
        assertTrue(saved.getIsInstitutional());
    }

    // ═══════════════════════════════════════════════════════════════════
    // saveBulkDealFromCsv
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void saveBulkDealFromCsv_validLine_savesAndReturnsTrue() throws Exception {
        String csvLine = "RELIANCE,15-May-2026,GOLDMAN SACHS,BUY,5000,2850.00,Extra remarks";

        when(stockRepository.findBySymbol("RELIANCE")).thenReturn(Optional.of(testStock));
        when(bulkDealRepository.existsByStockIdAndDealDateAndClientNameAndBuySell(
                anyLong(), any(), any(), any())).thenReturn(false);
        when(classifier.classify("GOLDMAN SACHS")).thenReturn("FII");
        when(classifier.isInstitutional("GOLDMAN SACHS")).thenReturn(true);

        boolean result = (boolean) invokePrivate("saveBulkDealFromCsv", csvLine);

        assertTrue(result);
        verify(bulkDealRepository).save(any(BulkDeal.class));
    }

    @Test
    void saveBulkDealFromCsv_tooFewFields_skips() throws Exception {
        String csvLine = "RELIANCE,15-May-2026,CLIENT"; // Only 3 fields

        boolean result = (boolean) invokePrivate("saveBulkDealFromCsv", csvLine);
        assertFalse(result);
        verify(stockRepository, never()).findBySymbol(any());
    }

    @Test
    void saveBulkDealFromCsv_unknownSymbol_skips() throws Exception {
        String csvLine = "UNKNOWN,15-May-2026,CLIENT,BUY,100,100.00";

        when(stockRepository.findBySymbol("UNKNOWN")).thenReturn(Optional.empty());

        boolean result = (boolean) invokePrivate("saveBulkDealFromCsv", csvLine);
        assertFalse(result);
        verify(bulkDealRepository, never()).save(any());
    }

    @Test
    void saveBulkDealFromCsv_duplicate_returnsFalse() throws Exception {
        String csvLine = "RELIANCE,15-May-2026,CLIENT,BUY,100,100.00";

        when(stockRepository.findBySymbol("RELIANCE")).thenReturn(Optional.of(testStock));
        when(bulkDealRepository.existsByStockIdAndDealDateAndClientNameAndBuySell(
                anyLong(), any(), any(), any())).thenReturn(true);

        boolean result = (boolean) invokePrivate("saveBulkDealFromCsv", csvLine);
        assertFalse(result);
        verify(bulkDealRepository, never()).save(any());
    }

    @Test
    void saveBulkDealFromCsv_remapsTradePriceCorrectly() throws Exception {
        String csvLine = "RELIANCE,15-May-2026,FII CLIENT,BUY,1000,250.50";

        when(stockRepository.findBySymbol("RELIANCE")).thenReturn(Optional.of(testStock));
        when(bulkDealRepository.existsByStockIdAndDealDateAndClientNameAndBuySell(
                anyLong(), any(), any(), any())).thenReturn(false);
        when(classifier.classify("FII CLIENT")).thenReturn("FII");
        when(classifier.isInstitutional("FII CLIENT")).thenReturn(true);

        ArgumentCaptor<BulkDeal> captor = ArgumentCaptor.forClass(BulkDeal.class);
        invokePrivate("saveBulkDealFromCsv", csvLine);

        verify(bulkDealRepository).save(captor.capture());
        BulkDeal saved = captor.getValue();
        assertEquals(1000L, saved.getQuantity());
        assertEquals(0, new BigDecimal("250.50").compareTo(saved.getTradePrice()));
    }

    // ═══════════════════════════════════════════════════════════════════
    // parseNseDate
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void parseNseDate_ddMMMyyyyFormat_parsed() throws Exception {
        LocalDate result = (LocalDate) invokePrivate("parseNseDate", "15-May-2026");
        assertEquals(LocalDate.of(2026, 5, 15), result);
    }

    @Test
    void parseNseDate_yyyyMMddFormat_parsed() throws Exception {
        LocalDate result = (LocalDate) invokePrivate("parseNseDate", "2026-05-15");
        assertEquals(LocalDate.of(2026, 5, 15), result);
    }

    @Test
    void parseNseDate_invalidFormat_returnsNull() throws Exception {
        LocalDate result = (LocalDate) invokePrivate("parseNseDate", "not-a-date");
        assertNull(result);
    }

    @Test
    void parseNseDate_nullInput_returnsNull() throws Exception {
        LocalDate result = (LocalDate) invokePrivate("parseNseDate", (String) null);
        assertNull(result);
    }

    @Test
    void parseNseDate_blankInput_returnsNull() throws Exception {
        LocalDate result = (LocalDate) invokePrivate("parseNseDate", "  ");
        assertNull(result);
    }

    // ═══════════════════════════════════════════════════════════════════
    // Query Methods
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void getDealsForStock_returnsDTOs() {
        BulkDeal deal = createDeal(1L, "BUY", 100L);
        when(bulkDealRepository.findByStockIdOrderByDealDateDesc(1L))
                .thenReturn(List.of(deal));

        List<BulkDealDTO> results = bulkDealService.getDealsForStock(1L);

        assertEquals(1, results.size());
        assertEquals("RELIANCE", results.get(0).getSymbol());
        assertEquals("BUY", results.get(0).getBuySell());
    }

    @Test
    void getDealsInDateRange_returnsDTOs() {
        BulkDeal deal = createDeal(1L, "SELL", 200L);
        LocalDate from = LocalDate.of(2026, 5, 1);
        LocalDate to = LocalDate.of(2026, 5, 31);
        when(bulkDealRepository.findByDealDateBetweenOrderByDealDateDesc(from, to))
                .thenReturn(List.of(deal));

        List<BulkDealDTO> results = bulkDealService.getDealsInDateRange(from, to);

        assertEquals(1, results.size());
    }

    @Test
    void getInstitutionalBuysSince_delegatesToRepo() {
        BulkDeal deal = createDeal(1L, "BUY", 500L);
        when(bulkDealRepository.findInstitutionalBuysSince(eq(1L), any(LocalDate.class)))
                .thenReturn(List.of(deal));

        List<BulkDealDTO> results = bulkDealService.getInstitutionalBuysSince(1L, 7);

        assertEquals(1, results.size());
        assertEquals("BUY", results.get(0).getBuySell());
    }

    @Test
    void countInstitutionalBuysSince_delegatesToRepo() {
        when(bulkDealRepository.countInstitutionalBuysSince(eq(1L), any(LocalDate.class)))
                .thenReturn(3);

        int count = bulkDealService.countInstitutionalBuysSince(1L, 7);

        assertEquals(3, count);
    }

    @Test
    void getTopInstitutionalDeals_delegatesToRepo() {
        BulkDeal deal = createDeal(1L, "BUY", 1000L);
        when(bulkDealRepository.findTopInstitutionalDealsByValue(any(LocalDate.class)))
                .thenReturn(List.of(deal));

        List<BulkDealDTO> results = bulkDealService.getTopInstitutionalDeals(30);

        assertEquals(1, results.size());
    }

    @Test
    void hasRecentBulkDeal_positive_returnsTrue() {
        when(bulkDealRepository.countInstitutionalBuysSince(eq(1L), any(LocalDate.class)))
                .thenReturn(1);

        assertTrue(bulkDealService.hasRecentBulkDeal(1L, 7));
    }

    @Test
    void hasRecentBulkDeal_zero_returnsFalse() {
        when(bulkDealRepository.countInstitutionalBuysSince(eq(1L), any(LocalDate.class)))
                .thenReturn(0);

        assertFalse(bulkDealService.hasRecentBulkDeal(1L, 7));
    }

    // ─── Test Helpers ────────────────────────────────────────────────────

    private BulkDeal createDeal(Long id, String buySell, long qty) {
        BulkDeal d = new BulkDeal();
        d.setId(id);
        d.setStock(testStock);
        d.setDealDate(LocalDate.of(2026, 5, 15));
        d.setClientName("Test Client");
        d.setBuySell(buySell);
        d.setQuantity(qty);
        d.setTradePrice(new BigDecimal("100.00"));
        d.setClientCategory("FII");
        d.setIsInstitutional(true);
        return d;
    }
}
