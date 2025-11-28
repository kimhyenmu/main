package com.example.assignment;

import com.example.assignment.service.*;
import com.example.assignment.storage.CacheStorage;
import com.example.assignment.storage.DatabaseStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 멀티스레드 환경에서의 조회수 증가 테스트
 * 각 서비스별로 race condition 발생 여부와 성능을 측정
 */
@SpringBootTest
public class ConcurrencyTest {

    @Autowired
    private CacheStorage cacheStorage;

    @Autowired
    private DatabaseStorage databaseStorage;

    @Autowired
    private BasicViewService basicViewService;

    @Autowired
    private GlobalLockViewService globalLockViewService;

    @Autowired
    private PerPostLockViewService perPostLockViewService;

    @Autowired
    private AtomicViewService atomicViewService;

    @Autowired
    private WriteThroughViewService writeThroughViewService;

    @BeforeEach
    public void setup() {
        cacheStorage.clear();
        databaseStorage.clear();
    }

    @Test
    public void testBasicViewService() throws InterruptedException {
        System.out.println("\n========================================");
        System.out.println("1. 기본 서비스 테스트 (문제 발생 버전)");
        System.out.println("========================================");
        
        basicViewService.reset();
        runConcurrencyTest(
            "Basic View Service",
            100,  // 스레드 수
            50,   // 각 스레드당 호출 횟수
            1L,   // 게시글 ID
            () -> basicViewService.incrementViewCount(1L),
            () -> basicViewService.getViewCount(1L),
            () -> basicViewService.getViewCountFromDb(1L)
        );
    }

    @Test
    public void testGlobalLockViewService() throws InterruptedException {
        System.out.println("\n========================================");
        System.out.println("2. 전역 락 서비스 테스트");
        System.out.println("========================================");
        
        globalLockViewService.reset();
        runConcurrencyTest(
            "Global Lock View Service",
            100,
            50,
            1L,
            () -> globalLockViewService.incrementViewCount(1L),
            () -> globalLockViewService.getViewCount(1L),
            () -> globalLockViewService.getViewCountFromDb(1L)
        );
    }

    @Test
    public void testPerPostLockViewService() throws InterruptedException {
        System.out.println("\n========================================");
        System.out.println("3. 게시글별 락 서비스 테스트");
        System.out.println("========================================");
        
        perPostLockViewService.reset();
        runConcurrencyTest(
            "Per-Post Lock View Service",
            100,
            50,
            1L,
            () -> perPostLockViewService.incrementViewCount(1L),
            () -> perPostLockViewService.getViewCount(1L),
            () -> perPostLockViewService.getViewCountFromDb(1L)
        );
    }

    @Test
    public void testAtomicViewService() throws InterruptedException {
        System.out.println("\n========================================");
        System.out.println("4. 원자적 연산 서비스 테스트");
        System.out.println("========================================");
        
        atomicViewService.reset();
        runConcurrencyTest(
            "Atomic View Service",
            100,
            50,
            1L,
            () -> atomicViewService.incrementViewCount(1L),
            () -> atomicViewService.getViewCount(1L),
            () -> atomicViewService.getViewCountFromDb(1L)
        );
    }

    @Test
    public void testWriteThroughViewService() throws InterruptedException {
        System.out.println("\n========================================");
        System.out.println("5. Write-Through 서비스 테스트");
        System.out.println("========================================");
        
        writeThroughViewService.reset();
        runConcurrencyTest(
            "Write-Through View Service",
            100,
            50,
            1L,
            () -> writeThroughViewService.incrementViewCount(1L),
            () -> writeThroughViewService.getViewCount(1L),
            () -> writeThroughViewService.getViewCountFromDb(1L)
        );
    }

    @Test
    public void testMultiplePostsConcurrency() throws InterruptedException {
        System.out.println("\n========================================");
        System.out.println("6. 여러 게시글 동시 처리 테스트 (게시글별 락 vs 전역 락)");
        System.out.println("========================================");
        
        // 전역 락: 모든 게시글이 순차 처리됨
        globalLockViewService.reset();
        long globalLockTime = measureMultiplePostsPerformance(
            "Global Lock (모든 게시글 순차 처리)",
            50, // 스레드 수
            20, // 각 스레드당 호출 횟수
            postId -> globalLockViewService.incrementViewCount(postId)
        );

        // 게시글별 락: 다른 게시글은 병렬 처리 가능
        perPostLockViewService.reset();
        long perPostLockTime = measureMultiplePostsPerformance(
            "Per-Post Lock (게시글별 병렬 처리)",
            50,
            20,
            postId -> perPostLockViewService.incrementViewCount(postId)
        );

        System.out.println("\n성능 비교:");
        System.out.println("전역 락 소요 시간: " + globalLockTime + "ms");
        System.out.println("게시글별 락 소요 시간: " + perPostLockTime + "ms");
        System.out.println("성능 향상: " + String.format("%.2f", (double) globalLockTime / perPostLockTime) + "배");
    }

