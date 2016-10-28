package com.archinamon

public class AspectJExtension {

    def String ajc = "1.8.9";

    def boolean defaultIncludeAllJars = false;
    List<String> includeJarFilter = new ArrayList<String>();
    List<String> excludeJarFilter = new ArrayList<String>();

    def boolean weaveInfo = true;
    def boolean addSerialVersionUID = false;
    def boolean noInlineAround = false;
    def boolean ignoreErrors = false;

    def String logFileName = "ajc-details.log";

    public AspectJExtension includeJarFilter(String...filters) {
        if (filters != null) {
//            todo: not supported now
//            includeJarFilter.addAll(filters);
        }

        return this
    }

    public AspectJExtension excludeJarFilter(String...filters) {
        if (filters != null) {
//            excludeJarFilter.addAll(filters);
        }

        return this
    }
}