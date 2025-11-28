# 구현 상세 설명

## 📦 핵심 컴포넌트

### 1. 도메인 모델

#### Post.java
```java
public class Post {
    private Long id;
    private String title;
    private Long viewCount;
}
```
- 게시글의 기본 정보를 담는 도메인 모델
- ID, 제목, 조회수로 구성

### 2. 저장소 시뮬레이션

#### CacheStorage.java
```java
@Component
public class CacheStorage {
    private final ConcurrentHashMap<Long, Long> cache = new ConcurrentHashMap<>();
}
```
- Redis 등의 캐시를 시뮬레이션
- `ConcurrentHashMap`으로 구현하여 기본적인 스레드 안전성 제공
- 하지만 읽기-수정-쓰기 연산은 원자적이지 않음

#### DatabaseStorage.java
```java
@Component
public class DatabaseStorage {
    private final ConcurrentHashMap<Long, Post> database = new ConcurrentHashMap<>();
}
```
- 실제 DB를 시뮬레이션
- `Thread.sleep(1)`로 DB 저장 지연 시뮬레이션
- Deep copy를 통해 실제 DB의 격리성 재현

## 🔧 서비스 구현 상세

### 1. BasicViewService - 문제 발생 버전

**핵심 코드:**
```java
public Long incrementViewCount(Long postId) {
    // (1) 캐시에서 조회수 읽기
    Long viewCount = cacheStorage.get(postId);
    
    // (2) 캐시 미스 처리
    if (viewCount == null) {
        Post post = databaseStorage.findById(postId);
        viewCount = post.getViewCount();
        Thread.sleep(1); // ⚠️ 타이밍 이슈 발생 지점
        cacheStorage.put(postId, viewCount);
    }
    
    // (3) 조회수 증가 (⚠️ 비원자적 연산)
    Long newViewCount = viewCount + 1;
    
    Thread.sleep(2); // ⚠️ Race condition 발생 지점
    
    // (4) DB 저장
    Post post = databaseStorage.findById(postId);
    post.setViewCount(newViewCount);
    databaseStorage.save(post);
    
    // (5) 캐시 저장
    cacheStorage.put(postId, newViewCount);
    
    return newViewCount;
}
```

**발생하는 문제:**
1. **Lost Update**: 읽기-수정-쓰기가 원자적이지 않음
2. **캐시 미스 중복**: 여러 스레드가 동시에 DB에서 읽음
3. **타이밍 이슈**: 지연으로 인해 문제가 더 자주 발생

### 2. GlobalLockViewService - 전역 락

**핵심 코드:**
```java
private final Object lock = new Object();

public Long incrementViewCount(Long postId) {
    synchronized (lock) {  // ✅ 모든 연산을 원자적으로 처리
        Long viewCount = cacheStorage.get(postId);
        
        if (viewCount == null) {
            Post post = databaseStorage.findById(postId);
            viewCount = post.getViewCount();
            cacheStorage.put(postId, viewCount);
        }
        
        Long newViewCount = viewCount + 1;
        
        Post post = databaseStorage.findById(postId);
        post.setViewCount(newViewCount);
        databaseStorage.save(post);
        
        cacheStorage.put(postId, newViewCount);
        
        return newViewCount;
    }
}
```

**특징:**
- ✅ 간단하고 확실한 동기화
- ❌ 모든 게시글이 하나의 락을 공유 → 병렬 처리 불가
- ❌ 성능 병목 발생

### 3. PerPostLockViewService - 게시글별 락

**핵심 코드:**
```java
private final ConcurrentHashMap<Long, Object> locks = new ConcurrentHashMap<>();

public Long incrementViewCount(Long postId) {
    Object lock = locks.computeIfAbsent(postId, k -> new Object());
    
    synchronized (lock) {  // ✅ 게시글마다 별도의 락
        // ... 동일한 로직
    }
}
```

**특징:**
- ✅ 게시글마다 독립적인 락
- ✅ 서로 다른 게시글은 병렬 처리 가능
- ⚠️ 락 객체 관리 오버헤드
- 📊 성능: 여러 게시글 처리 시 전역 락보다 2.97배 빠름

