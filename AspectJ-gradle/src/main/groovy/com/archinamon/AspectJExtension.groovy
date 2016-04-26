package com.archinamon;

public class AspectJExtension {

    def boolean binaryWeave = false;
    def String binaryExclude = "";

    def boolean weaveInfo = true;
    def boolean ignoreErrors = false;
    def boolean addSerialVersionUID = false;
    def String logFileName = "ajc-details.log";

    def boolean interruptOnWarnings = true;
    def boolean interruptOnErrors = true;
    def boolean interruptOnFails = true;

    def boolean execTestOnAjc = false;

    def static findCurrentJdk() {
        String javaHomeProp = System.properties.'java.home'
        if (javaHomeProp) {
            int jreIndex = javaHomeProp.lastIndexOf("${File.separator}jre")
            if (jreIndex != -1) {
                return javaHomeProp.substring(0, jreIndex)
            } else {
                return javaHomeProp
            }
        } else {
            return System.getenv("JAVA_HOME");
        }
    }
}