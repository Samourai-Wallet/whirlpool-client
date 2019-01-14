package com.samourai.whirlpool.client.utils;

import com.samourai.whirlpool.client.test.AbstractTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class FeeUtilsTest extends AbstractTest {

  @Test
  public void estimateTxBytes() throws Exception {
    // 72e5d19ee2f56a6db75993b47bbade1011e37a2899b4ee30b8ffdc2b8c8c9f2b: 1 in + 1 out = 191
    Assertions.assertEquals(191, FeeUtils.estimateTxBytes(1, 1));

    Assertions.assertEquals(303, FeeUtils.estimateTxBytes(3, 3));
    Assertions.assertEquals(404, FeeUtils.estimateTxBytes(4, 4));
    Assertions.assertEquals(505, FeeUtils.estimateTxBytes(5, 5));
  }
}
