package com.samourai.whirlpool.client.mix.listener;

public enum MixStep {
    CONNECTING,
    CONNECTED,

    REGISTERED_INPUT,

    CONFIRMING_INPUT,
    CONFIRMED_INPUT,

    REGISTERING_OUTPUT,
    REGISTERED_OUTPUT,

    REVEALED_OUTPUT,

    SIGNING,
    SIGNED,

    SUCCESS,
    FAIL
}
