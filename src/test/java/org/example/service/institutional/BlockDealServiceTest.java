package org.example.service.institutional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.dto.BlockDealDTO;
import org.example.entity.BlockDeal;
import org.example.entity.Stock;
import org.example.repository.BlockDealRepository;
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
class BlockDealServiceTest {

    @Mock private BlockDealRepository blockDealRepository;
    @Mock private StockRepository stockRepository;
    @Mock private ApiRateLimiter rateLimiter;
    @Mock private NseSessionManager sessionManager;
    @Mock private ClientClassifier classifier;
    @Mock private ObjectMapper objectMapper;

    private BlockDealService blockDealService;
    private Stock testStock;
    private ObjectMapper realMapper;

    @BeforeEach
    void setUp() {
        blockDealService = new BlockDealService(
                blockDealRepository, stockRepository, rateLimiter,
                sessionManager, classifier, objectMapper);

        testStock = new Stock();
        testStock.setId(1L);
        testStock.setSymbol("RELIANCE");
        testStock.setName("Reliance Industries Ltd");

        realMapper = new ObjectMapper();
    }

    // ─── Reflection Helper ────────────────────────────────────────────────

    private Object invokePrivate(String methodName, Object... args) throws Exception {
        Method[] methods = BlockDealService.class.getDeclaredMethods();
        Method target = null;
        for (Method m : methods) {
            if (m.getName().equals(methodName) && m.getParameterCount() == args.length) {
                target = m;
                break;
            }
        }
        if (target == null) throw new NoSuchMethodException(methodName + " with " + args.length + " params");
        target.setAccessible(true);
        return target.invoke(blockDealService, args);
    }

    // ═══════════════════════════════════════════════════════════════════
    // saveBlockDealFromJson
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void saveBlockDealFromJson_validRecord_savesAndReturnsTrue() throws Exception {
        String json = "{\"BD_SYMBOL\":\"RELIANCE\",\"BD_DT_DATE\":\"15-May-2026\","
                + "\"BD_CLIENT_NAME\":\"GOLDMAN SACHS INTERNATIONAL\","
                + "\"BD_BUY_SELL\":\"BUY\",\"BD_QTY_TRD\":5000,\"BD_TP_WATP\":2850.00}";
        JsonNode node = realMapper.readTree(json);

        when(stockRepository.findBySymbol("RELIANCE")).thenReturn(Optional.of(testStock));
        when(blockDealRepository.existsByStockIdAndDealDateAndClientNameAndBuySell(
                anyLong(), any(), any(), any())).thenReturn(false);
        when(classifier.classify("GOLDMAN SACHS INTERNATIONAL")).thenReturn("FII");
        when(classifier.isInstitutional("GOLDMAN SACHS INTERNATIONAL")).thenReturn(true);

        boolean result = (boolean) invokePrivate("saveBlockDealFromJson", node);

        assertTrue(result);
        verify(blockDealRepository).save(any(BlockDeal.class));
        verify(classifier).classify("GOLDMAN SACHS INTERNATIONAL");
    }

    @Test
    void saveBlockDealFromJson_unknownSymbol_skips() throws Exception {
        String json = "{\"BD_SYMBOL\":\"UNKNOWN\",\"BD_DT_DATE\":\"15-May-2026\","
                + "\"BD_CLIENT_NAME\":\"Client\",\"BD_BUY_SELL\":\"BUY\"}";
        JsonNode node = realMapper.readTree(json);

        when(stockRepository.findBySymbol("UNKNOWN")).thenReturn(Optional.empty());

        boolean result = (boolean) invokePrivate("saveBlockDealFromJson", node);
        assertFalse(result);
        verify(blockDealRepository, never()).save(any());
    }

    @Test
    void saveBlockDealFromJson_missingRequiredFields_skips() throws Exception {
        String json = "{\"BD_SYMBOL\":\"RELIANCE\"}"; // No date/client/buySell
        JsonNode node = realMapper.readTree(json);

        when(stockRepository.findBySymbol("RELIANCE")).thenReturn(Optional.of(testStock));

        boolean result = (boolean) invokePrivate("saveBlockDealFromJson", node);
        assertFalse(result);
        verify(blockDealRepository, never()).save(any());
    }

