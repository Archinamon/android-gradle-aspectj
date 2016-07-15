# GradleAspectJ-Android

A Gradle plugin which enables AspectJ for Android builds.
Supports writing code with AspectJ-lang in `.aj` files which then builds into annotated java class.
Full support of Android product flavors and build types.

Actual version: `com.archinamon:android-gradle-aspectj:1.3.2`.

Compilation order:
```groovy
  if (hasRetrolambda)
    retrolambdaTask.dependsOn(aspectCompileTask)
  else
    javaComplieTask.finalizedBy(aspectCompileTask)
```
This workaround is friendly with <a href="https://bitbucket.org/hvisser/android-apt" target="_blank">APT</a> (Android Annotation Processing Tools) and <a href="https://github.com/evant/gradle-retrolambda/" target="_blank">Retrolambda</a> project.
<a href="https://github.com/excilys/androidannotations" target="_blank">AndroidAnnotations</a>, <a href="https://github.com/square/dagger" target="_blank">Dagger</a> are also supported and works fine.
<a href="https://github.com/JakeWharton/butterknife" target="_blank">Butterknife</a> now doesn't support ad could works with bugs and errors. WIP on that problem.

This plugin was based on <a href="https://github.com/uPhyca/gradle-android-aspectj-plugin/" target="_blank">uPhyca's plugin</a>. Nowdays my plugin has completely re-written code base.

Key features
-----

It is easy to isolate your code with aspect classes, that will be simply injected via cross-point functions, named `advices`, into your core application. The main idea is — code less, do more!

AspectJ-Gradle plugin provides supply of all known JVM-based languages, such as Groovy, Kotlin, etc. That means you can easily write cool isolated stuff which may be inject into any JVM language, not only Java itself! :)

To start from you may look at my <a href="https://github.com/Archinamon/AspectJExampleAndroid" target="_blank">example project</a>. And also you may find useful to look at <a href="https://eclipse.org/aspectj/doc/next/quick5.pdf" target="_blank">reference manual</a> of AspectJ language and simple <a href="https://eclipse.org/aspectj/sample-code.html" target="_blank">code snipets</a>.

Usage
-----

First add a maven repo link into your `repositories` block of module build file:
```groovy
mavenCentral()
maven { url 'https://github.com/Archinamon/GradleAspectJ-Android/raw/master' }
```
Don't forget to add `mavenCentral()` due to some dependencies inside AspectJ-gradle module.

Add the plugin to your `buildscript`'s `dependencies` section:
```groovy
classpath 'com.archinamon:android-gradle-aspectj:1.3.1'
```

Apply the `aspectj` plugin:
```groovy
apply plugin: 'com.archinamon.aspectj'
```

Now you can write aspects using annotation style or native (even without IntelliJ IDEA Ultimate edition).
Let's write simple Application advice:
```java
import android.app.Application;
import android.app.NotificationManager;
import android.content.Context;
import android.support.v4.app.NotificationCompat;

aspect AppStartNotifier {

    pointcut postInit(): within(Application) && execution(* Application.onCreate());

    after() returning: postInit() {
        Application app = (Application) thisJoinPoint.getTarget();
        NotificationManager nmng = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
        nmng.notify(9999, new NotificationCompat.Builder(app).setTicker("Hello AspectJ")
                                                             .setContentTitle("Notification from aspectJ")
                                                             .setContentText("privileged aspect AppAdvice")
                                                             .setSmallIcon(R.drawable.ic_launcher)
                                                             .build());
    }
}
```

Tune extension
-------

```groovy
aspectj {
  weaveInfo true
  ignoreErrors false
  addSerialVersionUID false
  logFileName "ajc_details.log"
  
  interruptOnWarnings false
  interruptOnErrors false
  interruptOnFails true
  
  binaryWeave true
  weaveTests true
  exclude "com.example.xpoint"
}
```

- `weaveInfo` Enables printing info messages from Aj compiler
- `ignoreErrors` Prevent compiler from aborting if errors occurrs during processing the sources
- `addSerialVersionUID` Adds serialVersionUID field for Serializable-implemented aspect classes
- `logFileName` Defines name for the log file where all Aj compiler info writes to
- `interruptOn[level]` Defines compiler to abort execution if any message of defined level type throws
- `binaryWeave` Enables processing jvm-based languages, like Kotlin, Groovy. Read more about binary weaving in corresponding paragraph
- `weaveTests` Depands on binary processing and allows it for test flavours. That is an experimental option and may work incorrectly. Please, report if any abnormal behaviour occurrs
- `exclude` This option should be defined if binary processing enabled. You should define here all packages separated by coma, where aspectj source code is located. Please, be careful and not mix aj and jvm languages code in the same packages, because these packages will be excluded from final processing within test flavours and binary processing step

