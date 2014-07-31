package com.blackducksoftware.integration.hub.jenkins;

import hudson.Extension;
import hudson.ProxyConfiguration;
import hudson.model.AutoCompletionCandidates;
import hudson.model.AbstractProject;
import hudson.model.Descriptor;
import hudson.security.ACL;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;

import java.io.IOException;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

import javax.servlet.ServletException;

import jenkins.model.Jenkins;
import net.sf.json.JSONObject;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import com.blackducksoftware.integration.hub.jenkins.IScanInstallation.IScanDescriptor;
import com.cloudbees.plugins.credentials.CredentialsMatcher;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import com.cloudbees.plugins.credentials.matchers.IdMatcher;

/**
 * Descriptor for {@link CodeCenterBuildWrapper}. Used as a singleton. The
 * class is marked as public so that it can be accessed from views.
 * 
 * <p>
 * See <tt>src/main/resources/hudson/plugins/hello_world/HelloWorldBuilder/*.jelly</tt> for the actual HTML fragment for
 * the configuration screen.
 */
@Extension
// This indicates to Jenkins that this is an implementation of an extension
// point.
public class PostBuildScanDescriptor extends BuildStepDescriptor<Publisher> implements Serializable {

    private static final String FORM_SERVER_URL = "hubServerUrl";

    // private static final String FORM_TIMEOUT = "timeout";

    // private static final long DEFAULT_TIMEOUT = 300;

    private static final String FORM_CREDENTIALSID = "hubCredentialsId";

    private List<String> duplicates = new ArrayList<String>();

    private HubServerInfo hubServerInfo;

    private String projectId;

    /**
     * In order to load the persisted global configuration, you have to call
     * load() in the constructor.
     */
    public PostBuildScanDescriptor() {
        super(PostBuildHubiScan.class);
        load();
    }

    public List<String> getDuplicates() {
        return duplicates;
    }

    public void setDuplicates(List<String> dups) {
        duplicates = dups;
        save();
    }

    /**
     * @return the hubServerInfo
     */
    public HubServerInfo getHubServerInfo() {
        return hubServerInfo;
    }

    /**
     * @param hubServerInfo
     *            the hubServerInfo to set
     */
    public void setHubServerInfo(HubServerInfo hubServerInfo) {
        this.hubServerInfo = hubServerInfo;
    }

    public String getProjectId() {
        if (projectId != null) {
            return projectId;
        } else {
            // TODO When we get the api for this
            // JenkinsHubIntRestService service = new JenkinsHubIntRestService();
            // return service.getProjectId(getProjectName());
            return projectId;
        }

    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
        save(); // save the configuration whenever this field changes or else the config doesn't get updated correctly
    }

    public void setupService(JenkinsHubIntRestService service) {
        Jenkins jenkins = Jenkins.getInstance();
        if (jenkins != null) {
            ProxyConfiguration proxy = jenkins.proxy;
            if (proxy != null) {
                service.setNoProxyHosts(proxy.getNoProxyHostPatterns());
                service.setProxyHost(proxy.name);
                service.setProxyPort(proxy.port);
            }
        }
    }

    /**
     * Fills the Credential drop down list in the global config
     * 
     * @return
     */
    public ListBoxModel doFillHubCredentialsIdItems() {
        ClassLoader originalClassLoader = Thread.currentThread()
                .getContextClassLoader();
        boolean changed = false;
        ListBoxModel boxModel = null;
        try {

            // Code copied from
            // https://github.com/jenkinsci/git-plugin/blob/f6d42c4e7edb102d3330af5ca66a7f5809d1a48e/src/main/java/hudson/plugins/git/UserRemoteConfig.java
            CredentialsMatcher credentialsMatcher = CredentialsMatchers.anyOf(CredentialsMatchers.instanceOf(StandardUsernamePasswordCredentials.class));
            AbstractProject<?, ?> project = null; // Dont want to limit the search to a particular project for the drop
            // down menu
            boxModel = new StandardListBoxModel().withEmptySelection().withMatching(credentialsMatcher,
                    CredentialsProvider.lookupCredentials(StandardCredentials.class, project, ACL.SYSTEM, Collections.<DomainRequirement> emptyList()));

        } finally {
            if (changed) {
                Thread.currentThread().setContextClassLoader(
                        originalClassLoader);
            }
        }

        return boxModel;
    }

