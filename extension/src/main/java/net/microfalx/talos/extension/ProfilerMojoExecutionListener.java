package net.microfalx.talos.extension;

import net.microfalx.lang.ObjectUtils;
import net.microfalx.talos.core.MavenLogger;
import net.microfalx.talos.core.MavenTracker;
import net.microfalx.talos.core.MavenUtils;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.execution.MojoExecutionEvent;
import org.apache.maven.execution.MojoExecutionListener;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.eclipse.sisu.PostConstruct;
import org.eclipse.sisu.Priority;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;

@Named("mojo")
@Singleton
@Priority(1)
public class ProfilerMojoExecutionListener implements MojoExecutionListener {

    @Inject
    private ProfilerMetrics profilerMetrics;

    @Inject
    private MavenLogger logger;

    @Inject
    private MavenSession session;

    private final MavenTracker tracker = new MavenTracker(ProfilerMojoExecutionListener.class);
    private MavenConfiguration configuration;
    private PrintStream output;

    private volatile MavenProject lastProject;
    private volatile String lastGoal;
    private volatile String lastAction;

    private final Object lock = new Object();

    private static final Map<String, String> goalsToPrint = new HashMap<>();

    @Override
    public void beforeMojoExecution(MojoExecutionEvent event) throws MojoExecutionException {
        tracker.track("Mojo Execution", t -> {
            profilerMetrics.mojoStarted(event.getMojo(), event.getExecution());
            if (configuration.isQuietAndWithProgress()) printMojo(event);
        }, event.getProject(), event.getMojo());

    }

    @Override
    public void afterMojoExecutionSuccess(MojoExecutionEvent event) throws MojoExecutionException {
        tracker.track("Mojo Success", t -> {
            profilerMetrics.mojoStop(event.getProject(), event.getMojo(), null);
        }, event.getProject(), event.getMojo());
    }

    @Override
    public void afterExecutionFailure(MojoExecutionEvent event) {
        tracker.track("Mojo Failure", t -> {
            profilerMetrics.mojoStop(event.getProject(), event.getMojo(), event.getCause());
        }, event.getProject(), event.getMojo());
    }

    private void printMojo(MojoExecutionEvent event) {
        if (!configuration.isQuietAndWithProgress()) return;
        String goal = MavenUtils.getGoal(event.getExecution());
        synchronized (lock) {
            if (!ObjectUtils.equals(lastGoal, goal)) {
                String action = goalsToPrint.get(goal);
                if (action != null && !ObjectUtils.equals(lastAction, action)) {
                    print(action);
                    lastAction = action;
                }
            }
            print(".");
            lastProject = event.getProject();
            lastGoal = goal;
        }
    }

    @PostConstruct
    public void initialize() {
        configuration = new MavenConfiguration(session);
        output = logger.getSystemOutputPrintStream();
    }

    private void print(String message) {
        output.print(message);
    }

    static {
        goalsToPrint.put("clean", "clean");
        goalsToPrint.put("compiler:compile", "compile");
        goalsToPrint.put("compiler:testCompile", "compile");
        goalsToPrint.put("surefire:test", "unit-tests");
        goalsToPrint.put("failsafe:integration-test", "integration-tests");
        goalsToPrint.put("failsafe:verify", "verify");
        goalsToPrint.put("javadoc:jar", "doc");
        goalsToPrint.put("jar:jar", "package");
        goalsToPrint.put("site:site", "site");
        goalsToPrint.put("install:install", "install");
        goalsToPrint.put("deploy:deploy", "deploy");
    }
}
