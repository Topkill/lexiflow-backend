package com.lexiflow.ai.core.client;

@FunctionalInterface
public interface AiStreamDeltaHandler {

    void onDelta(String delta) throws Exception;
}