    /**
     * Fills the iScan drop down list in the job config
     * 
     * @return
     */
    public ListBoxModel doFillIScanNameItems() {
        ClassLoader originalClassLoader = Thread.currentThread()
                .getContextClassLoader();
        boolean changed = false;
        ListBoxModel items = null;
        try {
            items = new ListBoxModel();
            Jenkins jenkins = Jenkins.getInstance();
            IScanDescriptor iScanDescriptor = jenkins.getDescriptorByType(IScanDescriptor.class);

            IScanInstallation[] iScanInstallations = iScanDescriptor.getInstallations();
            for (IScanInstallation iScan : iScanInstallations) {
                items.add(iScan.getName());
            }

        } finally {
            if (changed) {
                Thread.currentThread().setContextClassLoader(
                        originalClassLoader);
            }
        }
        return items;
    }

    /**
     * Performs on-the-fly validation of the form field 'serverUrl'.
     * 
     * @param value
     *            This parameter receives the value that the user has typed.
     * @return Indicates the outcome of the validation. This is sent to the
     *         browser.
     */
    public FormValidation doCheckServerUrl(@QueryParameter("serverUrl") String serverUrl)
            throws IOException, ServletException {
        if (serverUrl.length() == 0) {
            return FormValidation.error(Messages
                    .HubBuildScan_getPleaseSetServerUrl());
        }
        URL url;
        try {
            url = new URL(serverUrl);
            try {
                url.toURI();
            } catch (URISyntaxException e) {
                return FormValidation.error(Messages
                        .HubBuildScan_getNotAValidUrl());
            }
        } catch (MalformedURLException e) {
            return FormValidation.error(Messages
                    .HubBuildScan_getNotAValidUrl());
        }
        try {
            URLConnection connection = url.openConnection();
            connection.getContent();
        } catch (IOException ioe) {
            return FormValidation.warning(Messages
                    .HubBuildScan_getCanNotReachThisServer_0_(serverUrl));
        } catch (RuntimeException e) {
            return FormValidation.error(Messages
                    .HubBuildScan_getNotAValidUrl());
        }
        return FormValidation.ok();
    }

    public AutoCompletionCandidates doAutoCompleteHubProjectName(@QueryParameter("value") final String hubProjectName) throws IOException,
            ServletException {
        AutoCompletionCandidates potentialMatches = new AutoCompletionCandidates();
        UsernamePasswordCredentialsImpl credential = null;
        if (!StringUtils.isEmpty(getHubServerUrl()) || !StringUtils.isEmpty(getHubServerInfo().getCredentialsId())) {
            credential = hubServerInfo.getCredential();
            if (credential != null) {
                ClassLoader originalClassLoader = Thread.currentThread()
                        .getContextClassLoader();
                boolean changed = false;
                try {
                    String credentialUserName = null;
                    String credentialPassword = null;
                    credentialUserName = credential.getUsername();
                    credentialPassword = credential.getPassword().getPlainText();

                    JenkinsHubIntRestService service = new JenkinsHubIntRestService();
                    setupService(service);
                    service.setBaseUrl(getHubServerUrl());
                    service.setCookies(credentialUserName, credentialPassword);
                    ArrayList<LinkedHashMap<String, Object>> responseList = service.getProjectMatches(hubProjectName);

                    if (!responseList.isEmpty()) {
                        ArrayList<String> projectNames = new ArrayList<String>();
                        for (LinkedHashMap<String, Object> map : responseList) {
                            if (map.get("value").equals(hubProjectName)) {
                                if (!projectNames.contains(map.get("value"))) {
                                    projectNames.add((String) map.get("value"));
                                }
                            } else {
                                // name does not match
                                projectNames.add((String) map.get("value"));
                            }
                        }
                        if (!projectNames.isEmpty()) {
                            for (String projectName : projectNames) {
                                potentialMatches.add(projectName);
                            }
                        }
                    }
                } catch (Exception e) {
                    // do nothing for exception
                } finally {
                    if (changed) {
                        Thread.currentThread().setContextClassLoader(
                                originalClassLoader);
                    }
                }
            }
        }
        return potentialMatches;
    }

