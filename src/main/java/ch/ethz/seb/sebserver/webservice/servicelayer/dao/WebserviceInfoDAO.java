/*
 * Copyright (c) 2021 ETH Zürich, IT Services
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package ch.ethz.seb.sebserver.webservice.servicelayer.dao;

/** Data access object for webservice info data.
 * This info is used to verify parallel running webservices and nominate one as master.
 * It shows also the history of SEB webservice registrations that has not been correctly shot down and still remain in
 * the persistent data table. */
public interface WebserviceInfoDAO {

    boolean isInitialized();

    /** Register a SEB webservice within the persistent storage
     *
     * @param uuid A unique identifier that was generated by the webservice on startup
     * @param address the IP address of the webservice
     * @return true if registration was successful */
    boolean register(String uuid, String address);

    /** This can periodically be called by a specific running webservice to verify whether the webservice is (still) the
     * master or a slave
     *
     * @param uuid The unique identifier of the webservice generated on startup
     * @return true if the calling webservice is (still) the master service */
    boolean isMaster(String uuid);

    /** When a webservice has a controlled shout down, it unregister itself within this method.
     * This removes the data entry of the webservice from persistent storage.
     *
     * @param uuid he unique identifier of the webservice generated on startup
     * @return true when the unregistering was successful */
    boolean unregister(String uuid);

}