    @Test
    void saveBlockDealFromJson_duplicate_returnsFalse() throws Exception {
        String json = "{\"BD_SYMBOL\":\"RELIANCE\",\"BD_DT_DATE\":\"15-May-2026\","
                + "\"BD_CLIENT_NAME\":\"GOLDMAN SACHS\",\"BD_BUY_SELL\":\"BUY\","
                + "\"BD_QTY_TRD\":5000,\"BD_TP_WATP\":2850.00}";
        JsonNode node = realMapper.readTree(json);

        when(stockRepository.findBySymbol("RELIANCE")).thenReturn(Optional.of(testStock));
        when(blockDealRepository.existsByStockIdAndDealDateAndClientNameAndBuySell(
                anyLong(), any(), any(), any())).thenReturn(true);

        boolean result = (boolean) invokePrivate("saveBlockDealFromJson", node);
        assertFalse(result);
        verify(blockDealRepository, never()).save(any());
    }

    @Test
    void saveBlockDealFromJson_emptySymbol_skips() throws Exception {
        String json = "{\"BD_SYMBOL\":\"\",\"BD_DT_DATE\":\"15-May-2026\"}";
        JsonNode node = realMapper.readTree(json);

        boolean result = (boolean) invokePrivate("saveBlockDealFromJson", node);
        assertFalse(result);
        verify(stockRepository, never()).findBySymbol(any());
    }

    @Test
    void saveBlockDealFromJson_classificationApplied() throws Exception {
        String json = "{\"BD_SYMBOL\":\"RELIANCE\",\"BD_DT_DATE\":\"15-May-2026\","
                + "\"BD_CLIENT_NAME\":\"LIC OF INDIA\",\"BD_BUY_SELL\":\"BUY\","
                + "\"BD_QTY_TRD\":10000,\"BD_TP_WATP\":150.00}";
        JsonNode node = realMapper.readTree(json);

        when(stockRepository.findBySymbol("RELIANCE")).thenReturn(Optional.of(testStock));
        when(blockDealRepository.existsByStockIdAndDealDateAndClientNameAndBuySell(
                anyLong(), any(), any(), any())).thenReturn(false);
        when(classifier.classify("LIC OF INDIA")).thenReturn("DII");
        when(classifier.isInstitutional("LIC OF INDIA")).thenReturn(true);

        ArgumentCaptor<BlockDeal> captor = ArgumentCaptor.forClass(BlockDeal.class);
        invokePrivate("saveBlockDealFromJson", node);

        verify(blockDealRepository).save(captor.capture());
        BlockDeal saved = captor.getValue();
        assertEquals("DII", saved.getClientCategory());
        assertTrue(saved.getIsInstitutional());
    }

    // ═══════════════════════════════════════════════════════════════════
    // saveBlockDealFromCsv
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void saveBlockDealFromCsv_validLine_savesAndReturnsTrue() throws Exception {
        String csvLine = "RELIANCE,15-May-2026,GOLDMAN SACHS,BUY,5000,2850.00,Extra remarks";

        when(stockRepository.findBySymbol("RELIANCE")).thenReturn(Optional.of(testStock));
        when(blockDealRepository.existsByStockIdAndDealDateAndClientNameAndBuySell(
                anyLong(), any(), any(), any())).thenReturn(false);
        when(classifier.classify("GOLDMAN SACHS")).thenReturn("FII");
        when(classifier.isInstitutional("GOLDMAN SACHS")).thenReturn(true);

        boolean result = (boolean) invokePrivate("saveBlockDealFromCsv", csvLine);

        assertTrue(result);
        verify(blockDealRepository).save(any(BlockDeal.class));
    }

    @Test
    void saveBlockDealFromCsv_tooFewFields_skips() throws Exception {
        String csvLine = "RELIANCE,15-May-2026,CLIENT"; // Only 3 fields

        boolean result = (boolean) invokePrivate("saveBlockDealFromCsv", csvLine);
        assertFalse(result);
        verify(stockRepository, never()).findBySymbol(any());
    }

    @Test
    void saveBlockDealFromCsv_unknownSymbol_skips() throws Exception {
        String csvLine = "UNKNOWN,15-May-2026,CLIENT,BUY,100,100.00";

        when(stockRepository.findBySymbol("UNKNOWN")).thenReturn(Optional.empty());

        boolean result = (boolean) invokePrivate("saveBlockDealFromCsv", csvLine);
        assertFalse(result);
        verify(blockDealRepository, never()).save(any());
    }

