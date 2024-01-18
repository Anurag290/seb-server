/*
 * Copyright (c) 2023 ETH Zürich, Educational Development and Technology (LET)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package ch.ethz.seb.sebserver.gbl;

import ch.ethz.seb.sebserver.gbl.model.exam.CollectingStrategy;
import ch.ethz.seb.sebserver.gbl.model.exam.ProctoringServiceSettings.ProctoringServerType;
import ch.ethz.seb.sebserver.gbl.model.institution.LmsSetup.LmsType;

@Deprecated // we need another more flexible feature service that also take new User Role and Privileges into account
            // SEBSERV-497
public interface FeatureService {

    String FEATURE_SETTINGS_PREFIX = "sebserver.feature.";

    enum ConfigurableFeature {
        SCREEN_PROCTORING("seb.screenProctoring"),
        INSTITUTION("admin.institution"),
        REMOTE_PROCTORING("seb.remoteProctoring"),
        TEST_LMS("lms.testLMS"),
        EXAM_NO_LMS("exam.noLMS"),
        LIGHT_SETUP("setup.light")

        ;

        final String namespace;

        ConfigurableFeature(final String namespace) {
            this.namespace = namespace;
        }
    }

    boolean isEnabled(ConfigurableFeature feature);

    boolean isEnabled(LmsType LmsType);

    boolean isEnabled(ProctoringServerType proctoringServerType);

    boolean isEnabled(CollectingStrategy collectingRoomStrategy);



}
