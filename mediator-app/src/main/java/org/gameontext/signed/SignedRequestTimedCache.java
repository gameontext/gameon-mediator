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
package org.gameontext.signed;

import java.time.Duration;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

import javax.annotation.Resource;
import javax.enterprise.concurrent.ManagedExecutorService;
import javax.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class SignedRequestTimedCache implements Runnable {

    @Resource
    ManagedExecutorService managedExecutorService;

    /** number of requests before a cleanup is triggered */
    final static int TRIGGER_CLEANUP_DEPTH = 1000;

    /** this map contains all the received messages, it is thread safe */
    protected ConcurrentHashMap<String,TimestampedKey> requests = new ConcurrentHashMap<>();
    protected AtomicInteger triggerCount = new AtomicInteger(0);

    public boolean isDuplicate(String hmac, Duration expiresIn) {
        int count = triggerCount.get();
        // if the count is over or above the cutoff point, try to clean up expired sessions
        // avoid using size() on concurrent maps.
        if ( count >= TRIGGER_CLEANUP_DEPTH && triggerCount.compareAndSet(count, 0) ) {
            managedExecutorService.execute(this);
        }

        TimestampedKey t = new TimestampedKey(hmac, expiresIn);
        if (requests.putIfAbsent(hmac, t) == null) {
            triggerCount.incrementAndGet();
            return false;
        } else {
            return true; // duplicate
        }
    }

    @Override
    public void run() {
        SignedRequestFeature.writeLog(Level.INFO,this,"Clearing expired hmacs");

        for ( Entry<String, TimestampedKey> request : requests.entrySet() ) {
            if ( request.getValue().hasExpired() ) {
                requests.remove(request.getKey());
            } else {
                // ConcurrentSkipListMap keeps them in sorted order by time
                // stop as soon as we find a not expired one.
                break;
            }
        }
    }
}
