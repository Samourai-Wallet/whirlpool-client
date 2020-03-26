package com.samourai.wallet.util;

public interface CallbackWithArg<S> {
  void apply(S arg) throws Exception;
}
