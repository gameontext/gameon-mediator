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

	@Resource( lookup = "java:comp/DefaultManagedScheduledExecutorService" )
  ManagedScheduledExecutorService executor;

	public Observable<GameOnEvent> consume() {
		System.out.println("RXJava Observable ["+this.hashCode()+"] consume invoked. Obtaining dataProvider.");
		KafkaRxAsyncDataProvider dp = dataProvider.get();
		Action0 unsubscribeHandler = new Action0() {
			@Override
			public void call(){
				System.out.println("RXJava Observable ["+KafkaRxJavaObservable.this.hashCode()+"] unsubscribe called, shutting down dataProvider.");
				dp.shutdown();
				System.out.println("RXJava Observable ["+KafkaRxJavaObservable.this.hashCode()+"] dataProvider shutdown complete.");
			}
		};

		OnSubscribe<GameOnEvent> os = AsyncOnSubscribe.createStateless(dp.getCallback(),unsubscribeHandler);

		//move subscribers to a new thread so they don't block on the data.
		//if we hook unsubscribe here, we can unblock our polling waits.. 
		//if not, then they expire when the next event comes in.
		Observable<GameOnEvent> goo = Observable
		.create(os)
		//.doOnUnsubscribe(() -> unsubscribeHandler.call())
		.subscribeOn(Schedulers.from(executor));
		return goo;
	}

}
