package com.pyisland.server.agent.service;

import com.tencent.asrv2.AsrConstant;
import com.tencent.asrv2.SpeechRecognizer;
import com.tencent.asrv2.SpeechRecognizerListener;
import com.tencent.asrv2.SpeechRecognizerRequest;
import com.tencent.asrv2.SpeechRecognizerResponse;
import com.tencent.asrv2.SpeechRecognizerResult;
import com.tencent.core.ws.Credential;
import com.tencent.core.ws.SpeechClient;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class TencentRealtimeAsrRelayService {

    private static final Logger log = LoggerFactory.getLogger(TencentRealtimeAsrRelayService.class);

    @Value("${TENCENT_ASR_APP_ID:}")
    private String appId;

    @Value("${TENCENT_ASR_SECRET_ID:}")
    private String secretId;

    @Value("${TENCENT_ASR_SECRET_KEY:}")
    private String secretKey;

    @Value("${TENCENT_ASR_ENGINE_MODEL_TYPE:}")
    private String engineModelType;

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

        Session[] sessionHolder = new Session[1];

        SpeechRecognizerListener listener = new SpeechRecognizerListener() {
            @Override
            public void onRecognitionStart(SpeechRecognizerResponse response) {
                log.debug("ASR recognition started, voiceId={}", response == null ? "" : response.getVoiceId());
                callbacks.onReady();
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
                String msg = response == null ? "" : response.getMessage();
                log.warn("ASR recognition failed, code={}, message={}", response == null ? -1 : response.getCode(), msg);
                callbacks.onError(msg == null || msg.isBlank() ? "腾讯语音识别失败" : msg);
                if (sessionHolder[0] != null) {
                    sessionHolder[0].stop();
                }
            }

            @Override
            public void onMessage(SpeechRecognizerResponse response) {
                // handled by specific callbacks above
            }
        };

        SpeechRecognizer recognizer = new SpeechRecognizer(client, credential, request, listener);
        recognizer.start();
        Session session = new Session(recognizer);
        sessionHolder[0] = session;
        return session;
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
        SpeechRecognizerResult result = response.getResult();
        if (result == null) {
            return "";
        }
        String text = result.getVoiceTextStr();
        return text == null ? "" : text.trim();
    }

    private boolean hasValue(String value) {
        return value != null && !value.isBlank();
    }

    public interface Callbacks {
        void onReady();
        void onPartial(String text);
        void onFinal(String text);
        void onError(String message);
    }

    public static class Session {
        private final SpeechRecognizer recognizer;
        private volatile boolean stopped = false;

        public Session(SpeechRecognizer recognizer) {
            this.recognizer = recognizer;
        }

        public void write(byte[] bytes) throws Exception {
            if (stopped) return;
            recognizer.write(bytes);
        }

        public synchronized void stop() {
            if (stopped) return;
            stopped = true;
            try {
                recognizer.close();
            } catch (Exception ignored) {
            }
        }
    }
}
