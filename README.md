# GradleAspectJ-Android
[![Android Arsenal](https://img.shields.io/badge/Android%20Arsenal-AspectJ%20Gradle-brightgreen.svg?style=flat)](http://android-arsenal.com/details/1/4578) ![contributions welcome](https://img.shields.io/badge/contributions-welcome-brightgreen.svg) [![](https://jitpack.io/v/Archinamon/GradleAspectJ-Android.svg)](https://jitpack.io/#Archinamon/GradleAspectJ-Android)

A Gradle plugin which enables AspectJ for Android builds.
Supports writing code with AspectJ-lang in `.aj` files and in java-annotation style.
Full support of Android product flavors and build types.
Support Kotlin, Groovy, Scala and any other languages that compiles into java bytecode.

Actual version: `com.archinamon:android-gradle-aspectj:2.3.0`.
Re-written with brand new <a href="http://tools.android.com/tech-docs/new-build-system/transform-api" target="_blank">Transform API</a>!

This plugin is completely friendly with <a href="https://bitbucket.org/hvisser/android-apt" target="_blank">APT</a> (Android Annotation Processing Tools) and <a href="https://github.com/evant/gradle-retrolambda/" target="_blank">Retrolambda</a> project (but Java 8 not supported in .aj files).
<a href="https://github.com/excilys/androidannotations" target="_blank">AndroidAnnotations</a>, <a href="https://github.com/square/dagger" target="_blank">Dagger</a> are also supported and works fine.

This plugin has many ideas from the others similar projects, but no one of them grants full pack of features like this one.
Nowdays it has been completely re-written using Transform API.

Key features
-----

Augments Java, Kotlin, Groovy bytecode simultaneously!<br />
Works with background mechanics of jvm-based languages out-of-box!<br />
[How to teach Android Studio to understand the AspectJ!](IDE)

It is easy to isolate your code with aspect classes, that will be simply injected via cross-point functions, named `advices`, into your core application. The main idea is — code less, do more!

AspectJ-Gradle plugin provides supply of all known JVM-based languages, such as Groovy, Kotlin, etc. That means you can easily write cool stuff which may be inject into any JVM language, not only Java itself! :)

To start from you may look at my <a href="https://github.com/Archinamon/AspectJExampleAndroid" target="_blank">example project</a>. And also you may find useful to look at <a href="https://eclipse.org/aspectj/doc/next/quick5.pdf" target="_blank">reference manual</a> of AspectJ language and simple <a href="https://eclipse.org/aspectj/sample-code.html" target="_blank">code snipets</a>. In case aspectj-native not supported by Android Studio (even with IDE-plugin it's using is complicated), you may write a java-classes with aspectj annotations.

Two simple rules you may consider when writing aspect classes.
- Do not write aspects outside the `src/$flavor/aspectj` source set! These aj-classes will be excluded from java compiler.
- Do not try to access aspect classes from java/kotlin/etc. In case java compiler doesn't know anything about aspectj, it will lead to compile errors on javac step.

These rules affects only in case you're writing in native aj-syntax.
You may write aspects in java-annotation style and being free from these limitations.

Usage
-----

First add a maven repo link into your `repositories` block of module build file:
```groovy
mavenCentral()
maven { url "https://jitpack.io" }
```
Don't forget to add `mavenCentral()` due to some dependencies inside AspectJ-gradle module.

Add the plugin to your `buildscript`'s `dependencies` section:
```groovy
classpath 'com.github.Archinamon:GradleAspectJ-Android:2.3.0'
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
        nmng.notify(9999, new NotificationCompat.Builder(app)
                              .setTicker("Hello AspectJ")
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
    ajc '1.8.9'

    includeJar 'design', 'support-v4', 'dagger'
    includeAspectsFromJar 'my-aj-logger-lib', 'any-other-libs-with-aspects'
    ajcExtraArgs << '-referenceInfo' << '-warn:deprecation'

    weaveInfo true
    debugInfo false
    addSerialVersionUID false
    noInlineAround false
    ignoreErrors false
    
    breakOnError true
    experimental false

    logFileName 'ajc-details.log'
}
```

- `ajc` Allows to define the aspectj runtime jar version manually (1.8.9 current)

- `includeJar` Name filter to include any jar/aar which name or path satisfies the filter
- `includeAspectsFromJar` Name filter to include any jar/aar with compiled binary aspects you wanna affect your project
- `ajcExtraArgs` Additional parameters for aspectj compiler

- `weaveInfo` Enables printing info messages from Aj compiler
- `debugInfo` Adds special debug info in aspect's bytecode
- `addSerialVersionUID` Adds serialVersionUID field for Serializable-implemented aspect classes
- `noInlineAround` Strict ajc to inline around advice's body into the target methods
- `ignoreErrors` Prevent compiler from aborting if errors occurrs during processing the sources

- `breakOnError` Allows to continue project building when ajc fails or throws any errors
- `experimental` Enables experimental ajc options: `-XhasMember` and `-Xjoinpoints:synchronization,arrayconstruction`. More details in <a href="https://github.com/Archinamon/GradleAspectJ-Android/issues/18" target="_blank">issue #18</a>

- `logFileName` Defines name for the log file where all Aj compiler info writes to

Working tests
-------
Just write a test and run them! If any errors occurrs please write an issue :)

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
---------
#### 2.3.0 -- Major fixes
* InstantRun support;
* fails androidTest hot launch;
* ZipException within augmenting third party libraries via AspectJ;
* more clear logging and errors emitting;

#### 2.2.2 -- Improvements
* fixed build config namings;
* re-designed work with log file and errors handling;
* pretty formatting ajc arguments for build stdout;
* implemented handling custom ajc arguments via build.gradle config;

#### 2.2.1 -- Hot-fix
* fixed illegal 'return' statement;
* change included in `updated` 2.2.0 artifacts;

#### 2.2.0 -- Ajc fixes and improvements
* fixed problem with -aspectPath building project with multidex;
* fixed scope problems with Transform API;
* removed Java 8 support;
* implemented clear and easy way to attach compiled aspects via jar/aar;
* implemented more easy way to weave by aspects any library (jar/aar);
* implemented breaking build on errors occurring to prevent runtime issues;
* implemented ajc experimental features: -XhasMember and -Xjoinpoints:synchronization,arrayconstruction;
* implemented more logic way to detect the plugin placement in build file to support retrolambda correctly;
* code cleanups and improvements;

#### 2.1.0 -- Transform api fix
* finally fixed errors with multidex;
* fixed jar merge errors;
* fixed errors with new gradle plugin;
* fixed Java 8 support;
* fixed Retrolambda compatibility;

#### 2.0.4 -- Small fix
* fixed error with mandatory default aj-directory;

#### 2.0.3 -- Gradle instant run
* merged pull request with the latest gradle plugin update;
* fixed errors after update;

#### 2.0.2 -- Fixed filters
* problem with empty filters now fixed;

#### 2.0.1 -- Hotfix :)
* proper scan of productFlavors and buildTypes folders for aj source sets;
* more complex selecting aj sources to compile;
* more precise work with jars;
* changed jar filter policy;
* optimized weave flags;

#### 2.0.0 -- Brand new mechanics
* full refactor on Transform API;
* added new options to aspectj-extension;

#### 1.3.3 -- Rt qualifier
* added external runtime version qualifier;

#### 1.3.2 -- One more fix
* now correctly sets destinationDir;

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
* You can't speak with sources in aspectj folder due to excluding it from java compiler;
* Doesn't support gradle-experimental plugin;

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