    private void runConcurrencyTest(
            String serviceName,
            int threadCount,
            int callsPerThread,
            Long postId,
            Runnable incrementTask,
            Supplier<Long> getCacheCount,
            Supplier<Long> getDbCount
    ) throws InterruptedException {
        
        int expectedCount = threadCount * callsPerThread;
        
        System.out.println("\n[테스트 설정]");
        System.out.println("서비스: " + serviceName);
        System.out.println("스레드 수: " + threadCount);
        System.out.println("각 스레드당 호출 횟수: " + callsPerThread);
        System.out.println("총 호출 횟수 (기대값): " + expectedCount);

        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        long startTime = System.currentTimeMillis();

        // 모든 스레드를 동시에 시작
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    for (int j = 0; j < callsPerThread; j++) {
                        incrementTask.run();
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                    System.err.println("Error: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        // 모든 스레드가 완료될 때까지 대기
        latch.await(30, TimeUnit.SECONDS);
        executorService.shutdown();
        executorService.awaitTermination(30, TimeUnit.SECONDS);

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // 결과 확인
        Long cacheViewCount = getCacheCount.get();
        Long dbViewCount = getDbCount.get();

        System.out.println("\n[테스트 결과]");
        System.out.println("소요 시간: " + duration + "ms");
        System.out.println("성공한 호출: " + successCount.get() + " / " + expectedCount);
        System.out.println("실패한 호출: " + errorCount.get());
        System.out.println("\n[조회수 비교]");
        System.out.println("기대값: " + expectedCount);
        System.out.println("캐시 조회수: " + cacheViewCount);
        System.out.println("DB 조회수: " + dbViewCount);
        
        long cacheLoss = expectedCount - cacheViewCount;
        long dbLoss = expectedCount - dbViewCount;
        boolean cacheDbMismatch = !cacheViewCount.equals(dbViewCount);

        System.out.println("\n[문제 분석]");
        if (cacheLoss > 0) {
            System.out.println("⚠️  캐시 손실: " + cacheLoss + " (" + 
                String.format("%.2f", (double) cacheLoss / expectedCount * 100) + "%)");
        } else {
            System.out.println("✅ 캐시 손실 없음");
        }

        if (dbLoss > 0) {
            System.out.println("⚠️  DB 손실: " + dbLoss + " (" + 
                String.format("%.2f", (double) dbLoss / expectedCount * 100) + "%)");
        } else {
            System.out.println("✅ DB 손실 없음");
        }

        if (cacheDbMismatch) {
            System.out.println("⚠️  캐시-DB 불일치: " + Math.abs(cacheViewCount - dbViewCount));
        } else {
            System.out.println("✅ 캐시-DB 일치");
        }

        if (cacheLoss == 0 && dbLoss == 0 && !cacheDbMismatch) {
            System.out.println("\n🎉 완벽한 결과! Race Condition이 해결되었습니다.");
        } else {
            System.out.println("\n⚠️  Race Condition 발생! 조회수 불일치가 발견되었습니다.");
        }
    }

    private long measureMultiplePostsPerformance(
            String serviceName,
            int threadCount,
            int callsPerThread,
            java.util.function.Consumer<Long> incrementTask
    ) throws InterruptedException {
        
        System.out.println("\n[" + serviceName + "]");
        System.out.println("3개의 게시글에 대해 동시 요청 처리");

        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            executorService.submit(() -> {
                try {
                    for (int j = 0; j < callsPerThread; j++) {
                        // 스레드를 3개 게시글에 분산
                        Long postId = (long) ((threadIndex % 3) + 1);
                        incrementTask.accept(postId);
                    }
                } catch (Exception e) {
                    System.err.println("Error: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        executorService.shutdown();
        executorService.awaitTermination(30, TimeUnit.SECONDS);

        long endTime = System.currentTimeMillis();
        return endTime - startTime;
    }

    @FunctionalInterface
    interface Supplier<T> {
        T get();
    }
}
