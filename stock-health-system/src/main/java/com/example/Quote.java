package com.example;

import java.util.List;

public record Quote(
    String symbol,
    String name,
    double previousClose, // 開盤價
    double openPrice, // 開盤價
    double highPrice, // 最高價
    double lowPrice, // 最低價
    double closePrice, // 收盤價或現價
    double avgPrice, // 均價
    long tradeVolume, // 成交量
    double change, // 漲跌
    double changePercent, // 幅度
    List<BidAsk> bids, // 委買價
    List<BidAsk> asks  // 委賣價
) {}