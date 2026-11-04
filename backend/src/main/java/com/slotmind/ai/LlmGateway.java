package com.slotmind.ai;

import java.util.function.Consumer;

public interface LlmGateway {

    /** Sends the prompt and returns the full model text. */
    String call(String prompt);

    /** Sends the prompt and delivers each text chunk as it arrives. */
    void stream(String prompt, Consumer<String> onChunk);
}
