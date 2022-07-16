package study.querydsl;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.ExpressionUtils;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import study.querydsl.dto.MemberDto;
import study.querydsl.dto.QMemberDto;
import study.querydsl.dto.UserDto;
import study.querydsl.entity.Member;
import study.querydsl.entity.QMember;
import study.querydsl.entity.Team;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static study.querydsl.entity.QMember.member;

@SpringBootTest
@Transactional
public class QuerydslBasicTest {

    @Autowired
    EntityManager em;

    JPAQueryFactory queryFactory;

    @BeforeEach
    public void testEntity() {
        queryFactory = new JPAQueryFactory(em);
        Team teamA = new Team("teamA");
        Team teamB = new Team("teamB");
        em.persist(teamA);
        em.persist(teamB);
        Member member1 = new Member("member1", 10, teamA);
        Member member2 = new Member("member2", 20, teamA);
        Member member3 = new Member("member3", 30, teamB);
        Member member4 = new Member("member4", 40, teamB);
        em.persist(member1);
        em.persist(member2);
        em.persist(member3);
        em.persist(member4);
    }

    @Test
    void findDtoByJPQL() {
        List<MemberDto> result = em.createQuery(
                        "select new study.querydsl.dto.MemberDto(m.username, m.age) " +
                                "from Member m", MemberDto.class)
                .getResultList();

        for (MemberDto memberDto : result) {
            System.out.println(memberDto);
        }
    }

    /**
     * dto setter 접근 안된다면 , 기본 생성자로 구현되어 Default 나 Null 값으로 들어간다.
     */
    @Test
    void findDtoBySetter() {
        List<MemberDto> result = queryFactory
                .select(Projections.bean(MemberDto.class,
                        member.username,
                        member.age))
                .from(member)
                .fetch();

        for (MemberDto memberDto : result) {
            System.out.println(memberDto);
        }
    }

    /**
     * Getter , setter 필요 없다.
     * field 에 바로 값을 넣어 버린다.
     */
    @Test
    void findDtoByField() {
        List<MemberDto> result = queryFactory
                .select(Projections.fields(MemberDto.class,
                        member.username,
                        member.age))
                .from(member)
                .fetch();

        for (MemberDto memberDto : result) {
            System.out.println(memberDto);
        }
    }

    @Test
    void findUserDtoByField() {
        QMember qMember = new QMember("memberSub");
        List<UserDto> result = queryFactory
                .select(Projections.fields(UserDto.class,
                        member.username.as("name"),
                        ExpressionUtils.as(JPAExpressions
                                .select(qMember.age.max())
                                .from(qMember), "age")
                ))
                .from(member)
                .fetch();

        for (UserDto userDto : result) {
            System.out.println(userDto);
        }
    }

    /**
     * parameter 오류 있어도 compile 이 되는게 단점
     * 런타임에서 실행이 되야 오류 뱉어냄.
     */
    @Test
    void findDtoByConstructor() {
        List<MemberDto> result = queryFactory
                .select(Projections.constructor(MemberDto.class,
                        member.username,
                        member.age))
                .from(member)
                .fetch();

        for (MemberDto memberDto : result) {
            System.out.println(memberDto);
        }
    }

    @Test
    void findUserDtoByConstructor() {
        List<UserDto> result = queryFactory
                .select(Projections.constructor(UserDto.class,
                        member.username,
                        member.age))
                .from(member)
                .fetch();

        for (UserDto userDto : result) {
            System.out.println(userDto);
        }
    }

    /**
     * 다 좋음.
     * parameter 오류가 발생해도 컴파일 시점에 바로 잡아주 안전함
     * 하지만 dto 객체가 querydsl 에 의존해야한다는 단점이 있다.
     *
     */
    @Test
    void findUserDtoByQueryProjection() {
        List<MemberDto> result = queryFactory
                .select(new QMemberDto(member.username, member.age))
                .from(member)
                .fetch();

        for (MemberDto memberDto : result) {
            System.out.println(memberDto);
        }
    }

    @Test
    void dynamicQuery_BooleanBuilder() {
        String usernameParam = "member1";
        Integer ageParam = null;

        List<Member> result = searchMember1(usernameParam, ageParam);
        assertThat(result.size()).isEqualTo(1);
    }

    private List<Member> searchMember1(String usernameParam, Integer ageParam) {
//        fix된 조건 처리
//        BooleanBuilder builder = new BooleanBuilder(member.username.eq("member1"));
        BooleanBuilder builder = new BooleanBuilder();
        if (usernameParam != null) {
            builder.and(member.username.eq(usernameParam));
        }
        if (ageParam != null) {
            builder.and(member.age.eq(ageParam));
        }
        return queryFactory
                .selectFrom(member)
                .where(builder)
                .fetch();
    }

    @Test
    void dynamicQuery_WhereParam() {
        String usernameParam = "member1";
        Integer ageParam = null;

        List<Member> result = searchMember2(usernameParam, ageParam);
        assertThat(result.size()).isEqualTo(1);
    }

    private List<Member> searchMember2(String usernameParam, Integer ageParam) {
        return queryFactory
                .selectFrom(member)
                .where(usernameAndAgeEq(usernameParam,ageParam))
                .fetch();
    }

    private BooleanExpression usernameEq(String usernameParam) {
        return usernameParam != null ? member.username.eq(usernameParam) : null;
    }

    private BooleanExpression ageEq(Integer ageParam) {
        return ageParam != null ? member.age.eq(ageParam) : null;
    }

    private BooleanExpression usernameAndAgeEq(String usernameParam, Integer ageParam) {
        return usernameEq(usernameParam).and(ageEq(ageParam));
    }

    /**
     * 엔티티 수정 하면 jpa 에서 transaction commit 할 때 flush 일어나면서 더티체킹 하면서 update 쿼리를 만들어 DB 반영해준다.
     * 개별 엔티티 건마다 일어나는 특징이 있다.
     *
     * 이러한 부분을 -> 벌크 연산을 통해 해결
     * 쿼리 하나로 대량의 데이터를 수정 , 삭제 할때 사용.
     *
     * 벌크 연산 시 , 영속성 컨텍스트를 무시하고 바로 DB query 를 보내기 때문에 , 영속성 컨텍스트를 한번 초기화 해주는 것이 좋다.
     * 데이터 무결성이 깨질수 있음.
     */
    @Test
    public void bulkUpdate() {

        //영속성컨텍스트와 DB 상태 불일치. 영속성 컨텍스트 우선권 가짐.
        //member1 = 10
        //member2 = 20
        //member3 = 30
        //member4 = 40
        long count = queryFactory
                .update(member)
                .set(member.username, "미성년자")
                .where(member.age.lt(25))
                .execute();

        em.flush();
        em.clear();

        List<Member> result = queryFactory
                .selectFrom(member)
                .fetch();

        for (Member member1 : result) {
            System.out.println(member1.getUsername());
        }

    }

    @Test
    public void bulkAdd() {
        queryFactory
                .update(member)
//                .set(member.age, member.age.multiply(0.5))
                .set(member.age, member.age.add(1))
                .execute();

        em.flush();
        em.clear();
    }

    @Test
    public void bulkDelete() {
        queryFactory
                .delete(member)
                .where(member.age.lt(20))
                .execute();

        em.flush();
        em.clear();
    }

}
