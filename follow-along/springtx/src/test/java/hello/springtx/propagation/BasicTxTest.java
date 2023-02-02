package hello.springtx.propagation;

import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.interceptor.DefaultTransactionAttribute;

import javax.sql.DataSource;

@Slf4j
@SpringBootTest
public class BasicTxTest {

    @Autowired
    PlatformTransactionManager txManager;

    @TestConfiguration
    static class Config {
        // 기본적으로 스프링이 PlatformTransactionManager 빈을 자동 등록하지만 아래와 같이 명시할 경우 명시한 빈이 등록된다.
        @Bean
        public PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }
    }

    @Test
    void commit() {
        log.info("트랜잭션 시작");
        TransactionStatus status = txManager.getTransaction(new DefaultTransactionAttribute()); // 트랜잭션 얻기의 뜻: 커넥션 얻은 후, 매뉴얼 commit 모드로 변경

        log.info("트랜잭션 커밋 시작");
        txManager.commit(status);   // 트랜잭션 커밋: commit 을 실행하고 커넥션 release (커넥션을 커넥션 풀에 되돌려 준다.)
        log.info("트랜잭션 커밋 완료");
    }

    @Test
    void rollback() {
        log.info("트랜잭션 시작");
        TransactionStatus status = txManager.getTransaction(new DefaultTransactionAttribute()); // 트랜잭션 얻기의 뜻: 커넥션 얻은 후, 매뉴얼 commit 모드로 변경

        log.info("트랜잭션 커밋 시작");
        txManager.rollback(status);   // 트랜잭션 커밋: rollback 을 실행하고 커넥션 release (커넥션을 커넥션 풀에 되돌려 준다.)
        log.info("트랜잭션 커밋 완료");
    }

    @Test
    void double_commit() {
        // HikariProxyConnection@히카리프록시커넥션객체주소1 wrapping conn0
        // 에 대해서 트랜잭션 실행
        log.info("트랜잭션1 시작");
        TransactionStatus tx1 = txManager.getTransaction(new DefaultTransactionAttribute());
        log.info("트랜잭션1 커밋");
        txManager.commit(tx1);

        // HikariProxyConnection@히카리프록시커넥션객체주소2 wrapping conn0
        // 에 대해서 트랜잭션 실행
        log.info("트랜잭션2 시작");
        TransactionStatus tx2 = txManager.getTransaction(new DefaultTransactionAttribute());
        log.info("트랜잭션2 커밋");
        txManager.commit(tx2);

        // conn0 는 물리적인 커넥션이고, 히카리 커넥션 풀은 커넥션을 "히카리 프록시 커넥션" 객체로 커넥션을 감싸서 애플리케이션에 넘겨주게 된다.
        // 커밋 후 커넥션 release 하면 히카리 프록시 커넥션은 사라진다. 물리적인 커넥션은 사라지지 않고 커넥션 풀에 남아있다.
        // 이후 다른 곳에서 커넥션을 요청하게 되면 또 다시 커넥션 풀의 물리 커넥션 하나가 새로운 히카리 프록시 커넥션 객체로 감싸져서 전달된다.
        // 이 때 물리 커넥션은 이전에 사용했던 동일한 물리 커넥션이 될 수 있다.
        // 위 2개의 연속되는 트랜잭션에 사용된 물리적인 커넥션은 동일하고, 히카리 프록시 커넥션은 다르다.
        // 즉 커넥션 자체는 재사용되지만, 커넥션을 넘겨 줄 때는 새로운 프록시 객체로 감싸지게 된다.

        // 여기서 중요한 점은, 두 개의 트랜잭션은 같은 물리적인 커넥션을 사용했다고 하더라도 서로 상관이 없다라는 것이다.
        // 왜냐하면 첫번째 트랜잭션이 끝나고 나서 두번째 트랜잭션이 실행되었기 때문이다.
    }

    @Test
    void double_commit_rollback() {
        log.info("트랜잭션1 시작");
        TransactionStatus tx1 = txManager.getTransaction(new DefaultTransactionAttribute());
        log.info("트랜잭션1 커밋");
        txManager.commit(tx1);

        log.info("트랜잭션2 시작");
        TransactionStatus tx2 = txManager.getTransaction(new DefaultTransactionAttribute());
        log.info("트랜잭션2 롤백");
        txManager.rollback(tx2);

        // 첫번째 트랜잭션은 커밋되고 두번째 트랜잭션은 롤백 된다.
        // 두 개의 트랜잭션은 연관이 없다.
    }

    @Test
    void inner_commit() {
        log.info("외부 트랜잭션 시작");
        TransactionStatus outer = txManager.getTransaction(new DefaultTransactionAttribute());
        log.info("outer.isNewTransaction()={}", outer.isNewTransaction());

        // 기존 트랜잭션에 참여 (내부 트랜잭션이 외부 트랜잭션에 참여)
        // 내부 트랜잭션이 외부 트랜잭션을 그대로 이어 받는다는 것이다. (같은 커넥션 사용)
        // 외부 트랜잭션 범위가 내부 트랜잭션까지 넓어 졌다는 뜻이다.
        // 즉, 외부 트랜잭션과 내부 트랜잭션이 하나의 물리 트랜잭션으로 묶이는 것이다.
        // 따라서 내부 트랜잭션은 신규 트랜잭션이 아니다.
        // 그런데 왜 커밋을 두 번이나 할 수 있는가? 하나의 커넥션을 커밋하거나 롤백하면, 커넥션을 반납하는 것 아닌가?
        log.info("내부 트랜잭션 시작");
        TransactionStatus inner = txManager.getTransaction(new DefaultTransactionAttribute());
        log.info("inner.isNewTransaction()={}", inner.isNewTransaction());
        log.info("내부 트랜잭션 커밋");
        txManager.commit(inner);    // 여기서 실제로 커밋?? no! 신규 트랜잭션이 아닌 경우는 커밋을 호출하지 않는다.

        log.info("외부 트랜잭션 커밋");
        txManager.commit(outer);    // 여기서 실제로 커밋?? yes! 신규 트랜잭션일 경우 커밋을 호출한다.

        // 트랜잭션 매니저에 커밋하는 것이 논리적인 커밋이라면, 실제 커넥션에 커밋하는 것을 물리 커밋이라고 할 수 있다.
        // 실제 데이터베이스에 커밋이 반영되고 물리 트랜잭션이 끝난다.
    }

    @Test
    void outer_rollback() {
        log.info("외부 트랜잭션 시작");
        TransactionStatus outer = txManager.getTransaction(new DefaultTransactionAttribute());

        log.info("내부 트랜잭션 시작");
        TransactionStatus inner = txManager.getTransaction(new DefaultTransactionAttribute());
        log.info("내부 트랜잭션 커밋");
        txManager.commit(inner);    // 내부 커밋 - 신규 트랜잭션이 아니기 때문에 물리 커밋 X

        log.info("외부 트랜잭션 롤백");
        txManager.rollback(outer);  // 외부 롤백 - 롤백 실행
    }

    @Test
    void inner_rollback() {
        log.info("외부 트랜잭션 시작");
        TransactionStatus outer = txManager.getTransaction(new DefaultTransactionAttribute());

        log.info("내부 트랜잭션 시작");
        TransactionStatus inner = txManager.getTransaction(new DefaultTransactionAttribute());
        log.info("내부 트랜잭션 롤백");
        txManager.rollback(inner);  // 내부 롤백 - Participating transaction failed - marking existing transaction as rollback-only

        log.info("외부 트랜잭션 커밋");
        // 외부 커밋 - 트랜잭션이 롤백 전용으로 표시되어 있으므로 커밋을 희망하더라도, 트랜잭션을 롤백한다.
        Assertions.assertThatThrownBy(() -> txManager.commit(outer))
                .isInstanceOf(UnexpectedRollbackException.class);
    }

    @Test
    void inner_rollback_requires_new() {
        log.info("외부 트랜잭션 시작");
        TransactionStatus outer = txManager.getTransaction(new DefaultTransactionAttribute());
        log.info("outer.isNewTransaction()={}", outer.isNewTransaction());  // true

        log.info("내부 트랜잭션 시작");
        DefaultTransactionAttribute definition = new DefaultTransactionAttribute();
        // 새로운 물리 트랜잭션 생성 옵션 지정
        definition.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        // Suspending current transaction, creating new transaction
        TransactionStatus inner = txManager.getTransaction(definition);
        log.info("outer.isNewTransaction()={}", inner.isNewTransaction());  // true

        log.info("내부 트랜잭션 롤백");
        txManager.rollback(inner);
        // Resuming suspended transaction after completion of inner transaction

        log.info("외부 트랜잭션 커밋");
        txManager.commit(outer);
    }
}
