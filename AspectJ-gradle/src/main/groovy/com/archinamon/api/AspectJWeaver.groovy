package com.archinamon.api

import org.aspectj.bridge.IMessage
import org.aspectj.bridge.MessageHandler
import org.aspectj.tools.ajc.Main
import org.gradle.api.Project
import org.gradle.api.tasks.StopExecutionException

class AspectJWeaver {

    def private static final errorReminder = "Look into %s file for details";

    private Project project;

    String logFile;
    String encoding;

    boolean weaveInfo;
    boolean addSerialVUID;
    boolean noInlineAround;

    ArrayList<File> ajSources = new ArrayList<>();
    ArrayList<File> aspectPath = new ArrayList<>();
    ArrayList<File> inPath = new ArrayList<>();
    ArrayList<File> classPath = new ArrayList<>();
    String bootClasspath;
    String sourceCompatibility;
    String targetCompatibility;
    String destinationDir;

    AspectJWeaver(Project project) {
        this.project = project;
    }

    protected void weave() {

        final def log = project.logger;

        //http://www.eclipse.org/aspectj/doc/released/devguide/ajc-ref.html
        //
        // -sourceroots:
        //  Find and build all .java or .aj source files under any directory listed in DirPaths. DirPaths, like classpath, is a single argument containing a list of paths to directories, delimited by the platform- specific classpath delimiter. Required by -incremental.
        // -inpath:
        //  Accept as source bytecode any .class files in the .jar files or directories on Path. The output will include these classes, possibly as woven with any applicable aspects. Path is a single argument containing a list of paths to zip files or directories, delimited by the platform-specific path delimiter.
        // -classpath:
        //  Specify where to find user class files. Path is a single argument containing a list of paths to zip files or directories, delimited by the platform-specific path delimiter.
        // -aspectpath:
        //  Weave binary aspects from jar files and directories on path into all sources. The aspects should have been output by the same version of the compiler. When running the output classes, the run classpath should contain all aspectPath entries. Path, like classpath, is a single argument containing a list of paths to jar files, delimited by the platform- specific classpath delimiter.
        // -bootclasspath:
        //  Override location of VM's bootClasspath for purposes of evaluating types when compiling. Path is a single argument containing a list of paths to zip files or directories, delimited by the platform-specific path delimiter.
        // -d:
        //  Specify where to place generated .class files. If not specified, Directory defaults to the current working dir.
        // -preserveAllLocals:
        //  Preserve all local variables during code generation (to facilitate debugging).

        def args = [
                "-encoding", encoding,
                "-source", sourceCompatibility,
                "-target", targetCompatibility,
                "-d", destinationDir,
                "-bootclasspath", bootClasspath,
                "-classpath", classPath.join(File.pathSeparator),
                "-inpath", inPath.join(File.pathSeparator),
                "-aspectpath", aspectPath.join(File.pathSeparator),
                "-sourceroots", ajSources.join(File.pathSeparator),
                "-preserveAllLocals"
        ];

        if (!logFile?.isEmpty()) {
            args << "-log" << logFile;
        }

        if (weaveInfo) {
            args << "-showWeaveInfo";
        }

        if (addSerialVUID) {
            args << "-XaddSerialVersionUID";
        }

        if (noInlineAround) {
            args << "-XnoInline";
        }

        log.warn "ajc args: " + Arrays.toString(args as String[]);

        MessageHandler handler = new MessageHandler(true);
        new Main().run(args as String[], handler);
        for (IMessage message : handler.getMessages(null, true)) {
            switch (message.getKind()) {
                case IMessage.ERROR:
                    log.error message?.message, message?.thrown;
                    if (!logFile?.empty) log.error(errorReminder, logFile);
                    break;
                case IMessage.FAIL:
                case IMessage.ABORT:
                    log.error message?.message, message?.thrown;
                    if (!logFile?.empty) log.error(errorReminder, logFile);
                    throw new StopExecutionException(message?.message);
                case IMessage.INFO:
                case IMessage.DEBUG:
                case IMessage.WARNING:
                    log.warn message?.message, message?.thrown;
                    if (!logFile?.empty) log.error(errorReminder, logFile);
                    break;
            }
        }
    }

    void setLogFile(String name) {
        if (name != null && name.length() > 0)
            this.logFile = project.buildDir.absolutePath + File.separator + name;
    }

    void setAjSources(File... ajSources) {
        for (File input : ajSources) {
            if (!this.ajSources.contains(input)) {
                this.ajSources.add(input);
            }
        }
    }
}
