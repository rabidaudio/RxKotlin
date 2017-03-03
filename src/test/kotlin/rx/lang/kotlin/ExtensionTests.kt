/**
 * Copyright 2013 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package rx.lang.kotlin

import org.funktionale.partials.invoke
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.mockito.Mockito.any
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import rx.Notification
import rx.Observable
import rx.Subscriber
import rx.schedulers.TestScheduler
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * This class contains tests using the extension functions provided by the language adaptor.
 */
class ExtensionTests : KotlinTests() {


    @Test fun testCreate() {
        Observable.create<String> { subscriber ->
            subscriber.onNext("Hello")
            subscriber.onCompleted()
        }.subscribe { result ->
            a.received(result)
        }

        verify(a, times(1)).received("Hello")
    }

    @Test fun testFilter() {
        listOf(1, 2, 3).toObservable().filter { it >= 2 }.subscribe(received())
        verify(a, times(0)).received(1)
        verify(a, times(1)).received(2)
        verify(a, times(1)).received(3)
    }


    @Test fun testLast() {
        assertEquals("three", listOf("one", "two", "three").toObservable().toBlocking().last())
    }

    @Test fun testLastWithPredicate() {
        assertEquals("two", listOf("one", "two", "three").toObservable().toBlocking().last { x -> x.length == 3 })
    }

    @Test fun testMap2() {
        listOf(1, 2, 3).toObservable().map { v -> "hello_$v" }.subscribe(received())
        verify(a, times(1)).received("hello_1")
        verify(a, times(1)).received("hello_2")
        verify(a, times(1)).received("hello_3")
    }

    @Test fun testMaterialize() {
        listOf(1, 2, 3).toObservable().materialize().subscribe(received())
        verify(a, times(4)).received(any(Notification::class.java))
        verify(a, times(0)).error(any(Exception::class.java))
    }


    @Test fun testMerge() {
        val observables = listOf(
                Observable.just(6),
                NullPointerException().toObservable<Int>(),
                Observable.just(7)
        ).merge()
        listOf(
                listOf(1, 2, 3).toObservable(),
                observables,
                listOf(4, 5).toObservable()
        ).merge()
                .subscribe(received()) { e -> a.error(e) }
        verify(a, times(1)).received(1)
        verify(a, times(1)).received(2)
        verify(a, times(1)).received(3)
        verify(a, times(0)).received(4)
        verify(a, times(0)).received(5)
        verify(a, times(1)).received(6)
        verify(a, times(0)).received(7)
        verify(a, times(1)).error(any(NullPointerException::class.java))
    }

    @Test fun testScriptWithMaterialize() {
        TestFactory().observable.materialize().subscribe(received())
        verify(a, times(2)).received(any(Notification::class.java))
    }

    @Test fun testScriptWithMerge() {
        val factory = TestFactory()
        (factory.observable.mergeWith(factory.observable)).subscribe((received()))
        verify(a, times(1)).received("hello_1")
        verify(a, times(1)).received("hello_2")
    }


    @Test fun testFromWithIterable() {
        assertEquals(5, listOf(1, 2, 3, 4, 5).toObservable().count().toBlocking().single())
    }

    @Test fun testStartWith() {
        val list = listOf(10, 11, 12, 13, 14)
        val startList = listOf(1, 2, 3, 4, 5)
        assertEquals(6, list.toObservable().startWith(0).count().toBlocking().single())
        assertEquals(10, list.toObservable().startWith(startList).count().toBlocking().single())
    }

    @Test fun testScriptWithOnNext() {
        TestFactory().observable.subscribe(received())
        verify(a, times(1)).received("hello_1")
    }

    @Test fun testSkipTake() {
        listOf(1, 2, 3).toObservable().skip(1).take(1).subscribe(received())
        verify(a, times(0)).received(1)
        verify(a, times(1)).received(2)
        verify(a, times(0)).received(3)
    }

    @Test fun testSkip() {
        listOf(1, 2, 3).toObservable().skip(2).subscribe(received())
        verify(a, times(0)).received(1)
        verify(a, times(0)).received(2)
        verify(a, times(1)).received(3)
    }

    @Test fun testTake() {
        listOf(1, 2, 3).toObservable().take(2).subscribe(received())
        verify(a, times(1)).received(1)
        verify(a, times(1)).received(2)
        verify(a, times(0)).received(3)
    }

    @Test fun testTakeLast() {
        TestFactory().observable.takeLast(1).subscribe(received())
        verify(a, times(1)).received("hello_1")
    }

