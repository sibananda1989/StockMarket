package org.example.service.institutional;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
@Slf4j
public class ClientClassifier {

    // ---- FII / FPI Keywords ----
    private static final Pattern[] FII_PATTERNS = {
        Pattern.compile("FOREIGN\\s+(PORTFOLIO|INSTITUTIONAL|VENTURE|FUND)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("FII|FPI", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(OVERSEAS|OFFSHORE)\\s+(FUND|INVESTMENT|PORTFOLIO|VENTURE)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("EMERGING\\s+MARKETS?\\s+(FUND|EQUITY|GROWTH)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("MAURITIUS|SINGAPORE|CAYMAN|BERMUDA", Pattern.CASE_INSENSITIVE),
        Pattern.compile("GLOBAL\\s+[A-Z].+FUND", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bGOVERNMENT\\s+OF\\s+SINGAPORE\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("ABERDEEN|MORGAN\\s+STANLEY|GOLDMAN\\s+SACHS|JPMORGAN|NOMURA|CREDIT\\s+SUISSE", Pattern.CASE_INSENSITIVE),
        Pattern.compile("SOC.GENERALE|BANK\\s+OF\\s+AMERICA|CITIGROUP|UBS\\s+|BARCLAYS|DEUTSCHE\\s+BANK", Pattern.CASE_INSENSITIVE),
    };

    // ---- Mutual Fund Keywords ----
    private static final Pattern[] MF_PATTERNS = {
        Pattern.compile("MUTUAL\\s+FUND", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bSBI\\s+(MUTUAL|MAGNUM|EQUITY|SMALL|MIDCAP|LARGE|FOCUSED|BLUE.?CHIP|BALANCED|DEBT|FUND)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bHDFC\\s+(MUTUAL|ASSET|BALANCED|EQUITY|GROWTH|PRUDENTIAL|FUND|SMALL|MID.?CAP|LARGE|TRUSTEE)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bICICI\\s+(PRUDENTIAL|MUTUAL|EQUITY|VALUE|FUND|BALANCED|FOCUSED)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bNIPPON\\s+(LIFE|INDIA|MUTUAL|EQUITY|FUND)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bKOTAK\\s+(MAHINDRA|MUTUAL|EQUITY|FUND|EMERGING|FOCUSED)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bAXIS\\s+(MUTUAL|EQUITY|FUND|SMALL|MID.?CAP|LARGE|FOCUSED|GROWTH|BLUE.?CHIP)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bDSP\\s+(MUTUAL|EQUITY|FUND|SMALL|MID.?CAP|FOCUSED|TAX|VALUE|GROWTH)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bFRANKLIN\\s+(TEMPLETON|MUTUAL|FUND|EQUITY)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bADITYA\\s+BIRLA\\s+(SUN\\s+LIFE|MUTUAL|EQUITY|FUND|ADVANTAGE)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bUTI\\s+(MUTUAL|EQUITY|FUND|MASTER|TRANSFER|VALUE|MID.?CAP|LEADERSHIP|BALANCED|FOCUSED)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bMIRAE\\s+ASSET\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bTATA\\s+(MUTUAL|EQUITY|FUND|SMALL|MID.?CAP|LARGE|FOCUSED|BALANCED|BOND)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bQUANT\\s+(MUTUAL|FUND|EQUITY|SMALL|MID.?CAP|ACTIVE|TAX|VALUE)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bBANDHAN\\s+(MUTUAL|EQUITY|FUND|SMALL|MID.?CAP|CORE)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bINVESCO\\s+(MUTUAL|EQUITY|FUND|GROWTH|INFRASTRUCTURE)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bEDELWEISS\\s+(MUTUAL|EQUITY|FUND|BALANCED|SMALL|MID.?CAP)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bPGIM\\s+(INDIA|MUTUAL|EQUITY|FUND)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bMOTILAL\\s+OSWAL\\s+(MUTUAL|FUND|EQUITY|MID.?CAP|LARGE|SMALL|MULTI.?CAP)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\BAUM\\s+(MUTUAL|FUND|EQUITY|SMALL|MID.?CAP|LARGE|FOCUSED)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bWHITEOAK\\s+CAPITAL\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bIDFC\\s+(MUTUAL|FUND|EQUITY|BOND|SMALL|MID.?CAP)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("A/C\\s+(.*\\s+)?(MUTUAL|EQUITY|FUND|BALANCED|GROWTH|SMALL|MID|LARGE|TAX|FOCUSED|BOND|DEBT|VALUE)", Pattern.CASE_INSENSITIVE),
    };

    // ---- DII (Domestic Institutional, non-MF) ----
    private static final Pattern[] DII_PATTERNS = {
        Pattern.compile("\\bLIC\\s+(OF\\s+)?(INDIA\\s+)?(MUTUAL|EQUITY|FUND|PENSION|PROFIT|LIFE)?", Pattern.CASE_INSENSITIVE),
        Pattern.compile("LIFE\\s+INSURANCE\\s+CORP", Pattern.CASE_INSENSITIVE),
        Pattern.compile("INSURANCE\\s+(COMPANY|CORPORATION|CORP|FUND)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("GENERAL\\s+INSURANCE\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("NEW\\s+INDIA\\s+(ASSURANCE|INSURANCE)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("UNITED\\s+INDIA\\s+INSURANCE", Pattern.CASE_INSENSITIVE),
        Pattern.compile("ORIENTAL\\s+INSURANCE", Pattern.CASE_INSENSITIVE),
        Pattern.compile("NATIONAL\\s+INSURANCE", Pattern.CASE_INSENSITIVE),
        Pattern.compile("HDFC\\s+(LIFE|ERGO|STANDARD)\\s+(LIFE|INSURANCE)?", Pattern.CASE_INSENSITIVE),
        Pattern.compile("ICICI\\s+PRUDENTIAL\\s+LIFE", Pattern.CASE_INSENSITIVE),
        Pattern.compile("PENSION\\s+FUND", Pattern.CASE_INSENSITIVE),
        Pattern.compile("PROVIDENT\\s+FUND", Pattern.CASE_INSENSITIVE),
        Pattern.compile("EMPLOYEES?\\s+PROVIDENT", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bSBI\\s+LIFE\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("BANK\\s+(OF\\s+)?(INDIA|MAHARASHTRA|BARODA|BORODA)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bPUNJAB\\s+NATIONAL\\s+BANK\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bCANARA\\s+BANK\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bHDFC\\s+BANK\\s+LTD\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(ICICI|AXIS|KOTAK|YES|IDBI|INDUSIND|FEDERAL)\\s+BANK\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("DELIVERY\\s+VERSUS\\s+PAYMENT", Pattern.CASE_INSENSITIVE),
    };

    // ---- Promoter Keywords ----
    private static final Pattern[] PROMOTER_PATTERNS = {
        Pattern.compile("\\bPROMOTER", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bDIRECTOR\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("BODY\\s+CORPORATE\\s+(AS\\s+)?PROMOTER", Pattern.CASE_INSENSITIVE),
    };

    // ---- Retail / Individual Keywords ----
    private static final Pattern[] RETAIL_PATTERNS = {
        Pattern.compile("\\bRETAIL\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bINDIVIDUAL\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bHUF\\b", Pattern.CASE_INSENSITIVE),
    };

    /**
     * Classifies a client name into a category.
     *
     * @param clientName The client name from bulk/block deal
     * @return Category string: "FII", "MF", "DII", "PROMOTER", "RETAIL", "UNKNOWN"
     */
    public String classify(String clientName) {
        if (clientName == null || clientName.isBlank()) return "UNKNOWN";

        String name = clientName.trim();

        // Order matters: check most specific first
        if (matchesAny(name, MF_PATTERNS)) return "MF";
        if (matchesAny(name, FII_PATTERNS)) return "FII";
        if (matchesAny(name, DII_PATTERNS)) return "DII";
        if (matchesAny(name, PROMOTER_PATTERNS)) return "PROMOTER";
        if (matchesAny(name, RETAIL_PATTERNS)) return "RETAIL";

        // Additional heuristic: if name is short or contains individual-like patterns
        if (name.length() < 15 && (name.contains(" ") || name.contains("."))) {
            return "RETAIL";
        }

        // Long names with "PRIVATE LIMITED", "LIMITED", "LLP" are often corporate
        if (name.contains("PRIVATE LIMITED") || name.contains("PVT LTD") || name.contains("LLP")) {
            return "UNKNOWN";  // Could be corporate holdings
        }

        return "UNKNOWN";
    }

    /**
     * Returns true if the client name represents an institutional investor.
     */
    public boolean isInstitutional(String clientName) {
        String category = classify(clientName);
        return "FII".equals(category) || "MF".equals(category) || "DII".equals(category);
    }

    private boolean matchesAny(String name, Pattern[] patterns) {
        for (Pattern p : patterns) {
            if (p.matcher(name).find()) return true;
        }
        return false;
    }
}
