package com.example;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 重大市場事件行事曆（硬編碼版 + MoneyDJ 動態抓取混合）
 * 只要今天符合任何一項，就會在主畫面文字區最上方顯示紅字提醒
 */
public final class MarketEventCalendar {

    private static final HttpClient httpClient = HttpClient.newHttpClient();
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final DateTimeFormatter MONEYDJ_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    // 聯準會 FOMC 利率決策日
    private static final List<LocalDate> FOMC_DATES = List.of(
            // 2025
            LocalDate.of(2025, 1, 29),
            LocalDate.of(2025, 3, 19),
            LocalDate.of(2025, 5, 7),
            LocalDate.of(2025, 6, 18),
            LocalDate.of(2025, 7, 30),
            LocalDate.of(2025, 9, 17),
            LocalDate.of(2025, 10, 29),
            LocalDate.of(2025, 12, 10),
            // 2026
            LocalDate.of(2026, 1, 28),
            LocalDate.of(2026, 3, 18),
            LocalDate.of(2026, 4, 29),
            LocalDate.of(2026, 6, 17),
            LocalDate.of(2026, 7, 29),
            LocalDate.of(2026, 9, 16),
            LocalDate.of(2026, 10, 28),
            LocalDate.of(2026, 12, 9),
            // 2027（已知的第一場）
            LocalDate.of(2027, 1, 27)
    );

    // FTSE Russell 指數重組生效日
    private static final List<LocalDate> FTSE_REBALANCE_DATES = List.of(
            LocalDate.of(2025, 6, 27),
            LocalDate.of(2026, 6, 26),
            LocalDate.of(2026, 11, 13),
            LocalDate.of(2027, 6, 25),
            LocalDate.of(2027, 11, 12)
    );

    // MSCI 季度/半年度調整生效日
    private static final List<LocalDate> MSCI_REVIEW_DATES = List.of(
            // 2025
            LocalDate.of(2025, 2, 24),
            LocalDate.of(2025, 5, 30),
            LocalDate.of(2025, 8, 25),
            LocalDate.of(2025, 11, 24),
            // 2026
            LocalDate.of(2026, 3, 2),
            LocalDate.of(2026, 5, 29),
            LocalDate.of(2026, 8, 31),
            LocalDate.of(2026, 12, 1),
            // 2027
            LocalDate.of(2027, 3, 1),
            LocalDate.of(2027, 5, 31),
            LocalDate.of(2027, 8, 30),
            LocalDate.of(2027, 11, 29)
    );

    // 台指期／選擇權／ETF 結算日（每月第三個星期三）
    public static boolean isTaiwanFuturesSettlementDay(LocalDate date) {
        LocalDate firstOfMonth = date.withDayOfMonth(1);
        LocalDate firstWednesday = firstOfMonth;
        while (firstWednesday.getDayOfWeek().getValue() != 3) { // 3 = Wednesday
            firstWednesday = firstWednesday.plusDays(1);
        }
        LocalDate thirdWednesday = firstWednesday.plusDays(14);
        return date.equals(thirdWednesday);
    }

    // 美股四巫日：3、6、9、12 月第三個星期五
    public static boolean isUSQuadrupleWitchingDay(LocalDate date) {
        int month = date.getMonthValue();
        if (month != 3 && month != 6 && month != 9 && month != 12) {
            return false;
        }
        LocalDate firstOfMonth = date.withDayOfMonth(1);
        LocalDate firstFriday = firstOfMonth;
        while (firstFriday.getDayOfWeek().getValue() != 5) { // 5 = Friday
            firstFriday = firstFriday.plusDays(1);
        }
        LocalDate thirdFriday = firstFriday.plusDays(14);
        return date.equals(thirdFriday);
    }

    // 美股重要財報提醒（提醒「前一個台股交易日」）
    private static final List<LocalDate> US_EARNINGS_DATES = List.of(
            // 2025 年（已確認官方公告 + 歷史規律推算）
            LocalDate.of(2025, 1, 22),
            LocalDate.of(2025, 1, 28),
            LocalDate.of(2025, 1, 29),
            LocalDate.of(2025, 1, 30),
            LocalDate.of(2025, 2, 4),
            LocalDate.of(2025, 2, 19),
            LocalDate.of(2025, 4, 23),
            LocalDate.of(2025, 4, 29),
            LocalDate.of(2025, 5, 1),
            LocalDate.of(2025, 5, 14),
            LocalDate.of(2025, 5, 20),
            LocalDate.of(2025, 7, 29),
            LocalDate.of(2025, 7, 30),
            LocalDate.of(2025, 7, 31),
            LocalDate.of(2025, 8, 27),
            LocalDate.of(2025, 10, 28),
            LocalDate.of(2025, 10, 30),
            LocalDate.of(2025, 11, 19),
            LocalDate.of(2026, 2, 18),
            LocalDate.of(2026, 5, 20),
            LocalDate.of(2026, 8, 26),
            LocalDate.of(2026, 11, 18)
    );

