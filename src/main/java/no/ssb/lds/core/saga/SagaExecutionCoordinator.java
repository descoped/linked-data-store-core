package no.ssb.lds.core.saga;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import no.ssb.concurrent.futureselector.SelectableFuture;
import no.ssb.concurrent.futureselector.SelectableThreadPoolExectutor;
import no.ssb.lds.api.persistence.json.JsonTools;
import no.ssb.saga.api.Saga;
import no.ssb.saga.execution.SagaExecution;
import no.ssb.saga.execution.SagaHandoffControl;
import no.ssb.saga.execution.SagaHandoffResult;
import no.ssb.saga.execution.adapter.AdapterLoader;
import no.ssb.sagalog.SagaLog;
import no.ssb.sagalog.SagaLogEntry;
import no.ssb.sagalog.SagaLogPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static java.util.stream.Collectors.groupingBy;
import static no.ssb.lds.api.persistence.json.JsonTools.mapper;

public class SagaExecutionCoordinator {

    private static final Logger LOG = LoggerFactory.getLogger(SagaExecutionCoordinator.class);

    final int numberOfSagaLogs;
    final SagaLogPool sagaLogPool;
    final BlockingQueue<String> availableLogIds = new LinkedBlockingQueue<>();
    final Map<String, SagaLog> sagaLogBySagaLogId = new ConcurrentHashMap<>();
    final Map<String, String> executionIdBySagaLogId = new ConcurrentHashMap<>();

    final SagaRepository sagaRepository;
    final SagasObserver sagasObserver;
    final SelectableThreadPoolExectutor threadPool;
    final Semaphore semaphore;
    final ThreadPoolWatchDog threadPoolWatchDog;

    public SagaExecutionCoordinator(SagaLogPool sagaLogPool, int numberOfSagaLogs, SagaRepository sagaRepository, SagasObserver sagasObserver, SelectableThreadPoolExectutor threadPool) {
        this.sagaLogPool = sagaLogPool;
        this.numberOfSagaLogs = numberOfSagaLogs;
        for (int i = 0; i < numberOfSagaLogs; i++) {
            availableLogIds.add(String.format("%02d", i));
        }
        this.sagaRepository = sagaRepository;
        this.sagasObserver = sagasObserver;
        this.threadPool = threadPool;
        int maxNumberConcurrentSagaExecutions = (threadPool.getMaximumPoolSize() + threadPool.getQueue().remainingCapacity()) / 2;
        this.semaphore = new Semaphore(maxNumberConcurrentSagaExecutions);
        threadPoolWatchDog = new ThreadPoolWatchDog();
    }

    public void startThreadpoolWatchdog() {
        threadPoolWatchDog.start();
    }

    public SagaLogPool getSagaLogPool() {
        return sagaLogPool;
    }

    public SelectableThreadPoolExectutor getThreadPool() {
        return threadPool;
    }

    public SelectableFuture<SagaHandoffResult> handoff(boolean sync, AdapterLoader adapterLoader, Saga saga, String namespace, String entity, String id, ZonedDateTime version, JsonNode data) {
        String versionStr = version.format(DateTimeFormatter.ISO_ZONED_DATE_TIME);
        ObjectNode input = mapper.createObjectNode();
        input.put("namespace", namespace);
        input.put("entity", entity);
        input.put("id", id);
        input.put("version", versionStr);
        input.set("data", data);
        String executionId = UUID.randomUUID().toString();

        String logId = getAvailableLogId();
        SagaLog sagaLog = sagaLogPool.connect(logId);
        executionIdBySagaLogId.compute(logId, (k, v) -> {
            if (v != null) {
                throw new RuntimeException(String.format("executionIdBySagaLogId with key %s is already associated with another executionId %s", k, v));
            }
            return executionId;
        });
        sagaLogBySagaLogId.compute(logId, (k, v) -> {
            if (v != null) {
                throw new RuntimeException(String.format("sagaLogBySagaLogId with key %s is already associated with another value", k));
            }
            return sagaLog;
        });
        SagaExecution sagaExecution = new SagaExecution(sagaLog, threadPool, saga, adapterLoader);

        SagaHandoffControl handoffControl = startSagaExecutionWithThrottling(sagaExecution, input, executionId, logId);

        sagasObserver.registerSaga(handoffControl);
        SelectableFuture<SagaHandoffResult> future = sync ?
                handoffControl.getCompletionFuture() : // full saga-execution
                handoffControl.getHandoffFuture();     // first saga-log write

        return future;
    }

