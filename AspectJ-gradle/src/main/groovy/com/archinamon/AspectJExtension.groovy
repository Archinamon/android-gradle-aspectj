package com.archinamon

public class AspectJExtension {

    def String ajc = "1.8.10";

    def boolean includeAllJars = false;

    List<String> includeJar = new ArrayList<String>();
    List<String> includeAspectsFromJar = new ArrayList<String>();
    List<String> ajcArgs = new ArrayList<String>();

    def boolean weaveInfo = true;
    def boolean debugInfo = false;
    def boolean addSerialVersionUID = false;
    def boolean noInlineAround = false;
    def boolean ignoreErrors = false;

    def boolean breakOnError = true;
    def boolean experimental = false;

    def String transformLogFile = "ajc-transform.log";
    def String compilationLogFile = "ajc-compile.log";

    public AspectJExtension ajcArgs(String... args) {
        if (args != null) {
            ajcArgs.addAll(args);
        }
    }

    public AspectJExtension includeJar(String... filters) {
        if (filters != null) {
            includeJar.addAll(filters);
        }

        return this
    }

    public AspectJExtension includeAspectsFromJar(String... filters) {
        if (filters != null) {
            includeAspectsFromJar.addAll(filters);
        }

        return this
    }
}