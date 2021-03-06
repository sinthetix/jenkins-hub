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
package com.blackducksoftware.integration.hub.jenkins.action;

import hudson.model.Action;
import hudson.model.AbstractBuild;

import java.util.List;

import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;

import com.blackducksoftware.integration.hub.jenkins.Messages;
import com.blackducksoftware.integration.hub.report.api.AggregateBomViewEntry;
import com.blackducksoftware.integration.hub.report.api.DetailedReleaseSummary;
import com.blackducksoftware.integration.hub.report.api.HubRiskReportData;
import com.blackducksoftware.integration.hub.report.api.VersionReport;

public class HubReportAction implements Action {

    private final AbstractBuild<?, ?> build;

    private HubRiskReportData reportData;

    public HubReportAction(AbstractBuild<?, ?> build) {
        this.build = build;
    }

    public AbstractBuild<?, ?> getBuild() {
        return build;
    }

    public VersionReport getReport() {
        return reportData.getReport();
    }

    public DetailedReleaseSummary getReleaseSummary() {
        if (reportData == null || reportData.getReport() == null) {
            return null;
        }
        return reportData.getReport().getDetailedReleaseSummary();
    }

    public List<AggregateBomViewEntry> getBomEntries() {
        if (reportData == null || reportData.getReport() == null) {
            return null;
        }
        return reportData.getReport().getAggregateBomViewEntries();
    }

    public int getVulnerabilityRiskHighCount() {
        return reportData.getVulnerabilityRiskHighCount();
    }

    public int getVulnerabilityRiskMediumCount() {
        return reportData.getVulnerabilityRiskMediumCount();
    }

    public int getVulnerabilityRiskLowCount() {
        return reportData.getVulnerabilityRiskLowCount();
    }

    public int getVulnerabilityRiskNoneCount() {
        return reportData.getVulnerabilityRiskNoneCount();
    }

    public int getLicenseRiskHighCount() {
        return reportData.getLicenseRiskHighCount();
    }

    public int getLicenseRiskMediumCount() {
        return reportData.getLicenseRiskMediumCount();
    }

    public int getLicenseRiskLowCount() {
        return reportData.getLicenseRiskLowCount();
    }

    public int getLicenseRiskNoneCount() {
        return reportData.getLicenseRiskNoneCount();
    }

    public int getOperationalRiskHighCount() {
        return reportData.getOperationalRiskHighCount();
    }

    public int getOperationalRiskMediumCount() {
        return reportData.getOperationalRiskMediumCount();
    }

    public int getOperationalRiskLowCount() {
        return reportData.getOperationalRiskLowCount();
    }

    public int getOperationalRiskNoneCount() {
        return reportData.getOperationalRiskNoneCount();
    }

    public double getPercentage(double count) {
        if (getBomEntries() == null) {
            return 0.0;
        }
        double totalCount = getBomEntries().size();
        double percentage = 0;
        if (totalCount > 0 && count > 0) {
            percentage = (count / totalCount) * 100;
        }
        return percentage;
    }

    public String htmlEscape(String valueToEscape) {
        if (StringUtils.isBlank(valueToEscape)) {
            return null;
        }
        return StringEscapeUtils.escapeHtml4(valueToEscape);
    }

    public void setReportData(HubRiskReportData reportData) {
        this.reportData = reportData;
    }

    @Override
    public String getIconFileName() {
        return "/plugin/hub-jenkins/images/blackduck.png";
    }

    @Override
    public String getDisplayName() {
        return Messages.HubReportAction_getDisplayName();
    }

    @Override
    public String getUrlName() {
        return "hub_risk_report";
    }

}
