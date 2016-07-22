package com.archinamon

import org.gradle.api.JavaVersion;

public class AspectJExtension {

    def String ajc = "1.8.9";

    List<String> includeJarFilter = new ArrayList<String>();
    List<String> excludeJarFilter = new ArrayList<String>();

    def boolean weaveInfo = true;
    def boolean addSerialVersionUID = false;
    def boolean noInlineAround = false;

    def JavaVersion javaVersion = JavaVersion.VERSION_1_7;

    def String logFileName = "ajc-details.log";

    public AspectJExtension includeJarFilter(String...filters) {
        if (filters != null) {
            includeJarFilter.addAll(filters);
        }

        return this
    }

    public AspectJExtension excludeJarFilter(String...filters) {
        if (filters != null) {
            excludeJarFilter.addAll(filters);
        }

        return this
    }
}