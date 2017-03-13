package com.archinamon
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Test

class AndroidAspectJTest {
    @Test
    public void pluginDetectsAppPlugin() {
        Project project = ProjectBuilder.builder().build();
        project.apply plugin: 'com.android.application'
        project.apply plugin: 'com.archinamon.aspectj'
    }

    @Test
    public void pluginDetectsLibraryPlugin() {
        Project project = ProjectBuilder.builder().build();
        project.apply plugin: 'com.android.library'
        project.apply plugin: 'com.archinamon.aspectj'
    }

    @Test(expected = GradleException)
    public void pluginFailsWithoutAndroidPlugin() {
        Project project = ProjectBuilder.builder().build();
        project.apply plugin: 'com.archinamon.aspectj'
    }
}