    /**
     * Performs on-the-fly validation of the form field 'hubProjectName'. Checks to see if there is already a project in
     * the Hub with this name.
     * 
     * @param hubProjectName
     *            This parameter receives the value that the user has typed.
     * @return Indicates the outcome of the validation. This is sent to the
     *         browser.
     */
    public FormValidation doCheckHubProjectName(@QueryParameter("hubProjectName") final String hubProjectName) throws IOException, ServletException {
        if (hubProjectName.length() > 0) {
            ClassLoader originalClassLoader = Thread.currentThread()
                    .getContextClassLoader();
            boolean changed = false;
            try {
                setProjectId(null);
                if (StringUtils.isEmpty(getHubServerUrl())) {
                    return FormValidation.error(Messages.HubBuildScan_getPleaseSetServerUrl());
                }
                if (StringUtils.isEmpty(getHubServerInfo().getCredentialsId())) {
                    return FormValidation.error(Messages.HubBuildScan_getCredentialsNotFound());
                }
                String credentialUserName = null;
                String credentialPassword = null;

                UsernamePasswordCredentialsImpl credential = hubServerInfo.getCredential();
                if (credential == null) {
                    return FormValidation.error(Messages.HubBuildScan_getCredentialsNotFound());
                }
                credentialUserName = credential.getUsername();
                credentialPassword = credential.getPassword().getPlainText();
                JenkinsHubIntRestService service = new JenkinsHubIntRestService();
                setupService(service);
                service.setBaseUrl(getHubServerUrl());
                service.setCookies(credentialUserName, credentialPassword);

                ArrayList<LinkedHashMap<String, Object>> responseList = service.getProjectMatches(hubProjectName);
                if (!responseList.isEmpty()) {
                    StringBuilder projectMatches = new StringBuilder();
                    ArrayList<String> projIds = new ArrayList<String>();
                    for (LinkedHashMap<String, Object> map : responseList) {
                        if (map.get("value").equals(hubProjectName)) {
                            projIds.add((String) map.get("uuid"));
                            // setProjectId((String) map.get("uuid"));
                            // return FormValidation.ok(Messages.HubBuildScan_getProjectExistsIn_0_(getServerUrl()));
                        } else {
                            // name does not match
                            if (projectMatches.length() > 0) {
                                projectMatches.append(", " + (String) map.get("value"));
                            } else {
                                projectMatches.append((String) map.get("value"));
                            }
                        }
                    }
                    if (projIds.size() > 1) {
                        List<String> dupList = new ArrayList<String>();
                        for (String id : projIds) {
                            dupList.add(id);
                        }
                        setDuplicates(dupList);
                        return FormValidation.warning(Messages.HubBuildScan_getProjectExistsWithDuplicateMatches_0_(getHubServerUrl()));
                    } else if (projIds.size() == 1) {
                        setDuplicates(null);
                        setProjectId(projIds.get(0));
                        return FormValidation.ok(Messages.HubBuildScan_getProjectExistsIn_0_(getHubServerUrl()));
                    } else {
                        setDuplicates(null);
                        return FormValidation.error(Messages.HubBuildScan_getProjectNonExistingWithMatches_0_(getHubServerUrl(), projectMatches.toString()));
                    }
                } else {
                    setDuplicates(null);
                    return FormValidation.error(Messages.HubBuildScan_getProjectNonExistingIn_0_(getHubServerUrl()));
                }
            } catch (Exception e) {
                setDuplicates(null);
                String message;
                if (e.getCause() != null && e.getCause().getCause() != null) {
                    message = e.getCause().getCause().toString();
                } else if (e.getCause() != null) {
                    message = e.getCause().toString();
                } else {
                    message = e.toString();
                }
                if (message.toLowerCase().contains("service unavailable")) {
                    message = Messages.HubBuildScan_getCanNotReachThisServer_0_(getHubServerUrl());
                } else if (message.toLowerCase().contains("precondition failed")) {
                    message = message + ", Check your configuration.";
                }
                return FormValidation.error(message);
            } finally {
                // load();
                // JenkinsHubIntRestService temp = new JenkinsHubIntRestService();
                // temp.reloadDuplicates();
                if (changed) {
                    Thread.currentThread().setContextClassLoader(
                            originalClassLoader);
                }
            }
        }
        return FormValidation.ok();
    }

