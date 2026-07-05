package org.example.service.institutional;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.entity.InstitutionalHolding;
import org.example.entity.Stock;
import org.example.repository.InstitutionalHoldingRepository;
import org.example.repository.StockRepository;
import org.example.service.ApiRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class NseXbrlShareholdingServiceTest {

    @Mock
    private InstitutionalHoldingRepository holdingRepository;

    @Mock
    private StockRepository stockRepository;

    @Mock
    private ApiRateLimiter rateLimiter;

    @Mock
    private NseSessionManager sessionManager;

    private NseXbrlShareholdingService service;

    @BeforeEach
    void setUp() {
        service = new NseXbrlShareholdingService(holdingRepository, stockRepository, rateLimiter, sessionManager, new ObjectMapper());
    }

    // ─── mapCategory Tests ───────────────────────────────────────────────

    @Test
    void mapCategory_FiiPatterns_ReturnsFII() {
        assertEquals("FII", service.mapCategory("ForeignPortfolioInvestorMember"));
        assertEquals("FII", service.mapCategory("fpi_member"));
        assertEquals("FII", service.mapCategory("FOREIGN_PORTFOLIO_INVESTOR"));
        assertEquals("FII", service.mapCategory("FII_MEMBER"));
        assertEquals("FII", service.mapCategory("ForeignPortfolioInvestor"));
    }

    @Test
    void mapCategory_MutualFundPatterns_ReturnsMF() {
        assertEquals("MF", service.mapCategory("MutualFundsMember"));
        assertEquals("MF", service.mapCategory("mutual_fund_member"));
        assertEquals("MF", service.mapCategory("Mutual Fund Member"));
        assertEquals("MF", service.mapCategory("MUTUALFUND"));
    }

    @Test
    void mapCategory_InsurancePatterns_ReturnsINSURANCE() {
        assertEquals("INSURANCE", service.mapCategory("InsuranceCompaniesMember"));
        assertEquals("INSURANCE", service.mapCategory("LifeInsuranceMember"));
        assertEquals("INSURANCE", service.mapCategory("GeneralInsuranceMember"));
    }

    @Test
    void mapCategory_BankPatterns_ReturnsBANK() {
        assertEquals("BANK", service.mapCategory("BanksMember"));
        assertEquals("BANK", service.mapCategory("ScheduledCommercialBankMember"));
        assertEquals("BANK", service.mapCategory("CommercialBankMember"));
    }

    @Test
    void mapCategory_AifPatterns_ReturnsAIF() {
        assertEquals("AIF", service.mapCategory("AlternateInvestmentFundsMember"));
        assertEquals("AIF", service.mapCategory("AIFMember"));
        assertEquals("AIF", service.mapCategory("SpecialistVCFMember"));
        assertEquals("AIF", service.mapCategory("VentureCapitalFundsMember"));
        assertEquals("AIF", service.mapCategory("CategoryIIIAIFMember"));
    }

    @Test
    void mapCategory_PensionPatterns_ReturnsPENSION() {
        assertEquals("PENSION", service.mapCategory("ProvidentFundsOrPensionFundsMember"));
        assertEquals("PENSION", service.mapCategory("PensionFundsMember"));
        assertEquals("PENSION", service.mapCategory("GratuityFundsMember"));
        assertEquals("PENSION", service.mapCategory("SuperannuationFundsMember"));
        assertEquals("PENSION", service.mapCategory("RetirementBenefitFundsMember"));
    }

    @Test
    void mapCategory_GovernmentPatterns_ReturnsGOVERNMENT() {
        assertEquals("GOVERNMENT", service.mapCategory("GovernmentMember"));
        assertEquals("GOVERNMENT", service.mapCategory("CentralGovernmentMember"));
        assertEquals("GOVERNMENT", service.mapCategory("StateGovernmentMember"));
    }

    @Test
    void mapCategory_PromoterPatterns_ReturnsPROMOTER() {
        assertEquals("PROMOTER", service.mapCategory("PromoterAndPromoterGroupMember"));
        assertEquals("PROMOTER", service.mapCategory("PromoterMember"));
    }

    @Test
    void mapCategory_EmployeePatterns_ReturnsEMPLOYEE() {
        assertEquals("EMPLOYEE", service.mapCategory("EmployeeTrustsMember"));
        assertEquals("EMPLOYEE", service.mapCategory("ESOPMember"));
        assertEquals("EMPLOYEE", service.mapCategory("ESOS_Member"));
        assertEquals("EMPLOYEE", service.mapCategory("EmployeesStockOptionPlanMember"));
    }

    @Test
    void mapCategory_OtherUnmapped_ReturnsNull() {
        assertNull(service.mapCategory("SomeRandomCategory"));
        assertNull(service.mapCategory("UnrelatedValue"));
        assertNull(service.mapCategory(""));
    }

    @Test
    void mapCategory_NullInput_ReturnsNull() {
        assertNull(service.mapCategory(null));
    }

    // ─── JSON Parsing Tests ──────────────────────────────────────────────

    @Test
    void parseMasterJson_SingleRecord_ReturnsParsedRecord() {
        String json = "[{\"date\":\"31-MAR-2026\",\"xbrl\":\"https://nse.com/xbrl1.xml\"," +
                "\"pr_and_prgrp\":\"50.00\",\"public_val\":\"50.00\",\"employeeTrusts\":\"0\"}]";
        List<NseXbrlShareholdingService.XbrlRecord> records = service.parseMasterJson(json);
        assertEquals(1, records.size());
        assertEquals(LocalDate.of(2026, 3, 31), records.get(0).quarterEnd);
        assertEquals("https://nse.com/xbrl1.xml", records.get(0).xbrlUrl);
        assertEquals(new BigDecimal("50.00"), records.get(0).promoterPct);
        assertEquals(new BigDecimal("50.00"), records.get(0).publicPct);
    }

    @Test
    void parseMasterJson_MultipleRecords_ReturnsAllSortedDesc() {
        String json = "[" +
                "{\"date\":\"31-MAR-2026\",\"xbrl\":\"https://nse.com/q4.xml\",\"pr_and_prgrp\":\"50\",\"public_val\":\"50\"}," +
                "{\"date\":\"31-DEC-2025\",\"xbrl\":\"https://nse.com/q3.xml\",\"pr_and_prgrp\":\"51\",\"public_val\":\"49\"}," +
                "{\"date\":\"30-SEP-2025\",\"xbrl\":\"https://nse.com/q2.xml\",\"pr_and_prgrp\":\"52\",\"public_val\":\"48\"}" +
                "]";
        List<NseXbrlShareholdingService.XbrlRecord> records = service.parseMasterJson(json);
        assertEquals(3, records.size());
        // Should be sorted descending by date
        assertEquals(LocalDate.of(2026, 3, 31), records.get(0).quarterEnd);
        assertEquals(LocalDate.of(2025, 12, 31), records.get(1).quarterEnd);
        assertEquals(LocalDate.of(2025, 9, 30), records.get(2).quarterEnd);
    }

    @Test
    void parseMasterJson_NoXbrlUrl_ExcludesRecord() {
        String json = "[{\"date\":\"31-MAR-2026\",\"pr_and_prgrp\":\"50\",\"public_val\":\"50\"}]";
        List<NseXbrlShareholdingService.XbrlRecord> records = service.parseMasterJson(json);
        assertTrue(records.isEmpty());
    }

    @Test
    void parseMasterJson_EmptyJson_ReturnsEmptyList() {
        assertTrue(service.parseMasterJson("[]").isEmpty());
        assertTrue(service.parseMasterJson("{}").isEmpty());
        assertTrue(service.parseMasterJson("").isEmpty());
        assertTrue(service.parseMasterJson("not json").isEmpty());
    }

    @Test
    void parseMasterJson_NullValues_HandlesGracefully() {
        String json = "[{\"date\":\"31-MAR-2026\",\"xbrl\":\"https://nse.com/xbrl.xml\"," +
                "\"pr_and_prgrp\":null,\"public_val\":null,\"employeeTrusts\":null}]";
        List<NseXbrlShareholdingService.XbrlRecord> records = service.parseMasterJson(json);
        assertEquals(1, records.size());
        assertNull(records.get(0).promoterPct);
        assertNull(records.get(0).publicPct);
        assertNull(records.get(0).employeeTrusts);
    }

    // ─── XBRL Category Parsing Tests ─────────────────────────────────────

    @Test
    void parseXbrlCategories_FullFiling_ReturnsAllCategories() throws Exception {
        String xml = buildRealisticSampleXbrl();
        Map<String, BigDecimal> result = service.parseXbrlCategories(xml);

        assertNotNull(result);
        assertEquals(new BigDecimal("15.23"), result.get("FII"));
        assertEquals(new BigDecimal("7.89"), result.get("MF"));
        assertEquals(new BigDecimal("4.56"), result.get("INSURANCE"));
        assertEquals(new BigDecimal("2.34"), result.get("BANK"));
        assertEquals(new BigDecimal("1.23"), result.get("AIF"));
        assertEquals(new BigDecimal("0.89"), result.get("PENSION"));
    }

    @Test
    void parseXbrlCategories_PromoterWithRetail_IncludesAll() throws Exception {
        String xml = buildSampleWithPromoterAndRetail();
        Map<String, BigDecimal> result = service.parseXbrlCategories(xml);

        assertNotNull(result);
        assertTrue(result.containsKey("PROMOTER"));
        assertTrue(result.containsKey("RETAIL"));
    }

    @Test
    void parseXbrlCategories_EmptyDocument_ReturnsEmptyMap() throws Exception {
        String xml = "<?xml version=\"1.0\"?><xbrli:xbrl xmlns:xbrli=\"http://www.xbrl.org/2003/instance\"></xbrli:xbrl>";
        assertTrue(service.parseXbrlCategories(xml).isEmpty());
    }

    @Test
    void parseXbrlCategories_NoCategoryAxis_ReturnsEmptyMap() throws Exception {
        String xml = "<?xml version=\"1.0\"?>" +
                "<xbrli:xbrl xmlns:xbrli=\"http://www.xbrl.org/2003/instance\">" +
                "<xbrli:context id=\"ctx1\">" +
                "<xbrli:entity><xbrli:identifier>TEST</xbrli:identifier></xbrli:entity>" +
                "<xbrli:period><xbrli:instant>2026-03-31</xbrli:instant></xbrli:period>" +
                "</xbrli:context>" +
                "</xbrli:xbrl>";
        assertTrue(service.parseXbrlCategories(xml).isEmpty());
    }

    @Test
    void parseXbrlCategories_PrefixedMembers_StripsPrefix() throws Exception {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<xbrli:xbrl xmlns:xbrli=\"http://www.xbrl.org/2003/instance\"\n" +
                " xmlns:xbrldi=\"http://www.xbrl.org/2006/xbrldi\"\n" +
                " xmlns:shp=\"http://www.bseindia.com/xbrl/shp/2025-10-31/in-bse-shp\">\n" +
                "<xbrli:context id=\"c1\">\n" +
                " <xbrli:entity><xbrli:identifier scheme=\"http://www.bseindia.com\">500325</xbrli:identifier></xbrli:entity>\n" +
                " <xbrli:period><xbrli:instant>2026-03-31</xbrli:instant></xbrli:period>\n" +
                " <xbrli:scenario>\n" +
                "  <xbrldi:explicitMember dimension=\"shp:CategoryOfShareholdersAxis\">shp:ForeignPortfolioInvestorMember</xbrldi:explicitMember>\n" +
                " </xbrli:scenario>\n" +
                "</xbrli:context>\n" +
                "<shp:PercentageOfShareholding contextRef=\"c1\" decimals=\"2\">12.50</shp:PercentageOfShareholding>\n" +
                "</xbrli:xbrl>";
        Map<String, BigDecimal> result = service.parseXbrlCategories(xml);
        assertEquals(new BigDecimal("12.50"), result.get("FII"));
    }

    // ─── Helper: sum() tests ─────────────────────────────────────────────

    @Test
    void sum_AllValues_ReturnsTotal() {
        // Access via reflection or test the logic directly
        assertEquals(new BigDecimal("4.46"),
                invokeSum(new BigDecimal("2.34"), new BigDecimal("1.23"), new BigDecimal("0.89")));
    }

    @Test
    void sum_WithNulls_IgnoresNull() {
        assertEquals(new BigDecimal("3.23"),
                invokeSum(new BigDecimal("2.34"), null, new BigDecimal("0.89")));
    }

    @Test
    void sum_AllNulls_ReturnsNull() {
        assertNull(invokeSum(null, null, null));
    }

    @Test
    void sum_SingleValue_ReturnsThatValue() {
        assertEquals(0, new BigDecimal("5").compareTo(invokeSum(new BigDecimal("5"), null, null)));
    }

    // ─── Private helper using reflection ─────────────────────────────────

    private BigDecimal invokeSum(BigDecimal... values) {
        try {
            var method = NseXbrlShareholdingService.class.getDeclaredMethod("sum", BigDecimal[].class);
            method.setAccessible(true);
            return (BigDecimal) method.invoke(service, (Object) values);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ─── Sample XBRL builders ────────────────────────────────────────────

    private String buildRealisticSampleXbrl() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<xbrli:xbrl xmlns:xbrli=\"http://www.xbrl.org/2003/instance\"\n" +
                " xmlns:xbrldi=\"http://www.xbrl.org/2006/xbrldi\"\n" +
                " xmlns:in-bse-shp=\"http://www.bseindia.com/xbrl/shp/2025-10-31/in-bse-shp\">\n" +
                // FII
                "<xbrli:context id=\"ctx_fii\"><xbrli:entity><xbrli:identifier scheme=\"http://www.bseindia.com\">500325</xbrli:identifier></xbrli:entity><xbrli:period><xbrli:instant>2026-03-31</xbrli:instant></xbrli:period><xbrli:scenario><xbrldi:explicitMember dimension=\"in-bse-shp:CategoryOfShareholdersAxis\">in-bse-shp:ForeignPortfolioInvestorMember</xbrldi:explicitMember></xbrli:scenario></xbrli:context>\n" +
                "<in-bse-shp:PercentageOfShareholding contextRef=\"ctx_fii\" decimals=\"2\">15.23</in-bse-shp:PercentageOfShareholding>\n" +
                // MF
                "<xbrli:context id=\"ctx_mf\"><xbrli:entity><xbrli:identifier scheme=\"http://www.bseindia.com\">500325</xbrli:identifier></xbrli:entity><xbrli:period><xbrli:instant>2026-03-31</xbrli:instant></xbrli:period><xbrli:scenario><xbrldi:explicitMember dimension=\"in-bse-shp:CategoryOfShareholdersAxis\">in-bse-shp:MutualFundsMember</xbrldi:explicitMember></xbrli:scenario></xbrli:context>\n" +
                "<in-bse-shp:PercentageOfShareholding contextRef=\"ctx_mf\" decimals=\"2\">7.89</in-bse-shp:PercentageOfShareholding>\n" +
                // Insurance
                "<xbrli:context id=\"ctx_ins\"><xbrli:entity><xbrli:identifier scheme=\"http://www.bseindia.com\">500325</xbrli:identifier></xbrli:entity><xbrli:period><xbrli:instant>2026-03-31</xbrli:instant></xbrli:period><xbrli:scenario><xbrldi:explicitMember dimension=\"in-bse-shp:CategoryOfShareholdersAxis\">in-bse-shp:InsuranceCompaniesMember</xbrldi:explicitMember></xbrli:scenario></xbrli:context>\n" +
                "<in-bse-shp:PercentageOfShareholding contextRef=\"ctx_ins\" decimals=\"2\">4.56</in-bse-shp:PercentageOfShareholding>\n" +
                // Bank (DII component)
                "<xbrli:context id=\"ctx_bank\"><xbrli:entity><xbrli:identifier scheme=\"http://www.bseindia.com\">500325</xbrli:identifier></xbrli:entity><xbrli:period><xbrli:instant>2026-03-31</xbrli:instant></xbrli:period><xbrli:scenario><xbrldi:explicitMember dimension=\"in-bse-shp:CategoryOfShareholdersAxis\">in-bse-shp:BanksMember</xbrldi:explicitMember></xbrli:scenario></xbrli:context>\n" +
                "<in-bse-shp:PercentageOfShareholding contextRef=\"ctx_bank\" decimals=\"2\">2.34</in-bse-shp:PercentageOfShareholding>\n" +
                // AIF (DII component)
                "<xbrli:context id=\"ctx_aif\"><xbrli:entity><xbrli:identifier scheme=\"http://www.bseindia.com\">500325</xbrli:identifier></xbrli:entity><xbrli:period><xbrli:instant>2026-03-31</xbrli:instant></xbrli:period><xbrli:scenario><xbrldi:explicitMember dimension=\"in-bse-shp:CategoryOfShareholdersAxis\">in-bse-shp:AlternateInvestmentFundsMember</xbrldi:explicitMember></xbrli:scenario></xbrli:context>\n" +
                "<in-bse-shp:PercentageOfShareholding contextRef=\"ctx_aif\" decimals=\"2\">1.23</in-bse-shp:PercentageOfShareholding>\n" +
                // Pension (DII component)
                "<xbrli:context id=\"ctx_pension\"><xbrli:entity><xbrli:identifier scheme=\"http://www.bseindia.com\">500325</xbrli:identifier></xbrli:entity><xbrli:period><xbrli:instant>2026-03-31</xbrli:instant></xbrli:period><xbrli:scenario><xbrldi:explicitMember dimension=\"in-bse-shp:CategoryOfShareholdersAxis\">in-bse-shp:ProvidentFundsOrPensionFundsMember</xbrldi:explicitMember></xbrli:scenario></xbrli:context>\n" +
                "<in-bse-shp:PercentageOfShareholding contextRef=\"ctx_pension\" decimals=\"2\">0.89</in-bse-shp:PercentageOfShareholding>\n" +
                "</xbrli:xbrl>";
    }

    private String buildSampleWithPromoterAndRetail() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<xbrli:xbrl xmlns:xbrli=\"http://www.xbrl.org/2003/instance\"\n" +
                " xmlns:xbrldi=\"http://www.xbrl.org/2006/xbrldi\"\n" +
                " xmlns:in-bse-shp=\"http://www.bseindia.com/xbrl/shp/2025-10-31/in-bse-shp\">\n" +
                "<xbrli:context id=\"c_promo\"><xbrli:entity><xbrli:identifier>500325</xbrli:identifier></xbrli:entity><xbrli:period><xbrli:instant>2026-03-31</xbrli:instant></xbrli:period><xbrli:scenario><xbrldi:explicitMember dimension=\"in-bse-shp:CategoryOfShareholdersAxis\">in-bse-shp:PromoterAndPromoterGroupMember</xbrldi:explicitMember></xbrli:scenario></xbrli:context>\n" +
                "<in-bse-shp:PercentageOfShareholding contextRef=\"c_promo\" decimals=\"2\">50.00</in-bse-shp:PercentageOfShareholding>\n" +
                "<xbrli:context id=\"c_retail\"><xbrli:entity><xbrli:identifier>500325</xbrli:identifier></xbrli:entity><xbrli:period><xbrli:instant>2026-03-31</xbrli:instant></xbrli:period><xbrli:scenario><xbrldi:explicitMember dimension=\"in-bse-shp:CategoryOfShareholdersAxis\">in-bse-shp:IndividualsOrHinduUndividedFamilyMember</xbrldi:explicitMember></xbrli:scenario></xbrli:context>\n" +
                "<in-bse-shp:PercentageOfShareholding contextRef=\"c_retail\" decimals=\"2\">25.00</in-bse-shp:PercentageOfShareholding>\n" +
                "</xbrli:xbrl>";
    }
}
