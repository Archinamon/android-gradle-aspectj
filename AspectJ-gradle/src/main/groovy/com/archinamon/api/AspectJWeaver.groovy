package com.archinamon.api

import org.aspectj.bridge.IMessage
import org.aspectj.bridge.MessageHandler
import org.aspectj.tools.ajc.Main
import org.gradle.api.GradleException
import org.gradle.api.Project

import static com.archinamon.StatusLogger.logBuildParametersAdapted
import static com.archinamon.StatusLogger.logExtraAjcArgumentAlreayExists

class AspectJWeaver {

    def private static final errorReminder = "Look into %s file for details";

    private Project project;

    String compilationLogFile;
    String transformLogFile;
    String encoding;

    boolean weaveInfo;
    boolean debugInfo;
    boolean addSerialVUID;
    boolean noInlineAround;
    boolean ignoreErrors;

    boolean breakOnError;
    boolean experimental;

    ArrayList<String> ajcArgs = new ArrayList<>();

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

    protected void doWeave() {
        File log = prepareLogger();

        //http://www.eclipse.org/aspectj/doc/released/devguide/ajc-ref.html

        def args = [
                "-encoding", encoding,
                "-source", sourceCompatibility,
                "-target", targetCompatibility,
                "-d", destinationDir,
                "-bootclasspath", bootClasspath,
                "-classpath", classPath.join(File.pathSeparator),
                "-sourceroots", ajSources.join(File.pathSeparator)
        ];

        if (!inPath?.empty) {
            args << "-inpath" << inPath.join(File.pathSeparator);
        }

        if (!aspectPath.empty) {
            args << "-aspectpath" << aspectPath.join(File.pathSeparator);
        }

        if (!logFile?.empty) {
            args << "-log" << logFile;
        }

        if (debugInfo) {
            args << "-g";
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

        if (ignoreErrors) {
            args << "-proceedOnError" << "-noImportError";
        }

        if (experimental) {
            args << "-XhasMember" << "-Xjoinpoints:synchronization,arrayconstruction";
        }

        if (!ajcArgs.empty) {
            ajcArgs.each { String extra ->
                if (extra.startsWith('-') && args.contains(extra)) {
                    logExtraAjcArgumentAlreayExists(extra);
                    log << "[warning] Duplicate argument found while composing ajc config! Build may be corrupted.\n\n"
                }
                args << extra;
            }
        }

        log << "Full ajc build args: ${Arrays.toString(args as String[])}\n\n";
        logBuildParametersAdapted(args as String[], log.name);

        MessageHandler handler = new MessageHandler(true);
        new Main().run(args as String[], handler);
        for (IMessage message : handler.getMessages(null, true)) {
            switch (message.getKind()) {
                case IMessage.ERROR:
                    log << "[error]" << message?.message << "${message?.thrown}\n\n";
                    if (breakOnError) throw new GradleException(String.format(errorReminder, logFile));
                    break;
                case IMessage.FAIL:
                case IMessage.ABORT:
                    log << "[error]" << message?.message << "${message?.thrown}\n\n";
                    throw new GradleException(message?.message);
                case IMessage.INFO:
                case IMessage.DEBUG:
                case IMessage.WARNING:
                    log << "[warning]" << message?.message << "${message?.thrown}\n\n";
                    if (!logFile.empty) log << "${String.format(errorReminder, logFile)}\n\n";
                    break;
            }
        }

        detectErrors();
    }

    void setTransformLogFile(String name) {
        if (name != null && name.length() > 0) {
            this.transformLogFile = project.buildDir.absolutePath + File.separator + name;
            this.compilationLogFile = null;
        }
    }

    void setCompilationLogFile(String name) {
        if (name != null && name.length() > 0) {
            this.compilationLogFile = project.buildDir.absolutePath + File.separator + name;
            this.transformLogFile = null;
        }
    }

    void setAjSources(File... ajSources) {
        for (File input : ajSources) {
            if (!this.ajSources.contains(input)) {
                this.ajSources.add(input);
            }
        }
    }

    private String getLogFile() {
        return compilationLogFile ?: transformLogFile;
    }

    def private prepareLogger() {
        File lf = project.file(logFile);
        if (lf.exists()) {
            lf.delete();
        }

        return lf;
    }

    def private detectErrors() {
        File lf = project.file(logFile);
        if (lf.exists()) {
            lf.readLines().reverseEach { String line ->
                if (line.contains("[error]") && breakOnError) {
                    throw new GradleException("$line\n${String.format(errorReminder, logFile)}");
                }
            }
        }
    }
}