    /**
     * Fills the duplicate Project Id drop down list. Query for the hubProjectName value so that when it is changed it
     * will trigger this drop down list to be filled or emptied.
     * 
     * 
     * @return
     * @throws InterruptedException
     */
    public ListBoxModel doFillDuplicateHubProjectIdItems(@QueryParameter("hubProjectName") final String hubProjectName) throws InterruptedException {
        Thread.sleep(1800); // DO NOT REMOVE
        // The sleep is so the checks can be run on the name properly before we try and populate the duplicate list
        ClassLoader originalClassLoader = Thread.currentThread()
                .getContextClassLoader();
        boolean changed = false;
        ListBoxModel items = null;
        try {
            items = new ListBoxModel();
            List<String> dups = getDuplicates();
            if (dups != null) {
                for (String dup : dups) {
                    items.add(dup);
                }
            }
        } finally {
            if (changed) {
                Thread.currentThread().setContextClassLoader(
                        originalClassLoader);
            }
        }
        return items;
    }

    /**
     * Performs on-the-fly validation of the form field 'hubProjectRelease'. Checks to see if there is already a project
     * in the Hub with this name.
     * 
     * @param hubProjectRelease
     *            This parameter receives the value that the user has typed for the Release.
     * @param hubProjectDuplicateId
     *            This parameter receives the value of the Project Id that the User has selected, if any.
     * 
     * @return Indicates the outcome of the validation. This is sent to the
     *         browser.
     */
    public FormValidation doCheckHubProjectRelease(@QueryParameter("hubProjectRelease") final String hubProjectRelease,
            @QueryParameter("duplicateHubProjectId") final String hubProjectDuplicateId) throws IOException, ServletException {
        if (hubProjectRelease.length() > 0) {

            ClassLoader originalClassLoader = Thread.currentThread()
                    .getContextClassLoader();
            boolean changed = false;
            try {
                if (StringUtils.isEmpty(getProjectId()) && StringUtils.isEmpty(hubProjectDuplicateId)) {
                    return FormValidation.error(Messages.HubBuildScan_getReleaseNonExistingIn_0_(null, null));
                }
                if (StringUtils.isEmpty(getHubServerUrl())) {
                    return FormValidation.error(Messages.HubBuildScan_getPleaseSetServerUrl());
                }
                if (StringUtils.isEmpty(getHubServerInfo().getCredentialsId())) {
                    return FormValidation.error(Messages.HubBuildScan_getCredentialsNotFound());
                }

                String credentialUserName = null;
                String credentialPassword = null;

                UsernamePasswordCredentialsImpl credential = hubServerInfo.getCredential();
                if (credential == null) {
                    return FormValidation.error(Messages.HubBuildScan_getCredentialsNotFound());
                }
                credentialUserName = credential.getUsername();
                credentialPassword = credential.getPassword().getPlainText();
                JenkinsHubIntRestService service = new JenkinsHubIntRestService();
                setupService(service);
                service.setBaseUrl(getHubServerUrl());
                service.setCookies(credentialUserName, credentialPassword);
                String idToUse = null;
                if (!StringUtils.isEmpty(hubProjectDuplicateId)) {
                    idToUse = hubProjectDuplicateId;
                } else {
                    idToUse = getProjectId();
                }

                HashMap<String, Object> responseMap = service.getReleaseMatchesForProjectId(idToUse);
                StringBuilder projectReleases = new StringBuilder();
                if (responseMap.containsKey("items")) {
                    ArrayList<LinkedHashMap> releaseList = (ArrayList<LinkedHashMap>) responseMap.get("items");
                    for (LinkedHashMap release : releaseList) {
                        if (((String) release.get("version")).equals(hubProjectRelease)) {
                            return FormValidation.ok(Messages.HubBuildScan_getReleaseExistsIn_0_(idToUse));
                        } else {
                            if (projectReleases.length() > 0) {
                                projectReleases.append(", " + ((String) release.get("version")));
                            } else {
                                projectReleases.append((String) release.get("version"));
                            }
                        }
                    }
                } else {
                    // The Hub Api has changed and we received a JSON response that we did not expect
                    return FormValidation.error(Messages.HubBuildScan_getIncorrectMappingOfServerResponse());
                }
                return FormValidation.error(Messages.HubBuildScan_getReleaseNonExistingIn_0_(idToUse, projectReleases.toString()));
            } catch (Exception e) {
                String message;
                if (e.getCause() != null && e.getCause().getCause() != null) {
                    message = e.getCause().getCause().toString();
                } else if (e.getCause() != null) {
                    message = e.getCause().toString();
                } else {
                    message = e.toString();
                }
                if (message.toLowerCase().contains("service unavailable")) {
                    message = Messages.HubBuildScan_getCanNotReachThisServer_0_(getHubServerUrl());
                } else if (message.toLowerCase().contains("precondition failed")) {
                    message = message + ", Check your configuration.";
                }
                return FormValidation.error(message);
            } finally {
                if (changed) {
                    Thread.currentThread().setContextClassLoader(
                            originalClassLoader);
                }
            }
        }
        return FormValidation.ok();
    }

