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
package org.gameontext.mediator;

import javax.inject.Inject;
import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.GET;
import javax.ws.rs.core.Response;

import org.gameontext.mediator.kafka.KafkaCDIBridge;

@WebServlet("/health")
public class Health extends HttpServlet {

    private static final long serialVersionUID = 1L;

    @Inject
    KafkaCDIBridge cdiBridge;

    @Inject
    MapClient mapClient;

    @GET
    public Response basicGet() {
        return Response.ok().build();
    }

   	/**
     * @throws java.io.IOException
     * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse
     *      response)
     */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if ( cdiBridge.isHealthy() ) {
            response.setStatus(200);
            response.getWriter().append("{\"status\":\"UP\"}");
        } else {
            response.setStatus(503);
            response.getWriter().append("{\"status\":\"DOWN\"}");
        }
   }
}
