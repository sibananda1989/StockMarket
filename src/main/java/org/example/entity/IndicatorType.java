package org.example.entity;

public enum IndicatorType {
    // Directional indicators (indicate buy/sell direction)
    RSI(true),
    SMA_20(true),
    SMA_50(true),
    SMA_200(true),
    EMA_20(true),
    MACD_LINE(true),
    MACD_SIGNAL(true),
    STOCH_K(true),
    STOCH_D(true),
    WILLIAMS_R(true),
    CCI(true),
    STOCH_RSI(true),
    PLUS_DI(true),
    MINUS_DI(true),
    ULTIMATE_OSC(true),
    ROC_12(true),
    OBV(true),
    VWAP(true),
    TENKAN_SEN(true),
    KIJUN_SEN(true),
    SENKOU_SPAN_A(true),
    SENKOU_SPAN_B(true),
    CHIKOU_SPAN(true),

    // Non-directional indicators (measure magnitude, not direction)
    BOLLINGER_UPPER(false),
    BOLLINGER_LOWER(false),
    ATR(false),
    ADX(false);

    private final boolean directional;

    IndicatorType(boolean directional) {
        this.directional = directional;
    }

    public boolean isDirectional() {
        return directional;
    }
}