    @Test
    void saveBlockDealFromCsv_duplicate_returnsFalse() throws Exception {
        String csvLine = "RELIANCE,15-May-2026,CLIENT,BUY,100,100.00";

        when(stockRepository.findBySymbol("RELIANCE")).thenReturn(Optional.of(testStock));
        when(blockDealRepository.existsByStockIdAndDealDateAndClientNameAndBuySell(
                anyLong(), any(), any(), any())).thenReturn(true);

        boolean result = (boolean) invokePrivate("saveBlockDealFromCsv", csvLine);
        assertFalse(result);
        verify(blockDealRepository, never()).save(any());
    }

    @Test
    void saveBlockDealFromCsv_quantityAndPriceMappedCorrectly() throws Exception {
        String csvLine = "RELIANCE,15-May-2026,FII CLIENT,BUY,2000,500.75";

        when(stockRepository.findBySymbol("RELIANCE")).thenReturn(Optional.of(testStock));
        when(blockDealRepository.existsByStockIdAndDealDateAndClientNameAndBuySell(
                anyLong(), any(), any(), any())).thenReturn(false);
        when(classifier.classify("FII CLIENT")).thenReturn("FII");
        when(classifier.isInstitutional("FII CLIENT")).thenReturn(true);

        ArgumentCaptor<BlockDeal> captor = ArgumentCaptor.forClass(BlockDeal.class);
        invokePrivate("saveBlockDealFromCsv", csvLine);

        verify(blockDealRepository).save(captor.capture());
        BlockDeal saved = captor.getValue();
        assertEquals(2000L, saved.getQuantity());
        assertEquals(0, new BigDecimal("500.75").compareTo(saved.getTradePrice()));
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
        BlockDeal deal = createDeal(1L, "BUY", 100L);
        when(blockDealRepository.findByStockIdOrderByDealDateDesc(1L))
                .thenReturn(List.of(deal));

        List<BlockDealDTO> results = blockDealService.getDealsForStock(1L);

        assertEquals(1, results.size());
        assertEquals("RELIANCE", results.get(0).getSymbol());
        assertEquals("BUY", results.get(0).getBuySell());
    }

    @Test
    void getDealsInDateRange_returnsDTOs() {
        BlockDeal deal = createDeal(1L, "SELL", 200L);
        LocalDate from = LocalDate.of(2026, 5, 1);
        LocalDate to = LocalDate.of(2026, 5, 31);
        when(blockDealRepository.findByDealDateBetweenOrderByDealDateDesc(from, to))
                .thenReturn(List.of(deal));

        List<BlockDealDTO> results = blockDealService.getDealsInDateRange(from, to);

        assertEquals(1, results.size());
    }

    @Test
    void getInstitutionalBuysSince_delegatesToRepo() {
        BlockDeal deal = createDeal(1L, "BUY", 500L);
        when(blockDealRepository.findInstitutionalBuysSince(eq(1L), any(LocalDate.class)))
                .thenReturn(List.of(deal));

        List<BlockDealDTO> results = blockDealService.getInstitutionalBuysSince(1L, 7);

        assertEquals(1, results.size());
        assertEquals("BUY", results.get(0).getBuySell());
    }

    @Test
    void countInstitutionalBuysSince_delegatesToRepo() {
        when(blockDealRepository.countInstitutionalBuysSince(eq(1L), any(LocalDate.class)))
                .thenReturn(5);

        int count = blockDealService.countInstitutionalBuysSince(1L, 30);

        assertEquals(5, count);
    }

    @Test
    void hasRecentBlockDeal_positive_returnsTrue() {
        when(blockDealRepository.countInstitutionalBuysSince(eq(1L), any(LocalDate.class)))
                .thenReturn(2);

        assertTrue(blockDealService.hasRecentBlockDeal(1L, 7));
    }

    @Test
    void hasRecentBlockDeal_zero_returnsFalse() {
        when(blockDealRepository.countInstitutionalBuysSince(eq(1L), any(LocalDate.class)))
                .thenReturn(0);

        assertFalse(blockDealService.hasRecentBlockDeal(1L, 7));
    }

    // ═══════════════════════════════════════════════════════════════════
    // Missing: getTopInstitutionalDeals — intentionally absent in BlockDealService
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void getTopInstitutionalDeals_doesNotExist() {
        // Verify that BlockDealService intentionally lacks this method
        try {
            BlockDealService.class.getDeclaredMethod("getTopInstitutionalDeals", int.class);
            fail("BlockDealService should not have getTopInstitutionalDeals method");
        } catch (NoSuchMethodException e) {
            // Expected — method intentionally absent
        }
    }

    // ─── Test Helpers ────────────────────────────────────────────────────

    private BlockDeal createDeal(Long id, String buySell, long qty) {
        BlockDeal d = new BlockDeal();
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