**락 세분화 (Lock Granularity) 효과:**
```
전역 락:
Post 1: [========]
Post 2:          [========]
Post 3:                   [========]
총 시간: 1,081ms

게시글별 락:
Post 1: [====]
Post 2: [====]
Post 3: [====]
총 시간: 364ms (2.97배 빠름)
```

### 4. AtomicViewService - 원자적 연산

**핵심 코드:**
```java
private final ConcurrentHashMap<Long, AtomicLong> atomicCache = new ConcurrentHashMap<>();

public Long incrementViewCount(Long postId) {
    AtomicLong viewCount = atomicCache.computeIfAbsent(postId, k -> {
        Post post = databaseStorage.findById(k);
        return new AtomicLong(post.getViewCount());
    });
    
    // ✅ 원자적 증가 (락 없이 CAS 연산 사용)
    long newViewCount = viewCount.incrementAndGet();
    
    // DB 저장
    Post post = databaseStorage.findById(postId);
    post.setViewCount(newViewCount);
    databaseStorage.save(post);
    
    return newViewCount;
}
```

**특징:**
- ✅ 락프리 (Lock-Free) 구현
- ✅ CAS (Compare-And-Swap) 연산으로 원자성 보장
- ✅ 최고의 성능: 72ms (전역 락보다 75배 빠름)
- 🏆 **권장 솔루션**

**AtomicLong의 동작 원리:**
```java
// AtomicLong.incrementAndGet() 내부 동작 (간소화)
public final long incrementAndGet() {
    long current;
    long next;
    do {
        current = get();           // 현재 값 읽기
        next = current + 1;        // 새 값 계산
    } while (!compareAndSet(current, next));  // CAS: 현재 값이 그대로면 새 값으로 변경
    return next;
}
```

### 5. WriteThroughViewService - Write-through 패턴

**핵심 코드:**
```java
private final ConcurrentHashMap<Long, Object> locks = new ConcurrentHashMap<>();

public Long incrementViewCount(Long postId) {
    Object lock = locks.computeIfAbsent(postId, k -> new Object());
    
    synchronized (lock) {
        // ✅ DB를 단일 진실 공급원으로 사용
        Post post = databaseStorage.findById(postId);
        
        Long newViewCount = post.getViewCount() + 1;
        post.setViewCount(newViewCount);
        
        // ✅ DB에 먼저 저장
        databaseStorage.save(post);
        
        // ✅ DB 저장 성공 후에만 캐시 갱신
        cacheStorage.put(postId, newViewCount);
        
        return newViewCount;
    }
}
```

**특징:**
- ✅ DB와 캐시 간 강한 일관성 보장
- ✅ Single Source of Truth 패턴
- ❌ DB 저장 지연이 응답 시간에 직접 영향
- 💡 금융, 재고 관리 등에 적합

**일관성 보장 메커니즘:**
```
1. 항상 DB에서 최신 값 읽기
2. DB에 먼저 쓰기 (Write-Through)
3. DB 저장 성공 후 캐시 갱신
4. 실패 시 캐시 갱신하지 않음 → 캐시-DB 불일치 방지
```

## 🧪 테스트 구현

### ConcurrencyTest.java

**테스트 구조:**
```java
@Test
public void testBasicViewService() throws InterruptedException {
    int threadCount = 100;
    int callsPerThread = 50;
    int expectedCount = threadCount * callsPerThread; // 5,000
    
    ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
    CountDownLatch latch = new CountDownLatch(threadCount);
    
    // 모든 스레드 동시 시작
    for (int i = 0; i < threadCount; i++) {
        executorService.submit(() -> {
            try {
                for (int j = 0; j < callsPerThread; j++) {
                    basicViewService.incrementViewCount(1L);
                }
            } finally {
                latch.countDown();
            }
        });
    }
    
    // 모든 스레드 완료 대기
    latch.await(30, TimeUnit.SECONDS);
    
    // 결과 검증
    Long cacheViewCount = basicViewService.getViewCount(1L);
    Long dbViewCount = basicViewService.getViewCountFromDb(1L);
    
    // 분석 및 출력
    analyzeResults(expectedCount, cacheViewCount, dbViewCount);
}
```

**핵심 테스트 요소:**
1. **CountDownLatch**: 모든 스레드가 완료될 때까지 대기
2. **ExecutorService**: 고정 크기 스레드 풀로 동시성 제어
3. **결과 분석**: 캐시 손실, DB 손실, 캐시-DB 불일치 검증

