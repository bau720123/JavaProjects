package com.example;

public record BidAsk(
    double price, // 委託價
    long size // 張數
) {}