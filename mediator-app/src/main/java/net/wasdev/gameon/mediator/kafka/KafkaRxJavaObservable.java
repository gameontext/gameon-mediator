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
package net.wasdev.gameon.mediator.kafka;

import javax.annotation.Resource;
import javax.enterprise.concurrent.ManagedScheduledExecutorService;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.inject.Provider;

import rx.Observable;
import rx.Observable.OnSubscribe;
import rx.functions.Action0;
import rx.observables.AsyncOnSubscribe;
import rx.schedulers.Schedulers;

@ApplicationScoped
public class KafkaRxJavaObservable {

    @Inject
    private Provider<KafkaRxAsyncDataProvider> dataProvider;

    @Resource
    ManagedScheduledExecutorService executor;

    public Observable<GameOnEvent> consume() {
        System.out.println("RXJava Observable [" + this.hashCode() + "] consume invoked. Obtaining dataProvider.");
        KafkaRxAsyncDataProvider dp = dataProvider.get();
        Action0 unsubscribeHandler = new Action0() {
            @Override
            public void call() {
                System.out.println("RXJava Observable [" + KafkaRxJavaObservable.this.hashCode()
                        + "] unsubscribe called, shutting down dataProvider.");
                dp.shutdown();
                System.out.println("RXJava Observable [" + KafkaRxJavaObservable.this.hashCode()
                        + "] dataProvider shutdown complete.");
            }
        };

        OnSubscribe<GameOnEvent> os = AsyncOnSubscribe.createStateless(dp.getCallback(), unsubscribeHandler);

        // move subscribers to a new thread so they don't block on the data.
        // if we hook unsubscribe here, we can unblock our polling waits..
        // if not, then they expire when the next event comes in.
        Observable<GameOnEvent> goo = Observable.create(os)
                // .doOnUnsubscribe(() -> unsubscribeHandler.call())
                .subscribeOn(Schedulers.from(executor));
        return goo;
    }

}
