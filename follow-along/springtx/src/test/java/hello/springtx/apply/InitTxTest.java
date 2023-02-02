package hello.springtx.apply;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.annotation.PostConstruct;

@SpringBootTest
public class InitTxTest {

    @Autowired
    Hello hello;

    @Test
    void go() {
        // @PostConstruct tx active=false
        // 초기화 코드가 먼저 호출되고 트랜잭션 AOP가 적용되기 때문에 트랜잭션을 획득할 수 없다.


        // Hello init @PostConstruct tx active=false <-- PostConstruct 메서드 실행
        // Started InitTxTest in 2.401 seconds (JVM running for 3.515) <-- application 시작
        // Getting transaction for [hello.springtx.apply.InitTxTest$Hello.initV2] <-- @EventListener(ApplicationReadyEvent.class) 메서드 실행
        // Hello init ApplicationReadyEvent tx active=true
        // Completing transaction for [hello.springtx.apply.InitTxTest$Hello.initV2]
    }

    @TestConfiguration
    static class Config {
        @Bean
        Hello hello() {
            return new Hello();
        }
    }

    @Slf4j
    static class Hello {
        // PostConstruct 후에 트랜잭션 AOP가 생성(?) 된다. 따라서 트랜잭션이 안 걸린다.
        @PostConstruct
        @Transactional
        public void initV1() {
            boolean isActive = TransactionSynchronizationManager.isActualTransactionActive();
            log.info("Hello init @PostConstruct tx active={}", isActive);
        }

        // 어플리케이션 로드가 모두 된 후에 메서드가 실행된다. 트랜잭션이 잘 동작한다.
        @EventListener(ApplicationReadyEvent.class)
        @Transactional
        public void initV2() {
            boolean isActive = TransactionSynchronizationManager.isActualTransactionActive();
            log.info("Hello init ApplicationReadyEvent tx active={}", isActive);
        }
    }
}
