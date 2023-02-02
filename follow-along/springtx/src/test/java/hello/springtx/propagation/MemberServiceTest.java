package hello.springtx.propagation;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.UnexpectedRollbackException;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@SpringBootTest
class MemberServiceTest {
    @Autowired
    MemberService memberService;
    @Autowired
    MemberRepository memberRepository;
    @Autowired
    LogRepository logRepository;

    /**
     * memberService    @Transactional: OFF
     * memberRepository @Transactional: ON
     * logRepository    @Transactional: ON
     */
    @Test
    void outerTxOff_success() {
        // given
        String username = "outerTxOff_success";
        // when
        memberService.joinV1(username);
        // then: 모든 데이터가 정상 저장된다.
        assertTrue(memberRepository.find(username).isPresent());
        assertTrue(logRepository.find(username).isPresent());
    }

    /**
     * memberService    @Transactional: OFF
     * memberRepository @Transactional: ON
     * logRepository    @Transactional: ON Exception
     * 멤버는 저장되고 로그는 롤백 --> 데이터 정합성에 문제 발생
     */
    @Test
    void outerTxOff_fail() {
        // given
        String username = "로그예외_outerTxOff_fail";
        // when
        assertThatThrownBy(() -> memberService.joinV1(username))
                .isInstanceOf(RuntimeException.class);
        // then: 멤버는 저장되고 로그는 롤백된다.
        assertTrue(memberRepository.find(username).isPresent());
        assertTrue(logRepository.find(username).isEmpty());
    }

    /**
     * memberService    @Transactional: ON
     * memberRepository @Transactional: OFF
     * logRepository    @Transactional: OFF
     * MemberService::joinV1 에 걸린 트랜잭션 1개로 모든 로직을 감쌌다.
     * 각 레포지토리는 이 트랜잭션 걸린 커넥션을 사용 (트랜잭션 동기화 매니저에 저장된)
     */
    @Test
    void singleTx() {
        // given
        String username = "singleTx";
        // when
        memberService.joinV1(username);
        // then: 모든 데이터가 정상 저장된다.
        assertTrue(memberRepository.find(username).isPresent());
        assertTrue(logRepository.find(username).isPresent());
    }

    /**
     * memberService    @Transactional: ON
     * memberRepository @Transactional: ON
     * logRepository    @Transactional: ON
     * MemberService::joinV1 에 걸린 트랜잭션 1개로 모든 로직을 감쌌다.
     * 각 레포지토리 AOP는 해당 트랜잭션에 참여해서 논리 트랜잭션을 얻는다. (다른 말로, 최초의 트랜잭션이 내부 트랜잭션으로 전파)
     */
    @Test
    void outerTxOn_success() {
        // given
        String username = "outerTxOn_success";
        // when
        memberService.joinV1(username);
        // then: 모든 데이터가 정상 저장된다.
        assertTrue(memberRepository.find(username).isPresent());
        assertTrue(logRepository.find(username).isPresent());
    }

    /**
     * memberService    @Transactional: ON Exception bubble up
     * memberRepository @Transactional: ON
     * logRepository    @Transactional: ON Exception
     * exception 이 logRepository 에서 발생해서 memberService까지 올라온다.
     * memberService AOP, logRepository AOP 모두 예외를 받고 롤백 호출한다. 물론 예외를 받는 곳은 없어서 memberService 밖으로까지 예외가 전달된다.
     * logRepository AOP 의 롤백 요청은 rollbackOnly 설정으로 끝난다.
     * memberService AOP 의 롤백 요청은 실재 트랜잭션의 롤백으로 끝난다. 어차피 롤백이므로 rollbackOnly 설정은 참고하지 않는다.
     * 멤버, 로그 모두 롤백 --> 데이터 정합성에 문제 없음
     */
    @Test
    void outerTxOn_fail() {
        // given
        String username = "로그예외_outerTxOn_fail";
        // when
        assertThatThrownBy(() -> memberService.joinV1(username))
                .isInstanceOf(RuntimeException.class);
        // then: 모두 롤백된다.
        assertTrue(memberRepository.find(username).isEmpty());
        assertTrue(logRepository.find(username).isEmpty());
    }

    // 회원 가입 이력 로그를 남기는 과정에 가끔 문제가 발생해서 회원 가입 자체가 안되는 경우가 가끔 발생한다고 하자.
    // 그래서 사용자들이 회원 가입에 실패하게 되어 이탈하는 문제가 발생할 것이다.
    // 회원 가입 이력 로그의 경우 여러가지 방법을 통해 추후에 복구가 가능할 것으로 보인다.
    // 따라서 비즈니스 로직이 다음과 같은 내용으로 변경되었다.
    // "회원 가입을 시도한 로그를 남기는 데 실패하더라도 회원 가입은 유지되어야 한다."

    /**
     * memberService    @Transactional: ON
     * memberRepository @Transactional: ON
     * logRepository    @Transactional: ON Exception : try-catch 로 서비스에서 잡음
     * 비록 logRepository에서 발생하는 예외를 멤버 서비스에서 try-catch 로 잡았다 하더라도 전체 롤백은 막을 수 없다.
     * 왜냐하면 logRepository의 트랜잭션 AOP 는 이미 예외를 감지하여 롤백을 호출하기 때문이다. 이것은 물리 커넥션을 roll-back only 로 마크한다.
     * 이후 memberService.joinV2 가 정상 종료되면서 memberService의 트랜잭션 AOP가 커밋을 시도하는데,
     * 이 때 물리 커넥션이 roll-back only 이므로 롤백을 수행하고 UnexpectedRollbackException 을 던진다.
     * 즉, 짧게 정리하자면, 논리 트랜잭션이 하나라도 롤백을 원하므로 전체 트랜잭션은 롤백이 된 것이다. --> 위의 비즈니스 로직을 만족하지 못한다.
     */
    @Test
    void recoverException_fail() {
        // given
        String username = "로그예외_recoverException_fail";
        // when
        assertThatThrownBy(() -> memberService.joinV2(username))
                .isInstanceOf(UnexpectedRollbackException.class);
        // then: 모두 롤백된다.
        assertTrue(memberRepository.find(username).isEmpty());
        assertTrue(logRepository.find(username).isEmpty());
    }

    /**
     * memberService    @Transactional: ON
     * memberRepository @Transactional: ON
     * logRepository    @Transactional: ON(REQUIRES_NEW) Exception : try-catch 로 서비스에서 잡음
     * memberService 는 트랜잭션을 생성하고 memberRepository 는 여기에 참가하여 비즈니스 로직을 수행한다.
     * logRepository 는 자신만의 트랜잭션을 생성한다(새로운 커넥션). 그리고 memberService에서 생성한 트랜잭션을 suspend 한다.
     * logRepository 에서 예외가 발생하여 logRepository AOP는 트랜잭션 롤백을 수행한다. --> log 는 저장되지 못한다.
     * 이 후 해당 예외가 memberService 코드까지 침범하지만 try-catch 로 잡아 코드 실행이 정상 흐름으로 되돌아 간다.
     * 정상적으로 종료된 memberService 코드 이후로 memberService AOP 는 커밋을 호출하고 따라서 멤버는 문제없이 저장된다.
     * 위의 비즈니스 로직을 만족하였다.
     */
    @Test
    void recoverException_success() {
        // given
        String username = "로그예외_recoverException_success";
        // when
        memberService.joinV2(username);
        // then: member 저장, log 롤백
        assertTrue(memberRepository.find(username).isPresent());
        assertTrue(logRepository.find(username).isEmpty());
    }

}