## 📈 성능 측정 방법

### 1. 단일 게시글 처리 성능
```java
long startTime = System.currentTimeMillis();

// 100개 스레드가 각각 50번 호출
// ... 테스트 실행 ...

long endTime = System.currentTimeMillis();
long duration = endTime - startTime;
```

### 2. 여러 게시글 병렬 처리 성능
```java
for (int i = 0; i < threadCount; i++) {
    final int threadIndex = i;
    executorService.submit(() -> {
        for (int j = 0; j < callsPerThread; j++) {
            // 스레드를 3개 게시글에 분산
            Long postId = (long) ((threadIndex % 3) + 1);
            service.incrementViewCount(postId);
        }
    });
}
```

## 🎯 학습 포인트

### 1. ConcurrentHashMap의 한계
```java
// ❌ 이것은 원자적이지 않습니다!
Long value = map.get(key);
value = value + 1;
map.put(key, value);

// ✅ 이것은 원자적입니다
map.compute(key, (k, v) -> v == null ? 1L : v + 1);

// 🏆 이것이 가장 좋습니다 (AtomicLong 사용)
AtomicLong value = map.computeIfAbsent(key, k -> new AtomicLong(0));
value.incrementAndGet();
```

### 2. 락의 세분화
```
전역 락: 하나의 큰 임계 영역
[==================]

게시글별 락: 여러 개의 작은 임계 영역
[====] [====] [====]

효과: 병렬성 증가, 대기 시간 감소
```

### 3. 락프리 알고리즘
```
Compare-And-Swap (CAS):
1. 현재 값을 읽는다
2. 새 값을 계산한다
3. 현재 값이 변경되지 않았으면 새 값으로 교체
4. 변경되었으면 1번부터 재시도

장점: 락 없이 원자성 보장, 높은 병렬성
단점: 경쟁이 심하면 재시도 증가
```

## 🚀 실전 적용 가이드

### 상황별 권장 전략

#### 1. 조회수, 좋아요 등 카운터
```java
// AtomicViewService 사용
private final ConcurrentHashMap<Long, AtomicLong> counters;

public long increment(Long id) {
    return counters
        .computeIfAbsent(id, k -> new AtomicLong(0))
        .incrementAndGet();
}
```

#### 2. 금융 거래, 재고 관리
```java
// WriteThroughViewService 또는 트랜잭션 사용
@Transactional
public void updateInventory(Long productId, int quantity) {
    // DB에서 최신 값 조회
    Product product = repository.findById(productId);
    
    // 비즈니스 로직 수행
    product.decreaseStock(quantity);
    
    // DB 저장 (트랜잭션 커밋)
    repository.save(product);
    
    // 캐시 갱신
    cacheService.update(productId, product);
}
```

#### 3. 여러 리소스 동시 처리
```java
// PerPostLockViewService 사용
private final Map<Long, Lock> locks = new ConcurrentHashMap<>();

public void process(Long resourceId) {
    Lock lock = locks.computeIfAbsent(resourceId, k -> new ReentrantLock());
    lock.lock();
    try {
        // 리소스별 독립적인 처리
    } finally {
        lock.unlock();
    }
}
```

## 📚 추가 학습 자료

### Java 동시성 관련 클래스
- `AtomicLong`, `AtomicInteger`: 원자적 카운터
- `ConcurrentHashMap`: 스레드 안전한 맵
- `ReentrantLock`: 재진입 가능한 락
- `StampedLock`: 읽기/쓰기 락 (낙관적 읽기 지원)
- `CountDownLatch`: 스레드 동기화
- `CyclicBarrier`: 반복 가능한 배리어
- `Semaphore`: 세마포어 (리소스 개수 제한)

### 동시성 패턴
- **락 기반**: Synchronized, ReentrantLock
- **락프리**: Atomic 클래스, CAS
- **트랜잭션**: @Transactional, 낙관적/비관적 락
- **캐시 패턴**: Write-Through, Write-Back, Write-Around
- **이벤트 기반**: 비동기 메시징, 이벤트 소싱

---

**구현 완료일**: 2025년 11월 28일
