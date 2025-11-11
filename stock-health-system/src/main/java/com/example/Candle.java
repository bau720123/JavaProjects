package com.example;

import java.time.LocalDate;

public record Candle(
    LocalDate date,
    double open,
    double high,
    double low,
    double close,
    long volume,  // 股數
    double change  // [調整]：漲跌額 (從 Fugle API "change" 欄位，放在 volume 後，匹配 POSTMAN 回應順序)
) {}