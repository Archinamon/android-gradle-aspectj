# GradleAspectJ-Android

A Gradle plugin which enables AspectJ for Android builds.
Supports writing code with AspectJ-lang in `.aj` files which then builds into annotated java class.
Compilation order:
```groovy
  if (hasRetrolambda)
    retrolambdaTask.dependsOn(aspectCompileTask)
  else
    javaComplieTask.finalizedBy(aspectCompileTask)
```
This workaround is friendly with <a href="https://bitbucket.org/hvisser/android-apt" target="_blank">APT</a> (Android Pre-Processing Tools) and <a href="https://github.com/evant/gradle-retrolambda/" target="_blank">Retrolambda</a> project.
<a href="https://github.com/excilys/androidannotations" target="_blank">AndroidAnnotations</a>, <a href="https://github.com/square/dagger" target="_blank">Dagger</a>, <a href="https://github.com/JakeWharton/butterknife" target="_blank">Butterknife</a> are also supported and works fine.

This plugin based on <a href="https://github.com/uPhyca/gradle-android-aspectj-plugin/" target="_blank">uPhyca's plugin</a>.

Usage
-----

First add a maven repo link into your `repositories` block of module build file:
```groovy
maven { url 'https://github.com/Archinamon/GradleAspectJ-Android/raw/master' }
```

Add the plugin to your `buildscript`'s `dependencies` section:
```groovy
classpath 'org.fxclub.aspectj:AspectJ-gradle:1.0.15'
```

Apply the `aspectj` plugin:
```groovy
apply plugin: 'org.fxclub.aspectj'
```

Now you can write aspects using annotation style or native (even without IntelliJ IDEA Ultimate edition).
Let's write simple Application advice:
```java
import android.app.Application;
import android.app.NotificationManager;
import android.content.Context;
import android.support.v4.app.NotificationCompat;

privileged aspect AppAdvice {

    pointcut preInit(): within(Application) && execution(* Application.onCreate());

    after() returning: preInit() {
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

Changelog
-------

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
