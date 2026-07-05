package org.example.service.institutional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for ClientClassifier — 30+ pattern tests covering
 * FII, MF, DII, Promoter, Retail categories plus heuristic branches.
 */
class ClientClassifierTest {

    private ClientClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new ClientClassifier();
    }

    // ─── FII Patterns ───────────────────────────────────────────────────

    @Test
    void classify_ForeignPortfolioInvestor_ReturnsFII() {
        assertEquals("FII", classifier.classify("FOREIGN PORTFOLIO INVESTOR"));
    }

    @Test
    void classify_ForeignInstitutionalVentureFund_ReturnsFII() {
        assertEquals("FII", classifier.classify("FOREIGN VENTURE CAPITAL FUND"));
    }

    @Test
    void classify_FiiAbbreviation_ReturnsFII() {
        assertEquals("FII", classifier.classify("FII CLIENT ACCOUNT"));
    }

    @Test
    void classify_FpiAbbreviation_ReturnsFII() {
        assertEquals("FII", classifier.classify("FPI"));
    }

    @Test
    void classify_OverseasFund_ReturnsFII() {
        assertEquals("FII", classifier.classify("OVERSEAS INVESTMENT FUND LTD"));
    }

    @Test
    void classify_MauritiusEntity_ReturnsFII() {
        assertEquals("FII", classifier.classify("MAURITIUS GLOBAL FUND"));
    }

    @Test
    void classify_SingaporeEntity_ReturnsFII() {
        assertEquals("FII", classifier.classify("SINGAPORE HOLDING PTE LTD"));
    }

    @Test
    void classify_GlobalFund_ReturnsFII() {
        assertEquals("FII", classifier.classify("GLOBAL GROWTH FUND"));
    }

    @Test
    void classify_GovernmentOfSingapore_ReturnsFII() {
        assertEquals("FII", classifier.classify("GOVERNMENT OF SINGAPORE"));
    }

    @Test
    void classify_MorganStanley_ReturnsFII() {
        assertEquals("FII", classifier.classify("MORGAN STANLEY ASIA PTE LTD"));
    }

    @Test
    void classify_GoldmanSachs_ReturnsFII() {
        assertEquals("FII", classifier.classify("GOLDMAN SACHS INTERNATIONAL"));
    }

    // ─── MF Patterns ────────────────────────────────────────────────────

    @Test
    void classify_MutualFundDirect_ReturnsMF() {
        assertEquals("MF", classifier.classify("MUTUAL FUND SCHEME A"));
    }

    @Test
    void classify_SbiMutualFund_ReturnsMF() {
        assertEquals("MF", classifier.classify("SBI MUTUAL FUND - EQUITY"));
    }

    @Test
    void classify_HdfcMutualFund_ReturnsMF() {
        assertEquals("MF", classifier.classify("HDFC ASSET MANAGEMENT CO LTD"));
    }

    @Test
    void classify_IciciPrudentialMutualFund_ReturnsMF() {
        assertEquals("MF", classifier.classify("ICICI PRUDENTIAL MUTUAL FUND"));
    }

    @Test
    void classify_NipponMutualFund_ReturnsMF() {
        assertEquals("MF", classifier.classify("NIPPON LIFE INDIA TRUSTEE LTD"));
    }

    @Test
    void classify_KotakMahindraMutualFund_ReturnsMF() {
        assertEquals("MF", classifier.classify("KOTAK MAHINDRA MUTUAL FUND"));
    }

    @Test
    void classify_AxisMutualFund_ReturnsMF() {
        assertEquals("MF", classifier.classify("AXIS MUTUAL FUND TRUSTEE LTD"));
    }

    @Test
    void classify_DspMutualFund_ReturnsMF() {
        assertEquals("MF", classifier.classify("DSP MUTUAL FUND SCHEME"));
    }

    @Test
    void classify_AcPrefixMutualFund_ReturnsMF() {
        assertEquals("MF", classifier.classify("A/C UTI EQUITY FUND"));
    }

    @Test
    void classify_AcPrefixMidcapFund_ReturnsMF() {
        assertEquals("MF", classifier.classify("A/C HDFC MID CAP OPPORTUNITIES FUND"));
    }

    // ─── DII Patterns ───────────────────────────────────────────────────

    @Test
    void classify_Lic_ReturnsDII() {
        assertEquals("DII", classifier.classify("LIC OF INDIA"));
    }

    @Test
    void classify_LifeInsuranceCorp_ReturnsDII() {
        assertEquals("DII", classifier.classify("LIFE INSURANCE CORPORATION OF INDIA"));
    }

    @Test
    void classify_InsuranceCompany_ReturnsDII() {
        assertEquals("DII", classifier.classify("INSURANCE COMPANY LTD"));
    }

    @Test
    void classify_GeneralInsurance_ReturnsDII() {
        assertEquals("DII", classifier.classify("GENERAL INSURANCE CORPORATION"));
    }

    @Test
    void classify_NewIndiaAssurance_ReturnsDII() {
        assertEquals("DII", classifier.classify("NEW INDIA ASSURANCE CO LTD"));
    }

    @Test
    void classify_BankOfIndia_ReturnsDII() {
        assertEquals("DII", classifier.classify("BANK OF INDIA"));
    }

    @Test
    void classify_BankOfBaroda_ReturnsDII() {
        assertEquals("DII", classifier.classify("BANK OF BARODA"));
    }

    @Test
    void classify_BankOfBorodaTypo_ReturnsDII() {
        // Verifies "BORODA" typo in pattern still matches
        assertEquals("DII", classifier.classify("BANK OF BORODA"));
    }

    @Test
    void classify_PunjabNationalBank_ReturnsDII() {
        assertEquals("DII", classifier.classify("PUNJAB NATIONAL BANK"));
    }

    @Test
    void classify_CanaraBank_ReturnsDII() {
        assertEquals("DII", classifier.classify("CANARA BANK"));
    }

    @Test
    void classify_HdfcBankLtd_ReturnsDII() {
        assertEquals("DII", classifier.classify("HDFC BANK LTD"));
    }

    @Test
    void classify_IciciBank_ReturnsDII() {
        assertEquals("DII", classifier.classify("ICICI BANK"));
    }

    @Test
    void classify_DeliveryVersusPayment_ReturnsDII() {
        assertEquals("DII", classifier.classify("DELIVERY VERSUS PAYMENT"));
    }

    @Test
    void classify_PensionFund_ReturnsDII() {
        assertEquals("DII", classifier.classify("PENSION FUND REGULATORY AUTHORITY"));
    }

    @Test
    void classify_ProvidentFund_ReturnsDII() {
        assertEquals("DII", classifier.classify("PROVIDENT FUND TRUSTEE"));
    }

    @Test
    void classify_EmployeesProvidentFund_ReturnsDII() {
        assertEquals("DII", classifier.classify("EMPLOYEES PROVIDENT FUND ORGANISATION"));
    }

    @Test
    void classify_SbiLife_ReturnsDII() {
        assertEquals("DII", classifier.classify("SBI LIFE INSURANCE CO LTD"));
    }

    // ─── Promoter Patterns ──────────────────────────────────────────────

    @Test
    void classify_PromoterKeyword_ReturnsPROMOTER() {
        assertEquals("PROMOTER", classifier.classify("PROMOTER GROUP ENTITY"));
    }

    @Test
    void classify_DirectorKeyword_ReturnsPROMOTER() {
        assertEquals("PROMOTER", classifier.classify("DIRECTOR SHAREHOLDING"));
    }

    @Test
    void classify_BodyCorporateAsPromoter_ReturnsPROMOTER() {
        assertEquals("PROMOTER", classifier.classify("BODY CORPORATE AS PROMOTER"));
    }

    // ─── Retail Patterns ────────────────────────────────────────────────

    @Test
    void classify_RetailKeyword_ReturnsRETAIL() {
        assertEquals("RETAIL", classifier.classify("RETAIL SHAREHOLDER"));
    }

    @Test
    void classify_IndividualKeyword_ReturnsRETAIL() {
        assertEquals("RETAIL", classifier.classify("INDIVIDUAL INVESTOR"));
    }

    @Test
    void classify_Huf_ReturnsRETAIL() {
        assertEquals("RETAIL", classifier.classify("HUF ACCOUNT"));
    }

    // ─── Heuristic Branches ─────────────────────────────────────────────

    @Test
    void classify_ShortNameWithDot_ReturnsRETAIL() {
        assertEquals("RETAIL", classifier.classify("R K GUPTA"));
    }

    @Test
    void classify_ShortNameWithoutSpace_ReturnsUNKNOWN() {
        assertEquals("UNKNOWN", classifier.classify("ABCDEFGHIJK"));
    }

    @Test
    void classify_PrivateLimited_ReturnsUNKNOWN() {
        assertEquals("UNKNOWN", classifier.classify("ABC CORPORATION PRIVATE LIMITED"));
    }

    @Test
    void classify_PvtLtd_ReturnsUNKNOWN() {
        assertEquals("UNKNOWN", classifier.classify("DEF ENTERPRISES PVT LTD"));
    }

    @Test
    void classify_Llp_ReturnsUNKNOWN() {
        assertEquals("UNKNOWN", classifier.classify("GHI SERVICES LLP"));
    }

    @Test
    void classify_NullInput_ReturnsUNKNOWN() {
        assertEquals("UNKNOWN", classifier.classify(null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "\t", "\n"})
    void classify_BlankInput_ReturnsUNKNOWN(String blank) {
        assertEquals("UNKNOWN", classifier.classify(blank));
    }

    // ─── Order Sensitivity ──────────────────────────────────────────────

    @Test
    void classify_MfPatternWinsOverFii_WhenBothMatch() {
        // A name like "SBI MUTUAL FUND" matches both MF (SBI MUTUAL) and FII (FUND)
        // patterns. MF is checked first, so it should win.
        assertEquals("MF", classifier.classify("SBI MUTUAL FUND"));
    }

    @Test
    void classify_BankOfAmerica_ReturnsFII_NotDII() {
        // "BANK OF AMERICA" is in the FII set (major IB names) even though
        // "BANK" patterns exist in DII. FII is checked before DII.
        assertEquals("FII", classifier.classify("BANK OF AMERICA"));
    }

    // ─── isInstitutional ────────────────────────────────────────────────

    @Test
    void isInstitutional_Fii_ReturnsTrue() {
        assertTrue(classifier.isInstitutional("FOREIGN PORTFOLIO INVESTOR"));
    }

    @Test
    void isInstitutional_Mf_ReturnsTrue() {
        assertTrue(classifier.isInstitutional("SBI MUTUAL FUND"));
    }

    @Test
    void isInstitutional_Dii_ReturnsTrue() {
        assertTrue(classifier.isInstitutional("LIC OF INDIA"));
    }

    @Test
    void isInstitutional_Promoter_ReturnsFalse() {
        assertFalse(classifier.isInstitutional("PROMOTER GROUP"));
    }

    @Test
    void isInstitutional_Retail_ReturnsFalse() {
        assertFalse(classifier.isInstitutional("RETAIL INVESTOR"));
    }

    @Test
    void isInstitutional_Unknown_ReturnsFalse() {
        assertFalse(classifier.isInstitutional("RANDOM COMPANY PRIVATE LIMITED"));
    }
}