    public static boolean shouldRemindUSEarnings(LocalDate taiwanDate) {
        LocalDate tomorrow = taiwanDate.plusDays(1);
        if (US_EARNINGS_DATES.contains(tomorrow)) return true;
        LocalDate dayAfterTomorrow = taiwanDate.plusDays(2);
        if ((tomorrow.getDayOfWeek().getValue() == 6 || tomorrow.getDayOfWeek().getValue() == 7) &&
            US_EARNINGS_DATES.contains(dayAfterTomorrow)) return true;
        return false;
    }

    // MoneyDJ 動態經濟事件
    private static List<JsonNode> cachedEvents = null; // 整個應用只抓一次

    private static List<JsonNode> getMoneyDJEvents() {
        if (cachedEvents != null) {
            return cachedEvents;
        }

        int currentYear = Year.now().getValue();
        String url = String.format("https://www.moneydj.com/us/rest/eventlist?from=%d-01-01&to=%d-12-31", currentYear, currentYear);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode root = mapper.readTree(response.body());
                cachedEvents = mapper.convertValue(root, new TypeReference<List<JsonNode>>() {});
                return cachedEvents;
            }
        } catch (Exception e) {
            System.err.println("MoneyDJ 經濟事件 API 抓取失敗，將停用動態提醒: " + e.getMessage());
        }

        cachedEvents = List.of();
        return cachedEvents;
    }

    public static boolean isTodayUSNonFarmPayroll() {
        LocalDate today = LocalDate.now();
        return getMoneyDJEvents().stream().anyMatch(event -> {
            String details = event.path("details").asText();
            if (!details.contains("美國非農業就業人數變化")) return false;
            String dateStr = event.path("start_date").asText().split(" ")[0];
            LocalDate eventDate = LocalDate.parse(dateStr, MONEYDJ_DATE_FORMATTER);
            return eventDate.equals(today);
        });
    }

    public static boolean isTodayUSConsumerConfidence() {
        LocalDate today = LocalDate.now();
        return getMoneyDJEvents().stream().anyMatch(event -> {
            String details = event.path("details").asText();
            if (!details.contains("美國消費者信心指數")) return false;
            String dateStr = event.path("start_date").asText().split(" ")[0];
            LocalDate eventDate = LocalDate.parse(dateStr, MONEYDJ_DATE_FORMATTER);
            return eventDate.equals(today);
        });
    }

    /**
     * 回傳今天是否有重大事件，有則回傳提醒文字，沒有則回傳 null
     */
    public static String getTodayEventMessage() {
        LocalDate today = LocalDate.now();
        StringBuilder sb = new StringBuilder("今日重大事件提醒\n\n");
        boolean hasEvent = false;

        if (FOMC_DATES.contains(today)) {
            sb.append("今天是美國聯準會 FOMC 利率決策日！（美股尾盤易大波動）\n");
            hasEvent = true;
        }
        if (FTSE_REBALANCE_DATES.contains(today)) {
            sb.append("今天是 FTSE Russell 指數重組生效日！（全球被動資金調整）\n");
            hasEvent = true;
        }
        if (MSCI_REVIEW_DATES.contains(today)) {
            sb.append("今天是 MSCI 季度/半年度權重調整生效日！（台股權重變動）\n");
            hasEvent = true;
        }
        if (isTaiwanFuturesSettlementDay(today)) {
            sb.append("今天是台指期／選擇權結算日（三巫日）！尾盤容易劇烈震盪\n");
            hasEvent = true;
        }
        if (isUSQuadrupleWitchingDay(today)) {
            sb.append("今天是美股四巫日（Quadruple Witching）！成交量爆衝，隔週一台股易受影響\n");
            hasEvent = true;
        }
        if (shouldRemindUSEarnings(today)) {
            sb.append("今晚（美股盤後）有重要美股財報！（NVDA、AAPL、META等）\n");
            sb.append("明天台股開盤可能大幅波動，請特別注意！\n");
            hasEvent = true;
        }
        if (isTodayUSNonFarmPayroll()) {
            sb.append("今晚 21:30 美國非農就業數據 (NFP) 即將公布！\n");
            sb.append("美元、美股、台指夜盤將劇烈波動，隔天開盤請特別小心！\n");
            hasEvent = true;
        }
        if (isTodayUSConsumerConfidence()) {
            sb.append("今晚 23:00 美國消費者信心指數 (CCI) 即將公布！\n");
            sb.append("若低於預期，消費股與科技股易受壓！\n");
            hasEvent = true;
        }

        return hasEvent ? sb.toString() : null;
    }
}