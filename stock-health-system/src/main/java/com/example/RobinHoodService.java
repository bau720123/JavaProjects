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
        private final String changeText;      // secondary_value.main.value
        private final String tertiaryText;    // 新增：tertiary_value 的 value
        private final String errorMessage;

        public RobinHoodRealtime(boolean success, String changeText, String tertiaryText, String errorMessage) {
            this.success = success;
            this.changeText = changeText != null ? changeText : "";
            this.tertiaryText = tertiaryText != null ? tertiaryText : "";
            this.errorMessage = errorMessage != null ? errorMessage : "";
        }

        public boolean isSuccess() {
            return success;
        }

        public String getChangeText() {
            return changeText;
        }

        public String getTertiaryText() {
            return tertiaryText;
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
            return new RobinHoodRealtime(false, null, null, "instrumentId 不可為空");
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
                    return new RobinHoodRealtime(false, null, null,
                            "HTTP " + response.code() + " - " + response.message());
                }

                String json = response.body().string();
                JsonNode root = mapper.readTree(json);

                // 共同的路徑前綴
                JsonNode display = root.path("chart_section")
                                      .path("default_display");

                // secondary_value.main.value
                JsonNode secondaryMain = display.path("secondary_value").path("main");
                String secondaryValue = secondaryMain.path("value").asText(null);

                // tertiary_value.main.value （假設結構類似 secondary）
                JsonNode tertiaryMain = display.path("tertiary_value").path("main");
                String tertiaryValue = tertiaryMain.path("value").asText(null);

                // 只要其中一個有值就算成功（或依需求調整）
                if ((secondaryValue == null || secondaryValue.trim().isEmpty()) &&
                    (tertiaryValue == null || tertiaryValue.trim().isEmpty())) {
                    return new RobinHoodRealtime(false, null, null, "找不到 secondary_value 或 tertiary_value 的 value");
                }

                return new RobinHoodRealtime(true,
                        secondaryValue,
                        tertiaryValue,
                        null);

            } catch (IOException e) {
                return new RobinHoodRealtime(false, null, null, "網路或 JSON 解析錯誤：" + e.getMessage());
            }
        } catch (Exception e) {
            return new RobinHoodRealtime(false, null, null, "意外錯誤：" + e.getMessage());
        }
    }
}