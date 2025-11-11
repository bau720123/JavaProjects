package com.example;

import java.time.LocalDate;

public record Candle(
    LocalDate date, // 日期
    double open, // 開盤價
    double high, // 最高價
    double low, // 最低價
    double close, // 收盤價
    long volume,  // 總量
    double change  // 漲跌
) {}