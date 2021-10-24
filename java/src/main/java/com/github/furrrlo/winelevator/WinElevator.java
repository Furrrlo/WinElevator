package com.github.furrrlo.winelevator;

import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;

public interface WinElevator {

    static WinElevator create() throws UnsupportedOperationException, UncheckedIOException {
        return new WinElevatorImpl();
    }

    static WinElevator create(Path binFolder) throws UnsupportedOperationException, UncheckedIOException {
        return new WinElevatorImpl(binFolder);
    }

    ProcessBuilder newProcessBuilder(List<String> command);

    ProcessBuilder newProcessBuilder(String... command);
}