    private String getAvailableLogId() {
        try {
            return availableLogIds.take();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private SagaHandoffControl startSagaExecutionWithThrottling(SagaExecution sagaExecution, JsonNode input, String executionId, String logId) {
        SagaHandoffControl handoffControl;
        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // set interrupt status
            throw new RuntimeException(e);
        }
        AtomicBoolean permitReleased = new AtomicBoolean(false);
        try {
            handoffControl = sagaExecution.executeSaga(executionId, input, false, r -> {
                if (permitReleased.compareAndSet(false, true)) {
                    semaphore.release();
                }
                sagaLogBySagaLogId.remove(logId);
                executionIdBySagaLogId.remove(logId);
                sagaLogPool.release(logId);
                availableLogIds.add(logId);
            });
            // permit will be released by sagaPermitReleaseThread
        } catch (RuntimeException e) {
            if (permitReleased.compareAndSet(false, true)) {
                semaphore.release(); // ensure that permit is always released even when saga-execution could not be run
            }
            sagaLogBySagaLogId.remove(logId);
            executionIdBySagaLogId.remove(logId);
            sagaLogPool.release(logId);
            availableLogIds.add(logId);
            throw e;
        }
        return handoffControl;
    }

    public void shutdown() {
        threadPoolWatchDog.shutdown();
    }

    public CompletableFuture completeIncompleteSagas() {
        Collection<String> logIds = new LinkedList<>();
        availableLogIds.drainTo(logIds);
        CompletableFuture<Void>[] allFutures = new CompletableFuture[logIds.size()];
        int i = 0;
        for (String logId : logIds) {
            SagaLog sagaLog = sagaLogPool.connect(logId);
            Map<String, List<SagaLogEntry>> entriesByExecutionId = sagaLog.readIncompleteSagas().collect(groupingBy(SagaLogEntry::getExecutionId));
            CompletableFuture<Void>[] futures = new CompletableFuture[entriesByExecutionId.size()];
            int j = 0;
            for (Map.Entry<String, List<SagaLogEntry>> entry : entriesByExecutionId.entrySet()) {
                String executionId = entry.getKey();
                Map<String, List<SagaLogEntry>> entriesByNodeId = entry.getValue().stream().collect(groupingBy(SagaLogEntry::getNodeId));
                futures[j++] = startSagaForwardRecovery(executionId, entriesByNodeId, sagaLog);
            }
            allFutures[i++] = CompletableFuture.allOf(futures)
                    .thenRun(() -> sagaLog.truncate())
                    .thenRun(() -> availableLogIds.add(logId))
                    .thenRun(() -> sagaLogPool.release(logId));
        }
        return CompletableFuture.allOf(allFutures);
    }

    private CompletableFuture startSagaForwardRecovery(String executionId, Map<String, List<SagaLogEntry>> entriesByNodeId, SagaLog sagaLog) {
        List<SagaLogEntry> sagaLogEntries = entriesByNodeId.get(Saga.ID_START);
        SagaLogEntry startSagaEntry = sagaLogEntries.get(0);
        Saga saga = sagaRepository.get(startSagaEntry.getSagaName());
        AdapterLoader adapterLoader = sagaRepository.getAdapterLoader();
        JsonNode sagaInput = JsonTools.toJsonNode(startSagaEntry.getJsonData());
        SagaExecution sagaExecution = new SagaExecution(sagaLog, threadPool, saga, adapterLoader);
        CompletableFuture<Void> future = new CompletableFuture<>();
        SagaHandoffControl handoffControl = sagaExecution.executeSaga(executionId, sagaInput, true, r -> future.complete(null));
        LOG.info("Started recovery of saga with executionId: {}", executionId);
        sagasObserver.registerSaga(handoffControl);
        return future;
    }

    /**
     * Regulary controls thread-pool to see if it is deadlocked, i.e. all threads are occupied in saga-traversal or
     * otherwise waiting for more available saga threadpool workers.
     */
    class ThreadPoolWatchDog extends Thread {

        public static final int WATCHDOG_INTERVAL_SEC = 1;

        final AtomicLong deadlockResolutionAttemptCounter = new AtomicLong(0);
        final CountDownLatch doneSignal = new CountDownLatch(1);

        public ThreadPoolWatchDog() {
            super("Saga-Threadpool-Watchdog");
        }

        void shutdown() {
            doneSignal.countDown();
        }

        @Override
        public void run() {
            try {
                BlockingQueue<Runnable> queue = threadPool.getQueue();
                int previousActiveCount = -1;
                long previousCompletedTaskCount = -1;
                while (!doneSignal.await(WATCHDOG_INTERVAL_SEC, TimeUnit.SECONDS)) {
                    // check saga-thread-pool for possible deadlock
                    if (possibleDeadlock(previousActiveCount, previousCompletedTaskCount)) {
                        int emptyTasksToSubmit = (threadPool.getMaximumPoolSize() - threadPool.getPoolSize()) / 2 + queue.remainingCapacity();
                        // Submit a number of empty tasks to thread-pool in order to force the work-queue to overflow.
                        // This should force around half the remaining thread-pool-capacity (with regards to max-size) to become available
                        LOG.info("Submitting {} empty-tasks in an attempt to resolve a potential saga-deadlock due to mismatch between workload and thread-pool configuration. Threadpool-state: {}", emptyTasksToSubmit, threadPool.toString());
                        for (int i = 0; i < emptyTasksToSubmit; i++) {
                            threadPool.submit(() -> {
                            });
                        }
                        deadlockResolutionAttemptCounter.incrementAndGet();
                    }
                    previousCompletedTaskCount = threadPool.getCompletedTaskCount();
                    previousActiveCount = threadPool.getActiveCount();
                }
            } catch (InterruptedException e) {
                LOG.warn("Saga threadpool watchdog interrupted and died.");
            } catch (Throwable t) {
                LOG.warn("", t);
            }
        }

        boolean possibleDeadlock(int previousActiveCount, long previousCompletedTaskCount) {
            return threadPool.getActiveCount() > 0 // at least one active thread
                    && threadPool.getActiveCount() == threadPool.getPoolSize() // all threads in pool are active
                    && threadPool.getPoolSize() < threadPool.getMaximumPoolSize() // pool can still grow
                    && threadPool.getActiveCount() == previousActiveCount // no recent change in activity
                    && threadPool.getCompletedTaskCount() == previousCompletedTaskCount; // no more tasks have completed since previous check
        }
    }
}