    @Test fun testTakeWhile() {
        listOf(1, 2, 3).toObservable().takeWhile { x -> x < 3 }.subscribe(received())
        verify(a, times(1)).received(1)
        verify(a, times(1)).received(2)
        verify(a, times(0)).received(3)
    }

    @Test fun testTakeWhileWithIndex() {
        listOf(1, 2, 3).toObservable().takeWhile { x -> x < 3 }.zipWith((0..Integer.MAX_VALUE).toObservable()) { x, i -> x }.subscribe(received())
        verify(a, times(1)).received(1)
        verify(a, times(1)).received(2)
        verify(a, times(0)).received(3)
    }

    @Test fun testToSortedList() {
        TestFactory().numbers.toSortedList().subscribe(received())
        verify(a, times(1)).received(listOf(1, 2, 3, 4, 5))
    }

    @Test fun testForEach() {
        Observable.create(asyncObservable).toBlocking().forEach(received())
        verify(a, times(1)).received(1)
        verify(a, times(1)).received(2)
        verify(a, times(1)).received(3)
    }

    @Test(expected = RuntimeException::class) fun testForEachWithError() {
        Observable.create(asyncObservable).toBlocking().forEach { throw RuntimeException("err") }
        fail("we expect an exception to be thrown")
    }

    @Test fun testLastOrDefault() {
        assertEquals("two", listOf("one", "two").toObservable().toBlocking().lastOrDefault("default") { x -> x.length == 3 })
        assertEquals("default", listOf("one", "two").toObservable().toBlocking().lastOrDefault("default") { x -> x.length > 3 })
    }

    @Test fun testDefer() {
        Observable.defer { listOf(1, 2).toObservable() }.subscribe(received())
        verify(a, times(1)).received(1)
        verify(a, times(1)).received(2)
    }

    @Test fun testAll() {
        listOf(1, 2, 3).toObservable().all { x -> x > 0 }.subscribe(received())
        verify(a, times(1)).received(true)
    }

    @Test fun testZip() {
        val o1 = listOf(1, 2, 3).toObservable()
        val o2 = listOf(4, 5, 6).toObservable()
        val o3 = listOf(7, 8, 9).toObservable()

        val values = Observable.zip(o1, o2, o3) { a, b, c -> listOf(a, b, c) }.toList().toBlocking().single()
        assertEquals(listOf(1, 4, 7), values[0])
        assertEquals(listOf(2, 5, 8), values[1])
        assertEquals(listOf(3, 6, 9), values[2])
    }

    @Test fun testSwitchOnNext() {
        val testScheduler = TestScheduler()
        val worker = testScheduler.createWorker()

        val observable = Observable.create<Observable<Long>> { s ->
            fun at(delay: Long, func: () -> Unit) {
                worker.schedule(
                        { func() },
                        delay,
                        TimeUnit.MILLISECONDS
                )
            }

            val first = Observable.interval(5, TimeUnit.MILLISECONDS, testScheduler).take(3)
            at(0) { s.onNext(first) }

            val second = Observable.interval(5, TimeUnit.MILLISECONDS, testScheduler).take(3)
            at(11) { s.onNext(second) }

            at(40) { s.onCompleted() }
        }

        observable.switchOnNext().subscribe(received())

        val inOrder = inOrder(a)
        testScheduler.advanceTimeTo(10, TimeUnit.MILLISECONDS)
        inOrder.verify(a, times(1)).received(0L)
        inOrder.verify(a, times(1)).received(1L)

        testScheduler.advanceTimeTo(40, TimeUnit.MILLISECONDS)
        inOrder.verify(a, times(1)).received(0L)
        inOrder.verify(a, times(1)).received(1L)
        inOrder.verify(a, times(1)).received(2L)
        inOrder.verifyNoMoreInteractions()
    }

    val funOnSubscribe: (Int, Subscriber<in String>) -> Unit = { counter, subscriber ->
        subscriber.onNext("hello_$counter")
        subscriber.onCompleted()
    }

    val asyncObservable: (Subscriber<in Int>) -> Unit = { subscriber ->
        thread {
            Thread.sleep(50)
            subscriber.onNext(1)
            subscriber.onNext(2)
            subscriber.onNext(3)
            subscriber.onCompleted()
        }
    }


    inner class TestFactory {
        var counter = 1

        val numbers: Observable<Int>
            get() = listOf(1, 3, 2, 5, 4).toObservable()

        val onSubscribe: (Subscriber<in String>) -> Unit
            get() = funOnSubscribe(p1 = counter++) // partial applied function

        val observable: Observable<String>
            get() = Observable.create(onSubscribe)

    }
}
