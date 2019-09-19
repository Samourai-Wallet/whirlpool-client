package com.samourai.wallet.client.indexHandler;

import com.samourai.whirlpool.client.test.AbstractTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class IndexHandlerTest extends AbstractTest {
  private MemoryIndexHandler indexHandler;

  public IndexHandlerTest() throws Exception {
    indexHandler = new MemoryIndexHandler();
  }

  @Test
  public void getAndSet() throws Exception {
    Assertions.assertEquals(0, indexHandler.get());
    Assertions.assertEquals(0, indexHandler.get());

    Assertions.assertEquals(0, indexHandler.getAndIncrement());
    Assertions.assertEquals(1, indexHandler.getAndIncrement());
    Assertions.assertEquals(2, indexHandler.getAndIncrement());

    indexHandler.set(5);
    Assertions.assertEquals(5, indexHandler.getAndIncrement());
    Assertions.assertEquals(6, indexHandler.getAndIncrement());
  }

  @Test
  public void getAndSetUnconfirmed() throws Exception {
    Assertions.assertEquals(0, indexHandler.getUnconfirmed());
    Assertions.assertEquals(0, indexHandler.get());
    Assertions.assertEquals(0, indexHandler.getUnconfirmed());
    Assertions.assertEquals(0, indexHandler.get());

    Assertions.assertEquals(0, indexHandler.getAndIncrementUnconfirmed());
    Assertions.assertEquals(0, indexHandler.get());
    Assertions.assertEquals(1, indexHandler.getAndIncrementUnconfirmed());
    Assertions.assertEquals(0, indexHandler.get());
    Assertions.assertEquals(2, indexHandler.getAndIncrementUnconfirmed());
    Assertions.assertEquals(0, indexHandler.get());
    Assertions.assertEquals(3, indexHandler.getAndIncrementUnconfirmed());
    Assertions.assertEquals(0, indexHandler.get());
    Assertions.assertEquals(4, indexHandler.getAndIncrementUnconfirmed());
    Assertions.assertEquals(0, indexHandler.get());
    Assertions.assertEquals(5, indexHandler.getAndIncrementUnconfirmed());
    Assertions.assertEquals(0, indexHandler.get());
    Assertions.assertEquals(6, indexHandler.getUnconfirmed());

    indexHandler.cancelUnconfirmed(5);
    Assertions.assertEquals(5, indexHandler.getUnconfirmed());
    Assertions.assertEquals(0, indexHandler.get());
    indexHandler.cancelUnconfirmed(4);
    Assertions.assertEquals(4, indexHandler.getUnconfirmed());
    Assertions.assertEquals(0, indexHandler.get());
    indexHandler.cancelUnconfirmed(1);
    Assertions.assertEquals(4, indexHandler.getUnconfirmed());
    Assertions.assertEquals(0, indexHandler.get());
    indexHandler.cancelUnconfirmed(2);
    Assertions.assertEquals(4, indexHandler.getUnconfirmed());
    Assertions.assertEquals(0, indexHandler.get());
    indexHandler.cancelUnconfirmed(3);
    Assertions.assertEquals(1, indexHandler.getUnconfirmed());
    Assertions.assertEquals(0, indexHandler.get());

    Assertions.assertEquals(0, indexHandler.getAndIncrement());
    Assertions.assertEquals(1, indexHandler.getAndIncrement());
    Assertions.assertEquals(2, indexHandler.getAndIncrement());

    indexHandler.set(5);
    Assertions.assertEquals(5, indexHandler.getAndIncrement());
    Assertions.assertEquals(6, indexHandler.getAndIncrement());
    Assertions.assertEquals(7, indexHandler.getAndIncrementUnconfirmed());
    Assertions.assertEquals(8, indexHandler.getUnconfirmed());
    Assertions.assertEquals(7, indexHandler.get());

    indexHandler.cancelUnconfirmed(7);
    Assertions.assertEquals(7, indexHandler.getUnconfirmed());
    Assertions.assertEquals(7, indexHandler.get());

    Assertions.assertEquals(7, indexHandler.getAndIncrementUnconfirmed());

    Assertions.assertEquals(8, indexHandler.getAndIncrementUnconfirmed());

    Assertions.assertEquals(9, indexHandler.getAndIncrementUnconfirmed());

    Assertions.assertEquals(10, indexHandler.getAndIncrementUnconfirmed());

    indexHandler.confirmUnconfirmed(10);
    Assertions.assertEquals(11, indexHandler.getAndIncrementUnconfirmed());
    Assertions.assertEquals(11, indexHandler.get());
  }
}
