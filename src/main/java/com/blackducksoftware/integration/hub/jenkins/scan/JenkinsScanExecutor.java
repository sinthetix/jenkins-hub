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
package com.blackducksoftware.integration.hub.jenkins.scan;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.blackducksoftware.integration.hub.HubSupportHelper;
import com.blackducksoftware.integration.hub.ScanExecutor;
import com.blackducksoftware.integration.hub.ScannerSplitStream;
import com.blackducksoftware.integration.hub.exception.HubIntegrationException;
import com.blackducksoftware.integration.hub.jenkins.HubJenkinsLogger;
import com.blackducksoftware.integration.hub.jenkins.HubServerInfo;

import hudson.FilePath;
import hudson.Launcher;
import hudson.Launcher.ProcStarter;
import hudson.model.AbstractBuild;
import hudson.model.TaskListener;

public class JenkinsScanExecutor extends ScanExecutor {
    public static final Integer THREAD_SLEEP = 100;

    private final AbstractBuild<?, ?> build;

    private final Launcher launcher;

    private final TaskListener listener;

    public JenkinsScanExecutor(final HubServerInfo serverInfo, final List<String> scanTargets, final Integer buildNumber, final HubSupportHelper supportHelper,
            final AbstractBuild<?, ?> build, final Launcher launcher, final TaskListener listener) {
        super(serverInfo.getServerUrl(), serverInfo.getUsername(), serverInfo.getPassword(), scanTargets, buildNumber, supportHelper);
        this.build = build;
        this.launcher = launcher;
        this.listener = listener;
    }

    @Override
    protected boolean isConfiguredCorrectly(final String scanExec, final String oneJarPath, final String javaExec) {
        if (getLogger() == null) {
            System.out.println("Could not find a logger");
            return false;
        }
        try {

            if (scanExec == null) {
                getLogger().error("Please provide the Hub scan CLI.");
                return false;
            }
            else {
                final FilePath scanExecRemote = new FilePath(build.getBuiltOn().getChannel(), scanExec);
                if (!scanExecRemote.exists()) {
                    getLogger().error("The Hub scan CLI provided does not exist.");
                    return false;
                }
            }
            if (oneJarPath == null) {
                getLogger().error("Please provide the path for the CLI cache.");
                return false;
            }
            if (javaExec == null) {
                getLogger().error("Please provide the java home directory.");
                return false;
            }
            else {
                final FilePath javaExecRemote = new FilePath(build.getBuiltOn().getChannel(), javaExec);
                if (!javaExecRemote.exists()) {
                    getLogger().error("The Java executable provided does not exist.");
                    return false;
                }
            }
            if (getScanMemory() <= 0) {
                getLogger().error("No memory set for the HUB CLI. Will use the default memory, " + DEFAULT_MEMORY);
                setScanMemory(DEFAULT_MEMORY);
            }
        } catch (final IOException e) {
            getLogger().error(e.toString(), e);
            return false;
        } catch (final InterruptedException e) {
            getLogger().error(e.toString(), e);
            return false;
        }
        return true;
    }

    @Override
    protected String getLogDirectoryPath() throws IOException {
        FilePath logDirectory = new FilePath(build.getBuiltOn().getChannel(), getWorkingDirectory());
        logDirectory = new FilePath(logDirectory, "HubScanLogs");
        logDirectory = new FilePath(logDirectory, String.valueOf(getBuildNumber()));
        // This log directory should never exist as a new one is created for each Build
        try {
            logDirectory.mkdirs();
        } catch (final InterruptedException e) {
            getLogger().error("Could not create the log directory : " + e.getMessage(), e);
        }

        return logDirectory.getRemote();
    }

    /**
     * Should determine the path to the scan status directory within the log directory.
     * This should only be used outside of this class to get the path of the satus directory
     *
     */
    @Override
    public String getScanStatusDirectoryPath() throws IOException {
        final FilePath logDirectory = new FilePath(build.getBuiltOn().getChannel(), getLogDirectoryPath());
        final FilePath scanStatusDirectory = new FilePath(logDirectory, "status");
        return scanStatusDirectory.getRemote();
    }

    @Override
    protected Result executeScan(final List<String> cmd, final String logDirectoryPath) throws HubIntegrationException, InterruptedException {
        try {
            final FilePath logBaseDirectory = new FilePath(build.getBuiltOn().getChannel(), getLogDirectoryPath());
            logBaseDirectory.mkdirs();
            final FilePath standardOutFile = new FilePath(logBaseDirectory, "CLI_Output.txt");
            standardOutFile.touch(0);
            final ProcStarter ps = launcher.launch();
            int exitCode = 0;
            if (ps != null) {
                // ////////////////////// Code to mask the password in the logs
                final ArrayList<Integer> indexToMask = new ArrayList<Integer>();
                // The User's password will be at the next index
                indexToMask.add(cmd.indexOf("--password") + 1);

                for (int i = 0; i < cmd.size(); i++) {
                    if (cmd.get(i).contains("-Dhttp") && cmd.get(i).contains("proxyPassword")) {
                        indexToMask.add(i);
                    }
                }
                final boolean[] masks = new boolean[cmd.size()];
                Arrays.fill(masks, false);

                for (final Integer index : indexToMask) {
                    masks[index] = true;
                }
                ps.masks(masks);
                // ///////////////////////
                ps.envs(build.getEnvironment(listener));

                final ScannerSplitStream splitStream = new ScannerSplitStream(new HubJenkinsLogger(listener), standardOutFile.write());

                exitCode = runScan(ps, cmd, splitStream);
                splitStream.flush();
                splitStream.close();

                if (logDirectoryPath != null) {
                    final FilePath logDirectory = new FilePath(build.getBuiltOn().getChannel(), logDirectoryPath);
                    if (logDirectory.exists()) {

                        getLogger().info(
                                "You can view the BlackDuck scan CLI logs at : '" + logDirectory.getRemote()
                                        + "'");
                        getLogger().info("");
                    }
                }

                if (exitCode == 0) {
                    return Result.SUCCESS;
                } else {
                    return Result.FAILURE;
                }
            } else {
                getLogger().error("Could not find a ProcStarter to run the process!");
            }
        } catch (final MalformedURLException e) {
            throw new HubIntegrationException("The server URL provided was not a valid", e);
        } catch (final IOException e) {
            throw new HubIntegrationException(e.getMessage(), e);
        } catch (final InterruptedException e) {
            throw new HubIntegrationException(e.getMessage(), e);
        }
        return Result.SUCCESS;
    }

    private int runScan(final ProcStarter ps, final List<String> cmd, final ScannerSplitStream splitStream) throws IOException, InterruptedException {
        ps.cmds(cmd);

        ps.stdout(splitStream);

        return ps.join();
    }
}
