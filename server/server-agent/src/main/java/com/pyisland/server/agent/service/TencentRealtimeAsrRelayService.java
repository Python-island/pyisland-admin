package com.pyisland.server.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tencent.asrv2.AsrConstant;
import com.tencent.asrv2.SpeechRecognizer;
import com.tencent.asrv2.SpeechRecognizerListener;
import com.tencent.asrv2.SpeechRecognizerRequest;
import com.tencent.asrv2.SpeechRecognizerResponse;
import com.tencent.core.ws.Credential;
import com.tencent.core.ws.SpeechClient;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class TencentRealtimeAsrRelayService {

    @Value("${TENCENT_ASR_APP_ID:}")
    private String appId;

    @Value("${TENCENT_ASR_SECRET_ID:}")
    private String secretId;

    @Value("${TENCENT_ASR_SECRET_KEY:}")
    private String secretKey;

    @Value("${TENCENT_ASR_ENGINE_MODEL_TYPE:}")
    private String engineModelType;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private SpeechClient speechClient;

    public boolean isConfigured() {
        return hasValue(appId) && hasValue(secretId) && hasValue(secretKey) && hasValue(engineModelType);
    }

    public Session startSession(Callbacks callbacks) throws Exception {
        if (!isConfigured()) {
            throw new IllegalStateException("Tencent ASR credentials not configured");
        }

        SpeechClient client = getOrCreateClient();
        Credential credential = new Credential(appId.trim(), secretId.trim(), secretKey.trim());

        SpeechRecognizerRequest request = SpeechRecognizerRequest.init();
        request.setEngineModelType(engineModelType.trim());
        request.setVoiceFormat(1);
        request.setVoiceId(UUID.randomUUID().toString());

        final String[] lastPartial = {""};
        final String[] lastFinal = {""};

        SpeechRecognizerListener listener = new SpeechRecognizerListener() {
            @Override
            public void onRecognitionStart(SpeechRecognizerResponse response) {
                // no-op
            }

            @Override
            public void onSentenceBegin(SpeechRecognizerResponse response) {
                // no-op
            }

            @Override
            public void onRecognitionResultChange(SpeechRecognizerResponse response) {
                String text = extractText(response);
                if (!text.isBlank() && !text.equals(lastPartial[0])) {
                    lastPartial[0] = text;
                    callbacks.onPartial(text);
                }
            }

            @Override
            public void onSentenceEnd(SpeechRecognizerResponse response) {
                String text = extractText(response);
                if (!text.isBlank() && !text.equals(lastFinal[0])) {
                    lastFinal[0] = text;
                    callbacks.onFinal(text);
                }
            }

            @Override
            public void onRecognitionComplete(SpeechRecognizerResponse response) {
                // no-op
            }

            @Override
            public void onFail(SpeechRecognizerResponse response) {
                String message = extractError(response);
                callbacks.onError(message.isBlank() ? "腾讯语音识别失败" : message);
            }

            @Override
            public void onMessage(SpeechRecognizerResponse response) {
                tryEmitByMessage(response, callbacks, lastPartial, lastFinal);
            }
        };

        SpeechRecognizer recognizer = new SpeechRecognizer(client, credential, request, listener);
        recognizer.start();
        return new Session(recognizer);
    }

    @PreDestroy
    public void shutdown() {
        if (speechClient != null) {
            speechClient.shutdown();
            speechClient = null;
        }
    }

    private synchronized SpeechClient getOrCreateClient() {
        if (speechClient == null) {
            speechClient = new SpeechClient(AsrConstant.DEFAULT_RT_REQ_URL);
        }
        return speechClient;
    }

    private String extractText(SpeechRecognizerResponse response) {
        if (response == null) {
            return "";
        }
        JsonNode node = objectMapper.valueToTree(response);
        String[] keys = {"voice_text_str", "voiceTextStr", "text", "result_text", "resultText"};
        for (String key : keys) {
            JsonNode value = node.get(key);
            if (value != null && !value.isNull()) {
                String text = value.asText("").trim();
                if (!text.isBlank()) {
                    return text;
                }
            }
        }
        return "";
    }

    private void tryEmitByMessage(SpeechRecognizerResponse response,
                                  Callbacks callbacks,
                                  String[] lastPartial,
                                  String[] lastFinal) {
        if (response == null) {
            return;
        }
        JsonNode node = objectMapper.valueToTree(response);
        String text = extractText(response);
        if (text.isBlank()) {
            return;
        }

        int sliceType = parseInt(node, "slice_type", "sliceType", "slice");
        boolean finalFlag = parseBoolean(node, "final", "is_final", "isFinal");

        if (sliceType == 2 || finalFlag) {
            if (!text.equals(lastFinal[0])) {
                lastFinal[0] = text;
                callbacks.onFinal(text);
            }
            return;
        }

        if (sliceType == 1 || sliceType == 0 || !text.equals(lastPartial[0])) {
            if (!text.equals(lastPartial[0])) {
                lastPartial[0] = text;
                callbacks.onPartial(text);
            }
        }
    }

    private int parseInt(JsonNode node, String... keys) {
        if (node == null || keys == null) {
            return Integer.MIN_VALUE;
        }
        for (String key : keys) {
            JsonNode value = node.get(key);
            if (value == null || value.isNull()) {
                continue;
            }
            if (value.isInt() || value.isLong()) {
                return value.asInt();
            }
            if (value.isTextual()) {
                try {
                    return Integer.parseInt(value.asText().trim());
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return Integer.MIN_VALUE;
    }

    private boolean parseBoolean(JsonNode node, String... keys) {
        if (node == null || keys == null) {
            return false;
        }
        for (String key : keys) {
            JsonNode value = node.get(key);
            if (value == null || value.isNull()) {
                continue;
            }
            if (value.isBoolean()) {
                return value.asBoolean(false);
            }
            if (value.isInt() || value.isLong()) {
                return value.asInt(0) == 1;
            }
            if (value.isTextual()) {
                String text = value.asText("").trim();
                if ("1".equals(text) || "true".equalsIgnoreCase(text)) {
                    return true;
                }
            }
        }
        return false;
    }

    private String extractError(SpeechRecognizerResponse response) {
        if (response == null) {
            return "";
        }
        JsonNode node = objectMapper.valueToTree(response);
        String[] keys = {"message", "error_msg", "errorMsg", "reason"};
        for (String key : keys) {
            JsonNode value = node.get(key);
            if (value != null && !value.isNull()) {
                String text = value.asText("").trim();
                if (!text.isBlank()) {
                    return text;
                }
            }
        }
        return "";
    }

    private boolean hasValue(String value) {
        return value != null && !value.isBlank();
    }

    public interface Callbacks {
        void onPartial(String text);
        void onFinal(String text);
        void onError(String message);
    }

    public static class Session {
        private final SpeechRecognizer recognizer;

        public Session(SpeechRecognizer recognizer) {
            this.recognizer = recognizer;
        }

        public void write(byte[] bytes) throws Exception {
            recognizer.write(bytes);
        }

        public void stop() {
            try {
                recognizer.stop();
            } catch (Exception ignored) {
            }
            try {
                recognizer.close();
            } catch (Exception ignored) {
            }
        }
    }
}
