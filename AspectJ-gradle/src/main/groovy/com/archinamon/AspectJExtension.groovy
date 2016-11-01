package com.archinamon

public class AspectJExtension {

    def String ajc = "1.8.9";

    List<String> includeJar = new ArrayList<String>();
    List<String> includeAspectsFromJar = new ArrayList<String>();
    List<String> ajcExtraArgs = new ArrayList<String>();

    def boolean weaveInfo = true;
    def boolean debugInfo = false;
    def boolean addSerialVersionUID = false;
    def boolean noInlineAround = false;
    def boolean ignoreErrors = false;

    def boolean breakOnError = true;
    def boolean experimental = false;

    def String logFileName = "ajc-details.log";

    public AspectJExtension ajcExtraArgs(String... args) {
        if (args != null) {
            ajcExtraArgs.addAll(args);
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