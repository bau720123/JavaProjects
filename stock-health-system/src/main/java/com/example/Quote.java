package com.example;

import java.util.List;

public record Quote(
    String symbol, // 股票代號
    String name, // 股票名稱
    double previousClose, // 開盤價
    double openPrice, // 開盤價
    double highPrice, // 最高價
    double lowPrice, // 最低價
    double closePrice, // 收盤價或現價
    double avgPrice, // 均價
    double change, // 漲跌
    double changePercent, // 漲跌幅度
    List<BidAsk> bids, // 委買價
    List<BidAsk> asks,  // 委賣價
    long tradeVolume, // 累計成交量
    long tradeVolumeAtBid, // 累計成交量
    long tradeVolumeAtAsk, // 累計成交量
    long transaction // 累計成交量
) {}