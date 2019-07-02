/*
 * Copyright (c) 2019 ETH Zürich, Educational Development and Technology (LET)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package ch.ethz.seb.sebserver.webservice.servicelayer.dao;

import ch.ethz.seb.sebserver.gbl.model.session.ClientConnection;
import ch.ethz.seb.sebserver.gbl.util.Result;

public interface ClientConnectionDAO extends EntityDAO<ClientConnection, ClientConnection> {

    Result<ClientConnection> byConnectionToken(Long institutionId, String connectionToken);

    Result<ClientConnection> byConnectionToken(String connectionToken);

}
