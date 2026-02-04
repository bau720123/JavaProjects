package com.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;

/**
 * 抓取 Robinhood 特定儀器（股票/ETF）的即時 detail-page-live-updating-data
 * 主要取得 secondary_value.main.value （例如 "-$0.25 (-0.07%)"）
 */
public class RobinHoodService {

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";
    private static final int TIMEOUT_MS = 15000;

    private final OkHttpClient client = new OkHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    private static final String BASE_URL_TEMPLATE = 
        "https://bonfire.robinhood.com/instruments/%s/detail-page-live-updating-data/?display_span=day&hide_extended_hours=false";

    public static class RobinHoodRealtime {
        private final boolean success;
        private final String changeText;   // e.g. "-$0.43 (-0.13%)"
        private final String errorMessage;

        public RobinHoodRealtime(boolean success, String changeText, String errorMessage) {
            this.success = success;
            this.changeText = changeText != null ? changeText : "";
            this.errorMessage = errorMessage != null ? errorMessage : "";
        }

        public boolean isSuccess() {
            return success;
        }

        public String getChangeText() {
            return changeText;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }

    /**
     * 抓取指定 instrumentId 的 Robinhood 即時變動資料
     * @param instrumentId Robinhood 的儀器唯一識別碼，例如 "ca4821f9-06c3-4c22-bbb8-efe569f23d2b"
     */
    public RobinHoodRealtime fetchRealtimeChange(String instrumentId) {
        if (instrumentId == null || instrumentId.trim().isEmpty()) {
            return new RobinHoodRealtime(false, null, "instrumentId 不可為空");
        }

        String url = String.format(BASE_URL_TEMPLATE, instrumentId);

        try {
            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .get()
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    return new RobinHoodRealtime(false, null,
                            "HTTP " + response.code() + " - " + response.message());
                }

                String json = response.body().string();
                JsonNode root = mapper.readTree(json);

                JsonNode secondary = root.path("chart_section")
                                        .path("default_display")
                                        .path("secondary_value")
                                        .path("main");

                if (secondary.isMissingNode() || secondary.isNull()) {
                    return new RobinHoodRealtime(false, null, "找不到 secondary_value.main 欄位");
                }

                String value = secondary.path("value").asText(null);
                if (value == null || value.trim().isEmpty()) {
                    return new RobinHoodRealtime(false, null, "value 欄位為空");
                }

                return new RobinHoodRealtime(true, value, null);

            } catch (IOException e) {
                return new RobinHoodRealtime(false, null, "網路或 JSON 解析錯誤：" + e.getMessage());
            }
        } catch (Exception e) {
            return new RobinHoodRealtime(false, null, "意外錯誤：" + e.getMessage());
        }
    }
}