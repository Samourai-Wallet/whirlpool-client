package com.samourai.whirlpool.client.mix.listener;

public enum MixStep {
    CONNECTING,
    CONNECTED,

    REGISTERING_INPUT,
    QUEUED_INPUT,
    REGISTERED_INPUT,

    REGISTERING_OUTPUT,
    REGISTERED_OUTPUT,

    REVEALING_OUTPUT,
    REVEALED_OUTPUT,

    SIGNING,
    SIGNED,

    SUCCESS,
    FAIL
}
