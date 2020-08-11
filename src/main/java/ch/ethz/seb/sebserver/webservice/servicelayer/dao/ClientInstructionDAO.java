/*
 * Copyright (c) 2019 ETH Zürich, Educational Development and Technology (LET)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package ch.ethz.seb.sebserver.webservice.servicelayer.dao;

import java.util.Collection;

import ch.ethz.seb.sebserver.gbl.model.session.ClientInstruction.InstructionType;
import ch.ethz.seb.sebserver.gbl.util.Result;
import ch.ethz.seb.sebserver.webservice.datalayer.batis.model.ClientInstructionRecord;

public interface ClientInstructionDAO {

    Result<ClientInstructionRecord> insert(
            Long examId,
            InstructionType type,
            String attributes,
            String connectionTokens,
            boolean needsConfirmation);

    Result<Collection<ClientInstructionRecord>> getAllActive();

    Result<Void> delete(Long id);

}
