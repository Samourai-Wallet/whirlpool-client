package com.samourai.whirlpool.client;

import org.junit.*;
import org.junit.runner.RunWith;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

@RunWith(SpringRunner.class)
@SpringBootTest
@SpringBootConfiguration
public class ApplicationTest {
    private ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    private PrintStream outOrig = System.out;
    private PrintStream errOrig = System.err;

    private void captureSystem() {
        outContent = new ByteArrayOutputStream();
        errContent = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outContent));
        System.setErr(new PrintStream(errContent));
    }

    private void resetSystem() {
        System.setOut(outOrig);
        System.setErr(errOrig);
    }

    @Test
    public void listPools() {
        String[] args = new String[]{
            "--network=test",
            "--debug"
        };
        ApplicationArguments appArgs = new DefaultApplicationArguments(args);

        captureSystem();
        new Application().run(appArgs);
        resetSystem();

        Assert.assertTrue(outContent.toString().contains(" • Retrieving pools..."));
        Assert.assertTrue(errContent.toString().isEmpty());
    }

    @Test
    public void whirlpool() {
        String[] args = new String[]{
                "--network=test",
                "--utxo=733a1bcb4145e3dd0ea3e6709bef9504fd252c9a26b254508539e3636db659c2-1",
                "--utxo-key=cUe6J7Fs5mxg6jLwXE27xcDpaTPXfQZ9oKDbxs5PP6EpYMFHab2T",
                "--utxo-balance=1000102",
                "--seed-passphrase=w0",
                "--seed-words=all all all all all all all all all all all all",
                "--mixs=5",
                "--debug",
                "--pool=1btc"
        };
        ApplicationArguments appArgs = new DefaultApplicationArguments(args);

        captureSystem();
        new Application().run(appArgs); // TODO mock server
        resetSystem();

        Assert.assertTrue(outContent.toString().contains(" • connecting to "));
        Assert.assertTrue(errContent.toString().isEmpty());
    }
}