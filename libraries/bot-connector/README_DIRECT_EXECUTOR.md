# Direct Executor 설계 문서

## 개요

기존 BotBuilder-Java SDK는 내부 스레드풀(ForkJoinPool)을 사용하여 비동기 작업을 처리했습니다. 
이 설계는 `guava MoreExecutors.directExecutor()`를 사용하여 호출 스레드가 직접 작업을 처리하도록 변경할 수 있는 옵션을 제공합니다.

## 아키텍처 변경

### 기존 설계
```
HTTP Request Thread → ExecutorFactory.getExecutor() → ForkJoinPool (별도 스레드)
                                                           ↓
                                                      비동기 작업 실행
```

### 새로운 설계 (Direct Executor 사용 시)
```
HTTP Request Thread → ExecutorFactory.getAsyncExecutor() → MoreExecutors.directExecutor()
                                                                ↓
                                                         호출 스레드에서 직접 실행
```

## 주요 변경 사항

### 1. ExecutorFactory 개선
- **새로운 메서드**: `getAsyncExecutor()` - 설정된 Executor를 반환
- **설정 메서드**: 
  - `setExecutor(Executor)` - 커스텀 Executor 설정
  - `useDirectExecutor()` - Direct Executor 모드로 전환
  - `useDefaultExecutor()` - 기본 ForkJoinPool로 복원
  - `isUsingDirectExecutor()` - 현재 모드 확인

### 2. 영향 받는 컴포넌트
- `BotFrameworkAdapter.sendActivities()` - 메시지 전송 시
- `ShowTypingMiddleware` - 타이핑 표시 백그라운드 작업
- `JwtTokenExtractor` - JWT 토큰 검증 시
- `TestFlow` - 테스트 프레임워크

## 사용 방법

### 1. Direct Executor 활성화 (애플리케이션 시작 시)

```java
import com.microsoft.bot.connector.ExecutorFactory;
import com.google.common.util.concurrent.MoreExecutors;

public class Application {
    public static void main(String[] args) {
        // 방법 1: 직접 설정
        ExecutorFactory.setExecutor(MoreExecutors.directExecutor());
        
        // 방법 2: 편의 메서드 사용
        ExecutorFactory.useDirectExecutor();
        
        // Bot 애플리케이션 시작
        SpringApplication.run(Application.class, args);
    }
}
```

### 2. Spring Boot 설정

```java
@Configuration
public class BotConfiguration extends BotDependencyConfiguration {
    
    @PostConstruct
    public void configureExecutor() {
        // Direct Executor 모드로 전환
        ExecutorFactory.useDirectExecutor();
    }
    
    @Bean
    public Bot getBot() {
        return new MyBot();
    }
}
```

### 3. 동적 전환 (런타임)

```java
// 성능 테스트나 디버깅 시 동적으로 전환 가능
if (debugMode) {
    ExecutorFactory.useDirectExecutor();
} else {
    ExecutorFactory.useDefaultExecutor();
}

// 현재 모드 확인
boolean isDirect = ExecutorFactory.isUsingDirectExecutor();
```

## 장단점 분석

### Direct Executor의 장점

1. **스레드 전환 오버헤드 제거**
   - 작업이 호출 스레드에서 직접 실행되어 컨텍스트 스위칭 비용 절약
   - 작은 작업에서 성능 향상

2. **디버깅 용이성**
   - 스택 트레이스가 단순해져 문제 추적이 쉬움
   - 스레드 간 전환이 없어 breakpoint 사용이 편리

3. **스레드 안전성**
   - 단일 스레드에서 실행되므로 동시성 문제 감소
   - ThreadLocal 값이 유지됨

4. **리소스 절약**
   - 별도 스레드 풀이 필요 없어 메모리 절약
   - 스레드 수 제한이 없음

### Direct Executor의 단점

1. **블로킹 위험**
   - 긴 작업이 호출 스레드를 블로킹할 수 있음
   - HTTP 요청 처리 스레드가 멈출 수 있음

2. **병렬 처리 불가**
   - CPU 바운드 작업의 병렬 처리 불가능
   - 멀티코어 활용도 감소

3. **ShowTypingMiddleware 제약**
   - 백그라운드 타이핑 표시 기능이 제대로 동작하지 않을 수 있음
   - 주기적인 작업에는 부적합

## 권장 사용 시나리오

