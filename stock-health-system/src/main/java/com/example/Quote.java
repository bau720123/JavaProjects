package com.example;

public record Quote(
    String symbol,
    String name,
    double openPrice,  // 開盤價
    double highPrice,  // 最高價
    double lowPrice,   // 最低價
    double closePrice, // 收盤/最後成交價
    double avgPrice,   // 均價
    long tradeVolume   // 成交量 (股數)
) {}