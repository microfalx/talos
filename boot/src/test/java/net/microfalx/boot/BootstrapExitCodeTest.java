package net.microfalx.boot;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Validates the exit code of the process, which requires the application to run in its own JVM.
 */
class BootstrapExitCodeTest {

    @TempDir
    private Path home;

    @BeforeEach
    void setup() throws IOException {
        Files.createDirectories(home.resolve("lib"));
        Files.createDirectories(home.resolve("tmp"));
        Files.createDirectories(home.resolve("logs"));
    }

    @Test
    void success() throws Exception {
        Assertions.assertThat(run(ApplicationMain.class)).isEqualTo(0);
    }

    @Test
    void outOfMemoryInBackgroundThread() throws Exception {
        Assertions.assertThat(run(OutOfMemoryMain.class)).isEqualTo(3);
        Assertions.assertThat(home.resolve("logs").resolve("boot.log")).content()
                .contains("Terminating due to java.lang.OutOfMemoryError");
    }

    @Test
    void failureInBackgroundThread() throws Exception {
        Assertions.assertThat(run(FailedThreadMain.class)).isEqualTo(0);
    }

    private int run(Class<?> mainClass) throws IOException, InterruptedException {
        String java = new File(System.getProperty("java.home"), "bin/java").getPath();
        ProcessBuilder builder = new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"),
                Bootstrap.class.getName(), mainClass.getName());
        builder.environment().put("HOME", home.toString());
        builder.redirectErrorStream(true).redirectOutput(home.resolve("output.log").toFile());
        Process process = builder.start();
        try {
            Assertions.assertThat(process.waitFor(60, TimeUnit.SECONDS)).isTrue();
            return process.exitValue();
        } finally {
            process.destroyForcibly();
        }
    }

    public static class ApplicationMain {

        public static void main(String[] args) {
            System.out.println("Starting Application");
        }
    }

    public static class OutOfMemoryMain {

        public static void main(String[] args) {
            new Thread(() -> {
                throw new OutOfMemoryError("Cannot reserve direct buffer memory");
            }).start();
        }
    }

    public static class FailedThreadMain {

        public static void main(String[] args) {
            new Thread(() -> {
                throw new IllegalStateException("Failed");
            }).start();
        }
    }
}
