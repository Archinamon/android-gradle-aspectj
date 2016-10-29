package com.archinamon

public class AspectJExtension {

    def String ajc = "1.8.9";

    List<String> includeJarFilter = new ArrayList<String>();
    List<String> binaryAspectsFilter = new ArrayList<String>();

    def boolean weaveInfo = true;
    def boolean debugInfo = false;
    def boolean addSerialVersionUID = false;
    def boolean noInlineAround = false;
    def boolean ignoreErrors = false;

    def boolean experimental = false;

    def String logFileName = "ajc-details.log";

    public AspectJExtension includeJar(String...filters) {
        if (filters != null) {
            includeJarFilter.addAll(filters);
        }

        return this
    }

    public AspectJExtension includeAspectsFromJar(String...filters) {
        if (filters != null) {
            binaryAspectsFilter.addAll(filters);
        }

        return this
    }
}