    /**
     * Validates that the URL, Username, and Password are correct for connecting to the Hub Server.
     * 
     * 
     * @param serverUrl
     *            String
     * @param hubCredentialsId
     *            String
     * @return FormValidation
     * @throws ServletException
     */
    public FormValidation doTestConnection(@QueryParameter("hubServerUrl") final String serverUrl,
            @QueryParameter("hubCredentialsId") final String hubCredentialsId) {
        ClassLoader originalClassLoader = Thread.currentThread()
                .getContextClassLoader();
        boolean changed = false;
        try {
            if (StringUtils.isEmpty(serverUrl)) {
                return FormValidation.error(Messages.HubBuildScan_getPleaseSetServerUrl());
            }
            if (StringUtils.isEmpty(hubCredentialsId)) {
                return FormValidation.error(Messages.HubBuildScan_getCredentialsNotFound());
            }

            String credentialUserName = null;
            String credentialPassword = null;

            UsernamePasswordCredentialsImpl credential = null;
            AbstractProject<?, ?> project = null;
            List<StandardUsernamePasswordCredentials> credentials = CredentialsProvider.lookupCredentials(StandardUsernamePasswordCredentials.class,
                    project, ACL.SYSTEM,
                    Collections.<DomainRequirement> emptyList());
            IdMatcher matcher = new IdMatcher(hubCredentialsId);
            for (StandardCredentials c : credentials) {
                if (matcher.matches(c) && c instanceof UsernamePasswordCredentialsImpl) {
                    credential = (UsernamePasswordCredentialsImpl) c;
                }
            }
            if (credential == null) {
                return FormValidation.error(Messages.HubBuildScan_getCredentialsNotFound());
            }
            credentialUserName = credential.getUsername();
            credentialPassword = credential.getPassword().getPlainText();

            JenkinsHubIntRestService service = new JenkinsHubIntRestService();

            setupService(service);
            service.setBaseUrl(serverUrl);

            int responseCode = service.setCookies(credentialUserName, credentialPassword);

            if (responseCode == 200 || responseCode == 204 || responseCode == 202) {
                return FormValidation.ok(Messages.HubBuildScan_getCredentialsValidFor_0_(serverUrl));
            } else if (responseCode == 401) {
                // If User is Not Authorized, 401 error, an exception should be thrown by the ClientResource
                return FormValidation.error(Messages.HubBuildScan_getCredentialsInValidFor_0_(serverUrl));
            } else {
                return FormValidation.error(Messages.HubBuildScan_getErrorConnectingTo_0_(responseCode));
            }
        } catch (Exception e) {
            String message;
            if (e.getCause() != null && e.getCause().getCause() != null) {
                message = e.getCause().getCause().toString();
            } else if (e.getCause() != null) {
                message = e.getCause().toString();
            } else {
                message = e.toString();
            }
            if (message.toLowerCase().contains("service unavailable")) {
                message = Messages.HubBuildScan_getCanNotReachThisServer_0_(getHubServerUrl());
            } else if (message.toLowerCase().contains("precondition failed")) {
                message = message + ", Check your configuration.";
            }
            return FormValidation.error(message);
        } finally {
            if (changed) {
                Thread.currentThread().setContextClassLoader(
                        originalClassLoader);
            }
        }

    }

