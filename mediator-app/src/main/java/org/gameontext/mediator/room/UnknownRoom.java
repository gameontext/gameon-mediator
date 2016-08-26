/*******************************************************************************
 * Copyright (c) 2016 IBM Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package org.gameontext.mediator.room;

import org.gameontext.mediator.MapClient;
import org.gameontext.mediator.MediatorNexus;
import org.gameontext.mediator.models.RoomInfo;
import org.gameontext.mediator.models.Site;

public class UnknownRoom extends AbstractRoomMediator {

    public static final String UNKNOWN_NAME = "unknown";
    public static final String UNKNOWN_FULLNAME = "Unknown site";
    public static final String UNKNOWN_DESCRIPTION = "Operator here: We're sorry, but we have no idea where you are. `/sos` is your best option";

    public UnknownRoom(MapClient mapClient, String id, MediatorNexus.View nexus) {
        super(nexus, mapClient, new Site(id));
    }

    @Override
    public String getName() {
        return UNKNOWN_NAME;
    }

    @Override
    public String getFullName() {
        return UNKNOWN_FULLNAME;
    }

    @Override
    public String getDescription() {
        return UNKNOWN_DESCRIPTION;
    }

    @Override
    public Type getType() {
        return Type.UNKNOWN;
    }

    @Override
    public void updateInformation(Site site) {
        super.exits = site.getExits();
        roomInfo = site.getInfo();
    }

    @Override
    public boolean sameConnectionDetails(RoomInfo info) {
        return info == null;
    }
}