### Direct Executor 권장
- ✅ 간단한 봇 (빠른 응답, I/O 작업 최소)
- ✅ 마이크로서비스 환경 (외부에서 스레드 관리)
- ✅ 개발/디버깅 환경
- ✅ 단위 테스트
- ✅ Serverless 환경 (AWS Lambda, Azure Functions)

### 기본 ForkJoinPool 권장
- ✅ 복잡한 봇 (여러 외부 API 호출)
- ✅ CPU 집약적 작업이 있는 경우
- ✅ ShowTypingMiddleware 사용
- ✅ 장시간 실행되는 작업
- ✅ 높은 처리량이 필요한 프로덕션 환경

## 성능 비교

### 벤치마크 예제

```java
@Test
public void performanceComparison() {
    int iterations = 10000;
    
    // ForkJoinPool 모드
    ExecutorFactory.useDefaultExecutor();
    long start1 = System.currentTimeMillis();
    for (int i = 0; i < iterations; i++) {
        processSimpleMessage();
    }
    long forkJoinTime = System.currentTimeMillis() - start1;
    
    // Direct Executor 모드
    ExecutorFactory.useDirectExecutor();
    long start2 = System.currentTimeMillis();
    for (int i = 0; i < iterations; i++) {
        processSimpleMessage();
    }
    long directTime = System.currentTimeMillis() - start2;
    
    System.out.println("ForkJoinPool: " + forkJoinTime + "ms");
    System.out.println("DirectExecutor: " + directTime + "ms");
}
```

예상 결과:
- 간단한 작업: Direct Executor가 20-30% 빠름
- 복잡한 작업: ForkJoinPool이 더 나음 (병렬 처리)

## 마이그레이션 가이드

### 기존 코드 호환성
모든 기존 코드는 수정 없이 동작합니다. `ExecutorFactory.getExecutor()`는 deprecated되었지만 하위 호환성을 유지합니다.

### 단계별 마이그레이션

1. **테스트 환경에서 활성화**
   ```java
   @BeforeClass
   public static void setup() {
       ExecutorFactory.useDirectExecutor();
   }
   ```

2. **모니터링**
   - 응답 시간 측정
   - 스레드 블로킹 확인
   - CPU 사용률 모니터링

3. **프로덕션 적용**
   - 카나리 배포로 일부 인스턴스에만 적용
   - 성능 지표 비교
   - 문제 발생 시 롤백

## 주의사항

1. **ShowTypingMiddleware와 함께 사용 시**
   - Direct Executor는 백그라운드 작업에 적합하지 않음
   - ShowTypingMiddleware 사용 시 기본 Executor 사용 권장

2. **Long-running 작업**
   ```java
   // ❌ 좋지 않은 예: Direct Executor로 긴 작업
   ExecutorFactory.useDirectExecutor();
   CompletableFuture.supplyAsync(() -> {
       Thread.sleep(10000); // 호출 스레드 블로킹!
       return result;
   }, ExecutorFactory.getAsyncExecutor());
   
   // ✅ 좋은 예: 커스텀 Executor 사용
   ExecutorService customPool = Executors.newCachedThreadPool();
   CompletableFuture.supplyAsync(() -> {
       Thread.sleep(10000);
       return result;
   }, customPool);
   ```

3. **스레드 로컬 변수**
   - Direct Executor 사용 시 ThreadLocal 값이 유지됨
   - 요청 간 격리가 필요한 경우 주의

## 향후 개선 사항

1. **하이브리드 모드**
   - 작업 유형에 따라 자동으로 Executor 선택
   - 간단한 작업: Direct Executor
   - 복잡한 작업: Thread Pool

2. **성능 프로파일링 도구**
   - Executor 선택에 도움이 되는 메트릭 수집
   - 자동 튜닝

3. **설정 기반 전환**
   ```properties
   bot.executor.mode=direct  # 또는 pool
   ```

## 참고 자료

- [Guava MoreExecutors Documentation](https://guava.dev/releases/snapshot-jre/api/docs/com/google/common/util/concurrent/MoreExecutors.html)
- [CompletableFuture Best Practices](https://www.baeldung.com/java-completablefuture)
- [Thread Pool vs Direct Executor](https://stackoverflow.com/questions/18730290/what-is-the-use-of-directexecutor-in-guava)

## 문의

이슈나 질문이 있으면 GitHub Issues에 등록해 주세요.

