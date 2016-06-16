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
package net.wasdev.gameon.mediator.room;

import javax.json.JsonValue;

import net.wasdev.gameon.mediator.MediatorNexus;
import net.wasdev.gameon.mediator.RoutedMessage;
import net.wasdev.gameon.mediator.models.Exit;
import net.wasdev.gameon.mediator.models.Exits;
import net.wasdev.gameon.mediator.models.RoomInfo;
import net.wasdev.gameon.mediator.models.Site;

public interface RoomMediator {

    enum Type {
        CONNECTING,
        EMPTY,
        REMOTE,
        SICK,
        UNKNOWN,
        FIRST_ROOM;
    }

    Type getType();

    String getId();

    String getName();

    String getFullName();

    String getDescription();

    Exits getExits();

    JsonValue listExits();

    void updateInformation(Site site);

    Exit getEmergencyReturnExit();

    void hello(MediatorNexus.UserView user, boolean recovery);

    void goodbye(MediatorNexus.UserView user);

    void join(MediatorNexus.UserView user);

    void part(MediatorNexus.UserView user);

    void sendToRoom(RoutedMessage message);

    void sendToClients(RoutedMessage message);

    void disconnect();

    boolean sameConnectionDetails(RoomInfo localInfo);

    MediatorNexus.View getNexusView();

    RoutedMessage getLocationEventMessage(MediatorNexus.UserView user);
}
