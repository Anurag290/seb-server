/*
 * Copyright (c) 2020 ETH Zürich, Educational Development and Technology (LET)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package ch.ethz.seb.sebserver.gui.service.page;

import ch.ethz.seb.sebserver.gbl.model.exam.ProctoringServiceSettings.ProctoringServerType;

public interface RemoteProctoringView extends TemplateComposer {

    /** Get the remote proctoring server type this remote proctoring view can handle.
     *
     * @return the remote proctoring server type this remote proctoring view can handle. */
    ProctoringServerType serverType();

}
