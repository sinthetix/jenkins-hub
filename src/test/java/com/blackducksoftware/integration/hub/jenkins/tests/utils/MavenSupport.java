/*******************************************************************************
 * Copyright (C) 2016 Black Duck Software, Inc.
 * http://www.blackducksoftware.com/
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License version 2 only
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License version 2
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *******************************************************************************/
package com.blackducksoftware.integration.hub.jenkins.tests.utils;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import hudson.maven.MavenModuleSet;
import hudson.model.FreeStyleProject;
import hudson.tasks.Maven;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

public class MavenSupport {

    private MavenSupport() {

    }

    public static void addMavenBuilder(FreeStyleProject project, String pomPath) throws Exception {

        Maven.MavenInstallation mvn = getMavenInstallation();

        // Maven.MavenInstallation mvn = configureMaven3();
        Maven builder = new Maven("dependency:resolve", "default", pomPath, null, null);
        builder.getDescriptor().setInstallations(mvn);

        project.getBuildersList().add(builder);
    }

    public static Maven.MavenInstallation getMavenInstallation() throws Exception {
        StringBuffer output = new StringBuffer();
        Process p;
        try {
            p = Runtime.getRuntime().exec("which mvn");
            p.waitFor();
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = null;
            while ((line = reader.readLine()) != null) {
                output.append(line + "\n");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        File mvnHomeDir = null;
        if (output.toString().trim().equals("")) {
            if (System.getenv("M2_HOME") != null) {
                output.append(System.getenv("M2_HOME")).append("/bin/mvn");
                mvnHomeDir = new File(System.getenv("M2_HOME"));
            }
        } else {
            File mvnCommand = new File(output.toString().trim());
            mvnHomeDir = new File(mvnCommand.getCanonicalFile().getParentFile().getParent());
        }
        assertNotNull("Please set your M2_HOME, mvnHomeDir is null", mvnHomeDir);
        assertTrue("Please set your M2_HOME, as maven is not been found " + mvnHomeDir.getCanonicalPath(), mvnHomeDir.exists());

        return new Maven.MavenInstallation("default", mvnHomeDir.getCanonicalPath(), null);
    }

    public static void addMavenToModuleSet(MavenModuleSet project, Maven.MavenInstallation mavenToAdd) throws Exception {
        Maven.MavenInstallation[] installations = new Maven.MavenInstallation[1];
        installations[0] = mavenToAdd;
        project.getDescriptor().getMavenDescriptor().setInstallations(installations);
    }
}
