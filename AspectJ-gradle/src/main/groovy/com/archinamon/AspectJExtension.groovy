package com.archinamon;

public class AspectJExtension {

    def boolean binaryWeave = false;
    def boolean weaveTests = false; //experimental option
    def String exclude = "";

    def boolean weaveInfo = true;
    def boolean ignoreErrors = false;
    def boolean addSerialVersionUID = false;
    def String logFileName = "ajc-details.log";

    def boolean interruptOnWarnings = true;
    def boolean interruptOnErrors = true;
    def boolean interruptOnFails = true;
}