package net.microfalx.boot;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Method;
import java.text.MessageFormat;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static net.microfalx.boot.BootstrapUtils.isNotEmpty;


/**
 * The main class which bootstraps a Java application.
 */
public class Bootstrap {

    private static final int EXIT_CODE = 10;
    /**
     * The exit code used by the JVM when <code>-XX:+ExitOnOutOfMemoryError</code> terminates the process.
     */
    private static final int OUT_OF_MEMORY_EXIT_CODE = 3;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final LocalDateTime startupTime = LocalDateTime.now();
    private final ApplicationBuilder applicationBuilder = new ApplicationBuilder();
    private final StringBuilder log = new StringBuilder();
    private Writer writer;
    private final boolean logConsole = getSystemProperty("log", false);

    private Method mainMethod;
    private int exitCode = EXIT_CODE;
    private String[] args;

    private static Bootstrap instance;

    static Bootstrap get() {
        if (instance == null) instance = new Bootstrap(false);
        return instance;
    }

    public Bootstrap() {
        this(true);
    }

    Bootstrap(boolean init) {
        instance = this;
        if (init) initLog();
    }

    /**
     * Returns the boot log.
     *
     * @return a non-null instance
     */
    public String getLog() {
        return log.toString();
    }

    /**
     * Changes the exit code if a problem is detected.
     * <p>
     * Mainly used during unit tests.
     *
     * @param exitCode the exit code
     */
    public void setExitCode(int exitCode) {
        this.exitCode = exitCode;
    }

    /**
     * Logs a message into the bootstrap log.
     *
     * @param message the message
     */
    public void log(String message) {
        if (logConsole) System.out.println(message);
        log.append(message).append('\n');
        Duration millisSinceStart = Duration.between(startupTime, LocalDateTime.now());
        LocalDateTime time = LocalDate.now().atStartOfDay().plus(millisSinceStart);
        message = "[" + FORMATTER.format(time) + "] " + message;
        if (writer != null) {
            try {
                writer.append(message).append('\n');
                writer.flush();
            } catch (IOException e) {
                // just ignore
            }
        }
    }

    /**
     * Logs a message into the bootstrap log.
     *
     * @param format the message format
     * @param args   arguments passed to the format
     */
    public void log(String format, Object... args) {
        if (args.length == 0) {
            log(format);
        } else {
            log(MessageFormat.format(format, args));
        }
    }

    void start(String[] args) {
        log("Starting application, home directory ''{0}''", applicationBuilder.getHome().getAbsolutePath());
        init(args);
        run();
    }

    public void init(String[] args) {
        if (args == null || args.length == 0) {
            log("At least one parameters is required, target main class name");
            abort();
            return;
        }
        String mainClassName = args[0];
        if (args.length - 1 > 0) {
            this.args = new String[args.length - 1];
            System.arraycopy(args, 1, this.args, 0, args.length - 1);
        } else {
            this.args = new String[0];
        }
        getPidFile().delete();
        ClassLoader classLoader = new BootstrapClassLoader(applicationBuilder.getClassPath(), ClassLoader.getSystemClassLoader());
        Thread.currentThread().setContextClassLoader(classLoader);
        try {
            Class<?> mainClass = Class.forName(mainClassName, false, classLoader);
            mainMethod = mainClass.getMethod("main", String[].class);
            log("Application class ''{0}'' loaded successfully", mainClassName);
        } catch (ClassNotFoundException e) {
            log("Application class ''{0}'' does not exists", mainClassName);
        } catch (NoSuchMethodException e) {
            log("Application class ''{0}'' does not have a main method", mainClassName);
        } catch (Exception e) {
            log("Application class ''{0}'' could not be loaded", mainClassName);
        }
        if (mainMethod == null) abort();
    }

    public void run() {
        if (mainMethod == null) return;
        if (args.length > 0) {
            log("Startup parameters:");
            for (String arg : args) {
                log("  - \"{0}\"", arg);
            }
        }
        try {
            mainMethod.invoke(null, new Object[]{args});
            log("Application started successfully, PID=" + getPid());
            writePid();
        } catch (Exception e) {
            log("Failed to start the application, stack trace\n{0}", BootstrapUtils.getStackTrace(e));
            abort();
        }
    }

    private void initLog() {
        try {
            writer = new FileWriter(new File(applicationBuilder.getLogsDirectory(), "boot.log"));
        } catch (IOException e) {
            System.err.println("Failed to create bootstrap logger, root cause: " + e.getMessage());
        }
    }

    private void abort() {
        if (exitCode > 0) System.exit(exitCode);
    }

    /**
     * Terminates the process when an {@link OutOfMemoryError} is not handled by the thread which raised it.
     * <p>
     * <code>-XX:+ExitOnOutOfMemoryError</code> only covers errors raised by the VM. The ones raised from Java code
     * (direct buffer memory, for example) only kill the current thread, and the process would report a successful exit
     * once the remaining threads complete.
     * <p>
     * The process is halted, like the JVM does, because shutdown hooks are not guaranteed to complete at this point.
     *
     * @param thread    the thread which failed
     * @param throwable the failure
     */
    private void handleUncaughtException(Thread thread, Throwable throwable) {
        boolean outOfMemory = throwable instanceof OutOfMemoryError;
        try {
            System.err.print("Exception in thread \"" + thread.getName() + "\" ");
            throwable.printStackTrace(System.err);
            if (outOfMemory) {
                log("Terminating due to " + throwable + " in thread '" + thread.getName() + "'");
            }
        } catch (Throwable e) {
            // the exit code must be reported even if there is not enough memory to report the failure
        } finally {
            if (outOfMemory) Runtime.getRuntime().halt(OUT_OF_MEMORY_EXIT_CODE);
        }
    }

    /**
     * Returns the file which holds the process PID.
     *
     * @return a non-null instance
     */
    private File getPidFile() {
        return new File(applicationBuilder.getTmpDirectory(), ".pid");
    }

    /**
     * Writes the PID to a file.
     */
    private void writePid() {
        try {
            try (Writer writer = new FileWriter(getPidFile())) {
                writer.write(Long.toString(getPid()));
            }
        } catch (IOException e) {
            log("Failed to write PID file, root cause: " + e.getMessage());
        }
    }

    private long getPid() {
        return ProcessHandle.current().pid();
    }

    /**
     * Returns a system property from the "microfalx" namespace.
     *
     * @param name the suffix of the property
     * @return a non-null instance
     */
    static String getSystemProperty(String name) {
        return System.getProperty("talos." + name);
    }

    /**
     * Returns a system property from the "microfalx" namespace.
     *
     * @param name the suffix of the property
     * @return a non-null instance
     */
    static boolean getSystemProperty(String name, boolean defaultValue) {
        String value = getSystemProperty(name);
        if (isNotEmpty(value)) {
            return "1".equals(value) || Boolean.parseBoolean(value);
        } else {
            return defaultValue;
        }
    }

    public static void main(String[] args) {
        Bootstrap bootstrap = new Bootstrap();
        Thread.setDefaultUncaughtExceptionHandler(bootstrap::handleUncaughtException);
        bootstrap.start(args);
    }

}
