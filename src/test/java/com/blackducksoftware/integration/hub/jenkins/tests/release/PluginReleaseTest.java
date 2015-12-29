package com.blackducksoftware.integration.hub.jenkins.tests.release;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.junit.Test;

public class PluginReleaseTest {

    @Test
    public void testProperties() throws Exception {
        Properties pluginProperties = new Properties();
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        InputStream is = classLoader.getResourceAsStream("updateSite.properties");
        try {
            pluginProperties.load(is);
        } catch (IOException e) {
            fail(e.toString());
        }

        Properties releaseProperties = new Properties();
        File releasePropertiesFile = new File("deployment.properties");
        System.out.println(releasePropertiesFile.getCanonicalPath());
        InputStream inputStream = new FileInputStream(releasePropertiesFile);
        // InputStream is = classLoader.getResourceAsStream("deployment.properties");
        try {
            releaseProperties.load(inputStream);
        } catch (IOException e) {
            fail(e.toString());
        }

        assertEquals(releaseProperties.getProperty("update.site.url"), pluginProperties.getProperty("hub.update.site.url"));
    }
}
