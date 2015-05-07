package com.blackducksoftware.integration.hub.jenkins;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.ProxyConfiguration;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.model.AbstractBuild;
import hudson.model.JDK;
import hudson.model.Node;
import hudson.remoting.VirtualChannel;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Recorder;
import hudson.tools.ToolDescriptor;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import jenkins.model.Jenkins;

import org.codehaus.plexus.util.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.restlet.data.Status;

import com.blackducksoftware.integration.hub.jenkins.exceptions.BDJenkinsHubPluginException;
import com.blackducksoftware.integration.hub.jenkins.exceptions.BDRestException;
import com.blackducksoftware.integration.hub.jenkins.exceptions.HubConfigurationException;
import com.blackducksoftware.integration.hub.jenkins.exceptions.IScanToolMissingException;

public class PostBuildHubScan extends Recorder {

    public static final int DEFAULT_MEMORY = 4096;

    private final ScanJobs[] scans;

    private final String scanName;

    private final String hubProjectName;

    private final String hubVersionPhase;

    private final String hubVersionDist;

    private String hubProjectVersion;

    // Old variable, renaming to hubProjectVersion
    // need to keep this around for now for migration purposes
    private String hubProjectRelease;

    private Integer scanMemory;

    private String workingDirectory;

    private JDK java;

    private Result result;

    // private JenkinsHubIntRestService service = null;

    private boolean test = false;

    @DataBoundConstructor
    public PostBuildHubScan(ScanJobs[] scans, String scanName, String hubProjectName, String hubProjectVersion, String hubVersionPhase, String hubVersionDist,
            String scanMemory) {
        this.scans = scans;
        this.scanName = scanName;
        this.hubProjectName = hubProjectName;
        this.hubProjectVersion = hubProjectVersion;
        this.hubVersionPhase = hubVersionPhase;
        this.hubVersionDist = hubVersionDist;
        Integer memory = 0;
        try {
            memory = Integer.valueOf(scanMemory);
        } catch (NumberFormatException e) {
            // return FormValidation.error(Messages
            // .HubBuildScan_getInvalidMemoryString());
        }

        if (memory == 0) {
            this.scanMemory = DEFAULT_MEMORY;
        } else {
            this.scanMemory = memory;
        }
    }

    public boolean isTEST() {
        return test;
    }

    // Set to true run the integration test without running the actual iScan.
    public void setTEST(boolean tEST) {
        test = tEST;
    }

    public Result getResult() {
        return result;
    }

    private void setResult(Result result) {
        this.result = result;
    }

    public String geDefaultMemory() {
        return String.valueOf(DEFAULT_MEMORY);
    }

    public String getScanMemory() {
        if (scanMemory == 0) {
            scanMemory = DEFAULT_MEMORY;
        }
        return String.valueOf(scanMemory);
    }

    public String getHubProjectVersion() {
        if (hubProjectVersion == null && hubProjectRelease != null) {
            hubProjectVersion = hubProjectRelease;
        }
        return hubProjectVersion;
    }

    public String getHubProjectName() {
        return hubProjectName;
    }

    public String getHubVersionPhase() {
        return hubVersionPhase;
    }

    public String getHubVersionDist() {
        return hubVersionDist;
    }

    public ScanJobs[] getScans() {
        return scans;
    }

    public String getScanName() {
        return scanName;
    }

    public String getWorkingDirectory() {
        return workingDirectory;
    }

    private void setWorkingDirectory(String workingDirectory) {
        if (!workingDirectory.startsWith("\\") && !workingDirectory.startsWith("/")) {
            workingDirectory = "/" + workingDirectory;
            // Need to do this because of the windows issue, IJH-64
        }
        workingDirectory = workingDirectory.replace("\\", "/"); // IJH-64

        this.workingDirectory = workingDirectory;
    }