    /**
     * Validates that the URL, Username, and Password are correct for connecting to the Hub Server.
     * 
     * 
     * @param serverUrl
     *            String
     * @param hubCredentialsId
     *            String
     * @return FormValidation
     * @throws ServletException
     */
    public FormValidation doCreateHubProject(@QueryParameter("hubProjectName") final String hubProjectName,
            @QueryParameter("hubProjectRelease") final String hubProjectRelease, @QueryParameter("duplicateHubProjectId") final String hubProjectDuplicateId) {
        ClassLoader originalClassLoader = Thread.currentThread()
                .getContextClassLoader();
        boolean changed = false;
        try {
            save();
            if (StringUtils.isEmpty(hubProjectName)) {
                return FormValidation.error(Messages.HubBuildScan_getProvideProjectName());
            }
            if (StringUtils.isEmpty(hubProjectRelease)) {
                return FormValidation.error(Messages.HubBuildScan_getProvideProjectRelease());
            }
            // Check if the Project with the given name exists or not before creating it
            FormValidation projectNameCheck = doCheckHubProjectName(hubProjectName);
            String projectNonExistentMessage = Messages.HubBuildScan_getProjectNonExistingWithMatches_0_(null, null);
            projectNonExistentMessage = projectNonExistentMessage.substring(0, 47);
            String duplicatesMessage = Messages.HubBuildScan_getProjectExistsWithDuplicateMatches_0_(null);
            duplicatesMessage = duplicatesMessage.substring(50, 96);
            boolean projectExists = false;
            if (FormValidation.Kind.OK.equals(projectNameCheck.kind)
                    || (FormValidation.Kind.WARNING.equals(projectNameCheck.kind) && projectNameCheck.getMessage().contains(duplicatesMessage))) {
                // Project exists for given name
                projectExists = true;
                // Check if the Release for the given Project exists or not before creating it
                FormValidation projectReleaseCheck = doCheckHubProjectRelease(hubProjectRelease, hubProjectDuplicateId);
                String releaseNonExistentMessage = Messages.HubBuildScan_getReleaseNonExistingIn_0_(null, null);
                releaseNonExistentMessage = releaseNonExistentMessage.substring(0, 52);
                if (FormValidation.Kind.OK.equals(projectReleaseCheck.kind)) {
                    return FormValidation.warning(Messages.HubBuildScan_getProjectAndReleaseExist());
                } else if (!FormValidation.Kind.ERROR.equals(projectReleaseCheck.kind) && !projectReleaseCheck.getMessage().contains(releaseNonExistentMessage)) {
                    return FormValidation.error(projectReleaseCheck.getMessage());
                }
            } else if (!FormValidation.Kind.ERROR.equals(projectNameCheck.kind) && !projectNameCheck.getMessage().contains(projectNonExistentMessage)) {
                return FormValidation.error(projectNameCheck.getMessage());
            }

            String credentialUserName = null;
            String credentialPassword = null;

            UsernamePasswordCredentialsImpl credential = hubServerInfo.getCredential();
            if (credential == null) {
                return FormValidation.error(Messages.HubBuildScan_getCredentialsNotFound());
            }
            credentialUserName = credential.getUsername();
            credentialPassword = credential.getPassword().getPlainText();

            JenkinsHubIntRestService service = new JenkinsHubIntRestService();
            setupService(service);
            service.setBaseUrl(getHubServerUrl());
            service.setCookies(credentialUserName, credentialPassword);

            if (!projectExists) {
                HashMap<String, Object> responseMap = service.createHubProject(hubProjectName);
                if (responseMap.containsKey("id")) {
                    String id = (String) responseMap.get("id");
                    setProjectId(id);
                } else {
                    // The Hub Api has changed and we received a JSON response that we did not expect
                    return FormValidation.error(Messages.HubBuildScan_getIncorrectMappingOfServerResponse());
                }
            }
            int responseCode = 0;
            if (!StringUtils.isEmpty(hubProjectDuplicateId) && projectExists) {
                responseCode = service.createHubRelease(hubProjectRelease, hubProjectDuplicateId);
            } else {
                responseCode = service.createHubRelease(hubProjectRelease, getProjectId());
            }
            if (responseCode == 201) {
                return FormValidation.ok(Messages.HubBuildScan_getProjectAndReleaseCreated());
            } else if (responseCode == 401) {
                // If User is Not Authorized, 401 error, an exception should be thrown by the ClientResource
                return FormValidation.error(Messages.HubBuildScan_getCredentialsInValidFor_0_(getHubServerUrl()));
            } else {
                return FormValidation.error(Messages.HubBuildScan_getErrorConnectingTo_0_(responseCode));
            }
        } catch (Exception e) {
            String message;
            if (e.getCause() != null && e.getCause().getCause() != null) {
                message = e.getCause().getCause().toString();
            } else if (e.getCause() != null) {
                message = e.getCause().toString();
            } else {
                message = e.toString();
            }
            if (message.toLowerCase().contains("service unavailable")) {
                message = Messages.HubBuildScan_getCanNotReachThisServer_0_(getHubServerUrl());
            } else if (message.toLowerCase().contains("precondition failed")) {
                message = message + ", Check your configuration.";
            }
            return FormValidation.error(message);
        } finally {
            if (changed) {
                Thread.currentThread().setContextClassLoader(
                        originalClassLoader);
            }
        }

    }

