package com.github.furrrlo.winelevator;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WinElevatorTest {

    @Test
    @EnabledOnOs(OS.WINDOWS)
    @Disabled("Requires UAC user input")
    void test() throws IOException, InterruptedException {
        final Process p = WinElevator.create()
                .newProcessBuilder(System.getenv("ComSpec"), "/C",
                        "net", "session", ">nul", "2>&1",
                        "&&", "echo",  "1",
                        "||", "echo", "0")
                .start();

        final String res;
        try(BufferedReader bis = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            res = bis.readLine().trim();
        }

        p.waitFor();
        assertEquals("1", res, "Command was not run as admin");
    }
}