/*
 * Copyright (c) 2019 ETH Zürich, Educational Development and Technology (LET)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package ch.ethz.seb.sebserver.webservice.servicelayer.lms.impl;

import java.io.InputStream;

import org.springframework.stereotype.Service;

import ch.ethz.seb.sebserver.gbl.model.institution.LmsSetup;
import ch.ethz.seb.sebserver.gbl.profile.WebServiceProfile;
import ch.ethz.seb.sebserver.gbl.util.Result;
import ch.ethz.seb.sebserver.webservice.servicelayer.dao.LmsSetupDAO;
import ch.ethz.seb.sebserver.webservice.servicelayer.lms.LmsAPIService;
import ch.ethz.seb.sebserver.webservice.servicelayer.lms.LmsAPITemplate;

@Service
@WebServiceProfile
public class LmsAPIServiceImpl implements LmsAPIService {

    private final LmsSetupDAO lmsSetupDAO;

    public LmsAPIServiceImpl(final LmsSetupDAO lmsSetupDAO) {
        this.lmsSetupDAO = lmsSetupDAO;
    }

    @Override
    public Result<LmsAPITemplate> createLmsAPITemplate(final Long lmsSetupId) {
        return this.lmsSetupDAO
                .byPK(lmsSetupId)
                .flatMap(this::createLmsAPITemplate);
    }

    @Override
    public Result<LmsAPITemplate> createLmsAPITemplate(final LmsSetup lmsSetup) {
        switch (lmsSetup.lmsType) {
            case MOCKUP:
                return Result.of(new MockupLmsAPITemplate(lmsSetup));
            default:
                return Result.ofError(
                        new UnsupportedOperationException("No support for LMS Type: " + lmsSetup.lmsType));
        }
    }

    @Override
    public Result<InputStream> createSEBStartConfiguration(final Long lmsSetupId) {
        return this.lmsSetupDAO
                .byPK(lmsSetupId)
                .flatMap(this::createSEBStartConfiguration);
    }

    @Override
    public Result<InputStream> createSEBStartConfiguration(final LmsSetup lmsSetup) {

        // TODO implementation of creation of SEB start configuration for specified LmsSetup
        // A SEB start configuration should at least contain the SEB-Client-Credentials to access the SEB Server API
        // and the SEB Server URL
        //
        // To Clarify : The format of a SEB start configuration
        // To Clarify : How the file should be encrypted (use case) maybe we need another encryption-secret for this that can be given by
        //              an administrator on SEB start configuration creation time

        return Result.ofTODO();
    }

}