Working tests
-------
To work properly with test flavours you have to follow a few steps.
First of all: do not write aspectj code inside the test flavours themself. Use aspects to process over production code only and test this cases by allowing aspects to process test-flavour code with general condition from within production code.
Second is: try to avoid binary processing within test flavours due to some abnormal conditions may occurrs.

The compilation flow also operates over built bytecode files. Ajc removes packages from `exclude` specified param to avoid mixing source files on dexTransform build step while deploying apk file.

ProGuard
-------
Correct tuning will depends on your own usage of aspect classes. So if you declares inter-type injections you'll have to predict side-effects and define your annotations/interfaces which you inject into java classes/methods/etc. in proguard config.

Basic rules you'll need to declare for your project:
```
-adaptclassstrings
-keepattributes InnerClasses, EnclosingMethod, Signature, *Annotation*

-keepnames @org.aspectj.lang.annotation.Aspect class * {
    ajc* <methods>;
}
```

If you will face problems with lambda factories, you may need to explicitely suppress them. That could happen not in aspect classes but in any arbitrary java-class if you're using Retrolambda.
So concrete rule is:
```
-keep class *$Lambda* { <methods>; }
-keepclassmembernames public class * {
    *** lambda*(...);
}
```

Changelog
-------
#### 1.3.3 -- Rt qualifier
* added external runtime version qualifier;

#### 1.3.2 -- One more fix
* now correctly sets destinationDir

#### 1.3.1 -- Hot-fixes
* changed module name from `AspectJ-gradle` to `android-gradle-aspectj`;
* fixed couple of problems with test flavours processing;
* added experimental option: `weaveTests`;
* added finally post-compile processing for tests;

#### 1.3.0 -- Merging binary processing and tests
* enables binary processing for test flavours;
* properly aspectpath and after-compile source processing for test flavours;
* corresponding sources processing between application modules;

#### 1.2.1 -- Hot-fix of Gradle DSL
* removed unnecessary parameters from aspectj-extension class;
* fixed gradle dsl-model;

#### 1.2.0 -- Binary weaving
* plugin now supports processing .class files;
* supporting jvm languages — Kotlin, Groovy, Scala;
* updated internal aj-tools and aj runtime to the newest 1.8.9;

#### 1.1.4 -- Experimenting with binary weaving
* implementing processing aars/jars;
* added excluding of aj-source folders to avoid aspects re-compiling;

#### 1.1.2 -- Gradle Instant-run
* now supports gradle-2.0.0-beta plugin and friendly with slicer task;
* fixed errors within collecting source folders;
* fixed mixing buildTypes source sets;

#### 1.1.1 -- Updating kernel
* AspectJ-runtime module has been updated to the newest 1.8.8 version;
* fixed plugin test;

#### 1.1.0 -- Refactoring
* includes all previous progress;
* updated aspectjtools and aspectjrt to 1.8.7 version;
* now has extension configuration;
* all logging moved to the separate file in `app/build/ajc_details.log`;
* logging, log file name, error ignoring now could be tuned within the extension;
* more complex and correct way to detect and inject source sets for flavors, buildTypes, etc;

#### 1.0.17 -- Cleanup
* !!IMPORTANT!! now corectly supports automatically indexing and attaching aspectj sources within any buildTypes and flavors;
* workspace code refactored;
* removed unnecessary logging calls;
* optimized ajc logging to provide more info about ongoing compilation;

#### 1.0.16 -- New plugin routes
* migrating from corp to personal routes within plugin name, classpath;

#### 1.0.15 -- Full flavor support
* added full support of buld variants within flavors and dimensions;
* added custom source root folder -- e.g. `src/main/aspectj/path.to.package.Aspect.aj`;

#### 1.0.9 -- Basic flavors support
* added basic support of additional build varians and flavors;
* trying to add incremental build //was removed due to current implementation of ajc-task;

#### 1.0 -- Initial release
* configured properly compile-order for gradle-Retrolambda plugin;
* added roots for preprocessing generated files (needed to support Dagger, etc.);
* added MultiDex support;
 
#### Known limitations
* You can't speak with native aspects from java — this case won't be fixed due to android's compile sequence rules;
* Doesn't support gradle-experimental plugin;
* UnitTest variants doesn't properly compiled under Retrolambda plugin due to <a href="https://github.com/evant/gradle-retrolambda/pull/185" target="_blank">known RL bug</a>;

All these limits are fighting on and I'll be glad to introduce new build as soon as I solve these problems.

License
-------

    Copyright 2015 Eduard "Archinamon" Matsukov.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
