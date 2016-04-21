package com.archinamon;

public class AndroidAspectJExtension {

    def boolean binaryWeave = false;
    def String binaryExclude = "";

    def boolean weaveInfo = true;
    def boolean ignoreErrors = false;
    def boolean addSerialVersionUID = false;
    def String logFileName = "ajc-details.log";

    def boolean interruptOnWarnings = true;
    def boolean interruptOnErrors = true;
    def boolean interruptOnFails = true;
}