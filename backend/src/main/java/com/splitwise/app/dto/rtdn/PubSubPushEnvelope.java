package com.splitwise.app.dto.rtdn;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The outer envelope Pub/Sub push delivers to a webhook - see
 * https://cloud.google.com/pubsub/docs/push#receive_push. {@code message.data}
 * is base64-encoded JSON; decoding it yields a DeveloperNotification.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PubSubPushEnvelope {

    public Message message;
    public String subscription;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Message {

        public String data;
        public String messageId;
        public String publishTime;
    }
}