    @Override
    public boolean isApplicable(Class aClass) {
        // Indicates that this builder can be used with all kinds of project
        // types
        return true;
        // || aClass.getClass().isAssignableFrom(MavenModuleSet.class);
    }

    /**
     * This human readable name is used in the configuration screen.
     */
    @Override
    public String getDisplayName() {
        return Messages.HubBuildScan_getDisplayName();
    }

    // /**
    // * Performs on-the-fly validation of the scans targets
    // *
    // * @param value
    // * This parameter receives the value that the user has typed.
    // * @return
    // * Indicates the outcome of the validation. This is sent to the browser.
    // */
    // public FormValidation doCheckScanTarget(@QueryParameter String value)
    // throws IOException, ServletException {
    // if (value.startsWith("/") || value.startsWith("\\")) {
    // return FormValidation.warning("");
    // }
    // if (value.endsWith("/") || value.endsWith("\\")) {
    // return FormValidation.warning("");
    // }
    // return FormValidation.ok();
    // }

    @Override
    public boolean configure(StaplerRequest req, JSONObject formData)
            throws Descriptor.FormException {
        // To persist global configuration information,
        // set that to properties and call save().
        hubServerInfo = new HubServerInfo(formData.getString(FORM_SERVER_URL), formData.getString(FORM_CREDENTIALSID));
        // formData.getLong(FORM_TIMEOUT));
        // ^Can also use req.bindJSON(this, formData);
        // (easier when there are many fields; need set* methods for this,
        // like setUseFrench)
        save();
        return super.configure(req, formData);
    }

    // public String getIScanToolLocation() {
    // return (iScanInfo == null ? "" : (iScanInfo
    // .getToolLocation() == null ? "" : iScanInfo
    // .getToolLocation()));
    // }

    public String getHubServerUrl() {
        return (hubServerInfo == null ? "" : (hubServerInfo
                .getServerUrl() == null ? "" : hubServerInfo
                .getServerUrl()));
    }

    // public long getTimeout() {
    // return hubServerInfo == null ? getDefaultTimeout()
    // : hubServerInfo.getTimeout();
    // }
    //
    // public long getDefaultTimeout() {
    // return DEFAULT_TIMEOUT;
    // }

    public String getHubCredentialsId() {
        return (hubServerInfo == null ? "" : (hubServerInfo.getCredentialsId() == null ? "" : hubServerInfo.getCredentialsId()));
    }

    public static class DuplicateProject {
        private String id;

        public DuplicateProject() {

        }

        public void setId(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }
    }
}
