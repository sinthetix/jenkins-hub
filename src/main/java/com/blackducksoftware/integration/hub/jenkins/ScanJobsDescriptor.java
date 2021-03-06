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
package com.blackducksoftware.integration.hub.jenkins;

import java.io.IOException;

import javax.servlet.ServletException;

import org.apache.commons.lang3.StringUtils;
import org.kohsuke.stapler.QueryParameter;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.util.FormValidation;

@Extension
public class ScanJobsDescriptor extends Descriptor<ScanJobs> {

	public ScanJobsDescriptor() {
		super(ScanJobs.class);
		load();
	}

	@Override
	public String getDisplayName() {
		return "";
	}

	/**
	 * Performs on-the-fly validation of the form field 'scanTarget'.
	 *
	 */
	public FormValidation doCheckScanTarget(@QueryParameter("scanTarget") final String scanTarget)
			throws IOException, ServletException {
		if (StringUtils.isBlank(scanTarget)) {
			return FormValidation.warningWithMarkup(Messages
					.HubBuildScan_getWorkspaceWillBeScanned());
		}

		return FormValidation.ok();
	}

}