    // http://javadoc.jenkins-ci.org/hudson/tasks/Recorder.html
    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.BUILD;
    }

    @Override
    public PostBuildScanDescriptor getDescriptor() {
        return (PostBuildScanDescriptor) super.getDescriptor();
    }

    /**
     * Overrides the Recorder perform method. This is the method that gets called by Jenkins to run as a Post Build
     * Action
     *
     * @param build
     *            AbstractBuild
     * @param launcher
     *            Launcher
     * @param listener
     *            BuildListener
     *
     * @throws IOException
     * @throws InterruptedException
     */
    @Override
    public boolean perform(AbstractBuild build, Launcher launcher,
            BuildListener listener) throws InterruptedException, IOException {
        setResult(build.getResult());
        if (result.equals(Result.SUCCESS)) {
            try {
                listener.getLogger().println("Starting BlackDuck Scans...");

                String localHostName = "";
                try {
                    localHostName = build.getBuiltOn().getChannel().call(new GetHostName());
                } catch (IOException e) {
                    listener.error("Problem getting the Local Host name : " + e.getMessage());
                    e.printStackTrace(listener.getLogger());
                }
                listener.getLogger().println("Hub Plugin running on machine : " + localHostName);

                ScanInstallation[] iScanTools = null;
                ToolDescriptor<ScanInstallation> iScanDescriptor = (ToolDescriptor<ScanInstallation>) build.getDescriptorByName(ScanInstallation.class
                        .getSimpleName());
                iScanTools = iScanDescriptor.getInstallations();
                // installations?
                if (validateConfiguration(iScanTools, getScans())) {
                    // This set the base of the scan Target, DO NOT remove this or the user will be able to specify any
                    // file even outside of the Jenkins directories
                    File workspace = null;
                    if (build.getWorkspace() == null) {
                        // might be using custom workspace
                        workspace = new File(build.getProject().getCustomWorkspace());
                    } else {
                        workspace = new File(build.getWorkspace().getRemote());
                    }
                    String workingDirectory = "";
                    try {
                        workingDirectory = build.getBuiltOn().getChannel().call(new GetCanonicalPath(workspace));
                    } catch (IOException e) {
                        listener.error("Problem getting the working directory on this node. Error : " + e.getMessage());
                        e.printStackTrace(listener.getLogger());
                    }
                    listener.getLogger().println("Node workspace " + workingDirectory);
                    setWorkingDirectory(workingDirectory);
                    setJava(build, listener);
                    EnvVars variables = build.getEnvironment(listener);
                    List<String> scanTargets = new ArrayList<String>();
                    for (ScanJobs scanJob : getScans()) {
                        if (StringUtils.isEmpty(scanJob.getScanTarget())) {
                            scanTargets.add(getWorkingDirectory());
                        } else {
                            // trim the target so there are no false whitespaces at the beginning or end of the target
                            // path
                            String target = handleVariableReplacement(variables, scanJob.getScanTarget().trim());

                            // make sure the target provided doesn't already begin with a slash or end in a slash
                            // removes the slash if the target begins or ends with one
                            if (target.startsWith("/") || target.startsWith("\\")) {
                                target = getWorkingDirectory() + target;
                            } else {
                                target = getWorkingDirectory() + File.separator + target;

                            }
                            if (target.endsWith("/") || target.endsWith("\\")) {
                                target = target.substring(0, target.length() - 1);
                            }

                            target = target.replace("\\", "/"); // IJH-64

                            scanTargets.add(target);
                        }
                    }
                    String projectName = null;
                    String projectVersion = null;

                    if (!StringUtils.isEmpty(getHubProjectName()) && !StringUtils.isEmpty(getHubProjectVersion())) {
                        projectName = handleVariableReplacement(variables, getHubProjectName());
                        projectVersion = handleVariableReplacement(variables, getHubProjectVersion());

                    }

                    printConfiguration(build, listener, projectName, projectVersion, scanTargets);

                    FilePath scanExec = getScanCLI(iScanTools, listener, build);

                    runScan(build, launcher, listener, scanExec, scanTargets);

                    // Only map the scans to a Project Version if the Project name and Project Version have been
                    // configured
                    if (getResult().equals(Result.SUCCESS) && !StringUtils.isEmpty(projectName) && !StringUtils.isEmpty(projectVersion)) {
                        // Wait 5 seconds for the scans to be recognized in the Hub server
                        listener.getLogger().println("Waiting a few seconds for the scans to be recognized by the Hub server.");
                        Thread.sleep(5000);

                        doScanMapping(build, listener, projectName, projectVersion, scanTargets);
                    }
                }
            } catch (BDJenkinsHubPluginException e) {
                listener.error(e.getMessage(), e);
                e.printStackTrace(listener.getLogger());
                setResult(Result.UNSTABLE);
            } catch (Exception e) {
                e.printStackTrace(listener.getLogger());
                String message;
                if (e.getMessage().contains("Project could not be found")) {
                    message = e.getMessage();
                } else {

                    if (e.getCause() != null && e.getCause().getCause() != null) {
                        message = e.getCause().getCause().toString();
                    } else if (e.getCause() != null) {
                        message = e.getCause().toString();
                    } else {
                        message = e.toString();
                    }
                    if (message.toLowerCase().contains("service unavailable")) {
                        message = Messages.HubBuildScan_getCanNotReachThisServer_0_(getDescriptor().getHubServerInfo().getServerUrl());
                    } else if (message.toLowerCase().contains("precondition failed")) {
                        message = message + ", Check your configuration.";
                    }
                }
                listener.error(message);
                setResult(Result.UNSTABLE);
            }
        } else {
            listener.getLogger().println("Build was not successful. Will not run Black Duck Scans.");
        }
        listener.getLogger().println("Finished running Black Duck Scans.");
        build.setResult(getResult());
        return true;
    }

    private void doScanMapping(AbstractBuild build, BuildListener listener, String projectName, String projectVersion, List<String> scanTargets)
            throws IOException, BDRestException,
            BDJenkinsHubPluginException,
            InterruptedException {
        JenkinsHubIntRestService service = setJenkinsHubIntRestService(listener);

        // /////////////////////////////////////////// Handling the Project name and Version
        String projectId = null;
        String versionId = null;

        // This behavior has been updated to match the Protex Jenkins Plugin version 1.x
        // This checks for the Project and Version and if they dont exist then it creates them
        try {
            projectId = service.getProjectId(projectName);

            LinkedHashMap<String, Object> versionMatchesResponse = service.getVersionMatchesForProjectId(projectId);
            versionId = service.getVersionIdFromMatches(versionMatchesResponse, projectVersion, getHubVersionPhase(), getHubVersionDist());
            if (versionId == null) {
                versionId = service.createHubVersion(projectVersion, projectId, getHubVersionPhase(), getHubVersionDist());
                listener.getLogger().println("[DEBUG] Version created!");
            }
        } catch (BDRestException e) {
            if (e.getResource() != null) {
                if (e.getResource().getResponse().getStatus().getCode() == 404) {
                    // Project was not found, try to create it
                    try {

                        projectId = service.createHubProject(projectName);
                        listener.getLogger().println("[DEBUG] Project created!");

                        // We check if the version exists first even though we just created the project
                        // The user might have specified the default version, in which case it already exists
                        LinkedHashMap<String, Object> versionMatchesResponse = service.getVersionMatchesForProjectId(projectId);
                        versionId = service.getVersionIdFromMatches(versionMatchesResponse, projectVersion, getHubVersionPhase(), getHubVersionDist());
                        if (versionId == null) {
                            versionId = service.createHubVersion(projectVersion, projectId, getHubVersionPhase(), getHubVersionDist());
                            listener.getLogger().println("[DEBUG] Version created!");
                        }
                    } catch (BDRestException e1) {
                        if (e1.getResource() != null) {
                            listener.getLogger().println("[ERROR] Status : " + e1.getResource().getStatus().getCode());
                            listener.getLogger().println("[ERROR] Response : " + e1.getResource().getResponse().getEntityAsText());
                        }
                        throw new BDJenkinsHubPluginException("Problem creating the Project or Version. ", e1);
                    }
                } else {
                    if (e.getResource() != null) {
                        listener.getLogger().println("[ERROR] Status : " + e.getResource().getStatus().getCode());
                        listener.getLogger().println("[ERROR] Response : " + e.getResource().getResponse().getEntityAsText());
                    }
                    throw new BDJenkinsHubPluginException("Problem getting the Project Id. ", e);
                }
            }
        }

        if (StringUtils.isEmpty(projectId)) {
            throw new BDJenkinsHubPluginException("The specified Project could not be found.");
        }

        listener.getLogger().println("[DEBUG] Project Id: '" + projectId + "'");

        if (StringUtils.isEmpty(versionId)) {
            throw new BDJenkinsHubPluginException("The specified Version could not be found in the Project.");
        }
        listener.getLogger().println("[DEBUG] Version Id: '" + versionId + "'");
        // /////////////////////////////////////////// END Handling the Project name and Version

        Map<String, Boolean> scanLocationIds = service.getScanLocationIds(build, listener, scanTargets, versionId);
        if (scanLocationIds != null && !scanLocationIds.isEmpty()) {
            listener.getLogger().println("[DEBUG] These scan Id's were found for the scan targets.");
            for (Entry<String, Boolean> scanId : scanLocationIds.entrySet()) {
                listener.getLogger().println(scanId.getKey());
            }

            service.mapScansToProjectVersion(listener, scanLocationIds, versionId);
        } else {
            listener.getLogger()
                    .println(
                            "[DEBUG] There was an issue getting the Scan Location Id's for the defined scan targets.");
        }

    }

    public JenkinsHubIntRestService setJenkinsHubIntRestService(BuildListener listener) throws MalformedURLException {
        JenkinsHubIntRestService service = new JenkinsHubIntRestService();
        service.setListener(listener);
        Jenkins jenkins = Jenkins.getInstance();
        if (jenkins != null) {
            ProxyConfiguration proxy = jenkins.proxy;
            if (proxy != null) {
                service.setNoProxyHosts(proxy.getNoProxyHostPatterns());
                service.setProxyHost(proxy.name);
                service.setProxyPort(proxy.port);
                if (!StringUtils.isEmpty(proxy.name) && proxy.port != 0) {
                    if (listener != null) {
                        listener.getLogger().println("[DEBUG] Using proxy: '" + proxy.name + "' at Port: '" + proxy.port + "'");
                    }
                }
            }
        }
        service.setBaseUrl(getDescriptor().getHubServerInfo().getServerUrl());
        service.setCookies(getDescriptor().getHubServerInfo().getUsername(),
                getDescriptor().getHubServerInfo().getPassword());
        return service;
    }

    /**
     *
     * @param variables
     *            Map of variables
     * @param value
     *            String to check for variables
     * @return the new Value with the variables replaced
     * @throws BDJenkinsHubPluginException
     */
    public String handleVariableReplacement(Map<String, String> variables, String value) throws BDJenkinsHubPluginException {
        if (value != null) {

            String newValue = Util.replaceMacro(value, variables);

            if (newValue.contains("$")) {
                throw new BDJenkinsHubPluginException("Variable was not properly replaced. Value : " + value + ", Result : " + newValue
                        + ". Make sure the variable has been properly defined.");
            }
            return newValue;
        } else {
            return null;
        }
    }

    public void printConfiguration(AbstractBuild build, BuildListener listener, String projectName, String projectVersion, List<String> scanTargets)
            throws IOException,
            InterruptedException {
        listener.getLogger().println(
                "Initializing - Hub Jenkins Plugin - "
                        + getDescriptor().getPluginVersion());
        listener.getLogger().println("-> Running on : " + build.getBuiltOn().getChannel().call(new GetHostName()));
        listener.getLogger().println("-> Using Url : " + getDescriptor().getHubServerInfo().getServerUrl());
        listener.getLogger().println("-> Using Username : " + getDescriptor().getHubServerInfo().getUsername());
        listener.getLogger().println(
                "-> Using Build Full Name : " + build.getFullDisplayName());
        listener.getLogger().println(
                "-> Using Build Number : " + build.getNumber());
        listener.getLogger().println(
                "-> Using Build Workspace Path : "
                        + build.getWorkspace().getRemote());
        listener.getLogger().println(
                "-> Using Hub Project Name : " + projectName + ", Version : " + projectVersion);

        listener.getLogger().println(
                "-> Scanning the following targets  : ");
        for (String target : scanTargets) {
            listener.getLogger().println(
                    "-> " + target);
        }
    }

    /**
     * Validates that the target of the scanJob exists, creates a ProcessBuilder to run the shellscript and passes in
     * the necessarry arguments, sets the JAVA_HOME of the Process Builder to the one that the User chose, starts the
     * process and prints out all stderr and stdout to the Console Output.
     *
     * @param build
     *            AbstractBuild
     * @param launcher
     *            Launcher
     * @param listener
     *            BuildListener
     * @param scanExec
     *            FilePath
     * @param scanTargets
     *            List<String>
     *
     * @throws IOException
     * @throws HubConfigurationException
     * @throws InterruptedException
     * @throws BDRestException
     * @throws BDJenkinsHubPluginException
     */
    private void runScan(AbstractBuild build, Launcher launcher, BuildListener listener, FilePath scanExec, List<String> scanTargets)
            throws IOException, HubConfigurationException, InterruptedException, BDRestException, BDJenkinsHubPluginException {
        validateScanTargets(listener, build.getBuiltOn().getChannel(), scanTargets);
        String hubVersion = null;
        JenkinsHubIntRestService service = setJenkinsHubIntRestService(listener);
        try {
            hubVersion = service.getHubVersion();
        } catch (BDRestException e) {
            if (e.getResourceException().getStatus().equals(Status.CLIENT_ERROR_NOT_FOUND)) {
                // The Hub server is version 2.0.0 and the version endpoint does not exist
            } else {
                listener.error(e.getResourceException().getMessage());
            }
        }

        CallableHubScan scan = new CallableHubScan(build, launcher, listener);
        scan.setHubServerInfo(getDescriptor().getHubServerInfo());
        scan.setHubVersion(hubVersion);
        scan.setJava(getJava());
        scan.setScanExec(scanExec);
        scan.setScanMemory(scanMemory);
        scan.setScanTargets(scanTargets);
        scan.setWorkingDirectory(new File(workingDirectory));

        scan.setIsTest(isTEST());

        Result result = build.getBuiltOn().getChannel().call(scan);

        setResult(result);
    }

    public JDK getJava() {
        return java;
    }

    /**
     * Sets the Java Home that is to be used for running the Shell script
     *
     * @param build
     *            AbstractBuild
     * @param listener
     *            BuildListener
     * @throws IOException
     * @throws InterruptedException
     * @throws HubConfigurationException
     */
    private void setJava(AbstractBuild build, BuildListener listener) throws IOException, InterruptedException, HubConfigurationException {
        EnvVars envVars = build.getEnvironment(listener);
        JDK javaHomeTemp = null;
        if (StringUtils.isEmpty(build.getBuiltOn().getNodeName())) {
            listener.getLogger().println("Getting Jdk on master  : " + build.getBuiltOn().getNodeName());
            // Empty node name indicates master
            javaHomeTemp = build.getProject().getJDK();
        } else {
            listener.getLogger().println("Getting Jdk on node  : " + build.getBuiltOn().getNodeName());
            javaHomeTemp = build.getProject().getJDK().forNode(build.getBuiltOn(), listener);
        }
        if (javaHomeTemp != null && javaHomeTemp.getHome() != null) {
            listener.getLogger().println("JDK home : " + javaHomeTemp.getHome());
        }

        if (javaHomeTemp == null || StringUtils.isEmpty(javaHomeTemp.getHome())) {
            listener.getLogger().println("Could not find the specified Java installation, checking the JAVA_HOME variable.");
            if (envVars.get("JAVA_HOME") == null || envVars.get("JAVA_HOME") == "") {
                throw new HubConfigurationException("Need to define a JAVA_HOME or select an installed JDK.");
            }
            // In case the user did not select a java installation, set to the environment variable JAVA_HOME
            javaHomeTemp = new JDK("Default Java", envVars.get("JAVA_HOME"));
        }
        FilePath javaExec = new FilePath(build.getBuiltOn().getChannel(), javaHomeTemp.getHome());
        if (!javaExec.exists()) {
            throw new HubConfigurationException("Could not find the specified Java installation at: " +
                    javaExec.getRemote());
        }
        java = javaHomeTemp;
    }

    /**
     * Looks through the ScanInstallations to find the one that the User chose, then looks for the scan.cli.sh in the
     * bin folder of the directory defined by the Installation.
     * It then checks that the File exists.
     *
     * @param iScanTools
     *            IScanInstallation[] User defined iScan installations
     * @param listener
     *            BuildListener
     * @param build
     *            AbstractBuild
     *
     * @return File the scan.cli.sh
     * @throws IScanToolMissingException
     * @throws IOException
     * @throws InterruptedException
     * @throws HubConfigurationException
     */
    public FilePath getScanCLI(ScanInstallation[] scanTools, BuildListener listener, AbstractBuild build) throws IScanToolMissingException, IOException,
            InterruptedException, HubConfigurationException {
        FilePath iScanExec = null;
        for (ScanInstallation iScan : scanTools) {
            Node node = build.getBuiltOn();
            if (StringUtils.isEmpty(node.getNodeName())) {
                // Empty node name indicates master
                listener.getLogger().println("[DEBUG] : Running on : master");
            } else {
                listener.getLogger().println("[DEBUG] : Running on : " + node.getNodeName());
                iScan = iScan.forNode(node, listener); // Need to get the Slave iScan
            }
            if (iScan.getName().equals(getScanName())) {
                if (iScan.getExists(node.getChannel(), listener)) {
                    iScanExec = iScan.getCLI(node.getChannel());
                    if (iScanExec == null) {
                        // Should not get here unless there are no iScan Installations defined
                        // But we check this just in case
                        throw new HubConfigurationException("You need to select which BlackDuck Scan installation to use.");
                    }
                    listener.getLogger().println(
                            "[DEBUG] : Using this BlackDuck Scan CLI at : " + iScanExec.getRemote());
                } else {
                    listener.getLogger().println("[ERROR] : Could not find the CLI file in : " + iScan.getHome());
                    throw new IScanToolMissingException("Could not find the CLI file to execute at : '" + iScan.getHome() + "'");
                }
            }
        }
        if (iScanExec == null) {
            // Should not get here unless there are no iScan Installations defined
            // But we check this just in case
            throw new HubConfigurationException("You need to select which BlackDuck Scan installation to use.");
        }
        return iScanExec;
    }

    /**
     * Validates that the Plugin is configured correctly. Checks that the User has defined an iScan tool, a Hub server
     * URL, a Credential, and that there are at least one scan Target/Job defined in the Build
     *
     * @param iScanTools
     *            IScanInstallation[] User defined iScan installations
     * @param scans
     *            IScanJobs[] the iScan jobs defined in the Job config
     *
     * @return True if everything is configured correctly
     *
     * @throws IScanToolMissingException
     * @throws HubConfigurationException
     */
    public boolean validateConfiguration(ScanInstallation[] iScanTools, ScanJobs[] scans) throws IScanToolMissingException, HubConfigurationException {
        if (iScanTools == null || iScanTools.length == 0 || iScanTools[0] == null) {
            throw new IScanToolMissingException("Could not find an Black Duck Scan Installation to use.");
        }
        if (scans == null || scans.length == 0) {
            throw new HubConfigurationException("Could not find any targets to scan.");
        }
        if (!getDescriptor().getHubServerInfo().isPluginConfigured()) {
            // If plugin is not Configured, we try to find out what is missing.
            if (StringUtils.isEmpty(getDescriptor().getHubServerInfo().getServerUrl())) {
                throw new HubConfigurationException("No Hub URL was provided.");
            }
            if (StringUtils.isEmpty(getDescriptor().getHubServerInfo().getCredentialsId())) {
                throw new HubConfigurationException("No credentials could be found to connect to the Hub.");
            }
        }
        // No exceptions were thrown so return true
        return true;
    }

    /**
     * Validates that all scan targets exist
     *
     * @param listener
     *            BuildListener
     * @param channel
     *            VirtualChannel
     * @param scanTargets
     *            List<String>
     *
     * @return
     * @throws IOException
     * @throws HubConfigurationException
     * @throws InterruptedException
     */
    public boolean validateScanTargets(BuildListener listener, VirtualChannel channel, List<String> scanTargets) throws IOException, HubConfigurationException,
            InterruptedException {
        for (String currTarget : scanTargets) {
            File locationFile = new File(currTarget);
            String targetPath = "";
            if (channel != null) {
                targetPath = channel.call(new GetCanonicalPath(locationFile));
            } else {
                targetPath = locationFile.getCanonicalPath();
            }

            if (targetPath.length() <= getWorkingDirectory().length()
                    && !getWorkingDirectory().equals(targetPath) && !targetPath.contains(getWorkingDirectory())) {
                throw new HubConfigurationException("Can not scan targets outside of the workspace.");
            }

            FilePath target = new FilePath(channel, targetPath);
            if (!target.exists()) {
                throw new IOException("Scan target could not be found : " + target.getRemote());
            } else {
                listener.getLogger().println(
                        "[DEBUG] : Scan target exists at : " + target.getRemote());
            }
        }
        return true;
    }
}
