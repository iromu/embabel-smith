package com.embabel.smith.studio.improve;

import com.embabel.smith.builder.publish.PublishResult;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for W1: the pending map is reachable from the scheduled loop tick, the
 * REST trigger, and the REST listing/approve/reject threads at once, so its structural
 * mutations must be guarded against concurrent readers and writers.
 */
class ApprovalQueueConcurrencyTest {

	private static final int WORKER_THREADS = 8;

	private static final int PER_THREAD = 200;

	@Test
	void concurrentEnqueueAndDecideIsSafe() {
		ApprovalQueue queue = new ApprovalQueue();
		var published = new CopyOnWriteArrayList<PendingCandidate>();
		ApprovalQueue.LoopPublisher publisher = c -> {
			published.add(c);
			return new PublishResult(Path.of("/tmp/" + c.id()), false);
		};

		CountDownLatch start = new CountDownLatch(1);
		AtomicBoolean workersDone = new AtomicBoolean(false);
		Set<Throwable> failures = ConcurrentHashMap.newKeySet();
		var pool = Executors.newFixedThreadPool(WORKER_THREADS + 1);

		// One reader hammers the read paths while the workers mutate the queue.
		Future<?> reader = pool.submit(() -> {
			awaitQuietly(start);
			while (!workersDone.get()) {
				queue.pending();
				queue.size();
				queue.get("t0-0");
			}
		});

		var futures = new ConcurrentLinkedQueue<Future<?>>();
		for (int t = 0; t < WORKER_THREADS; t++) {
			final int thread = t;
			futures.add(pool.submit(() -> {
				awaitQuietly(start);
				for (int i = 0; i < PER_THREAD; i++) {
					queue.enqueue(TestFixtures.candidate("t" + thread + "-" + i, "extract"), false, publisher);
				}
				for (int i = 0; i < PER_THREAD; i++) {
					String id = "t" + thread + "-" + i;
					if (i % 2 == 0) {
						queue.approve(id, publisher);
					}
					else {
						queue.reject(id);
					}
				}
			}));
		}

		start.countDown();
		for (Future<?> future : futures) {
			try {
				future.get(2, TimeUnit.MINUTES);
			}
			catch (Exception e) {
				failures.add(e);
			}
		}
		workersDone.set(true);
		try {
			reader.get(2, TimeUnit.MINUTES);
		}
		catch (Exception e) {
			failures.add(e);
		}
		pool.shutdownNow();

		assertThat(failures).isEmpty();

		// Every enqueued id ended up approved or rejected: the queue is empty and the
		// published set is exactly the even-indexed ids.
		assertThat(queue.size()).isZero();
		assertThat(queue.pending()).isEmpty();
		Set<String> publishedIds = new HashSet<>();
		for (PendingCandidate c : published) {
			publishedIds.add(c.id());
		}
		Set<String> expected = new HashSet<>();
		for (int t = 0; t < WORKER_THREADS; t++) {
			for (int i = 0; i < PER_THREAD; i += 2) {
				expected.add("t" + t + "-" + i);
			}
		}
		assertThat(publishedIds).containsExactlyInAnyOrderElementsOf(expected);
	}

	private static void awaitQuietly(CountDownLatch latch) {
		try {
			latch.await();
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

}
