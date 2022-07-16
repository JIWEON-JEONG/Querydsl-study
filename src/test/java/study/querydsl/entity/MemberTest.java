package study.querydsl.entity;

import com.querydsl.core.QueryResults;
import com.querydsl.core.Tuple;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceUnit;
import javax.transaction.Transactional;

import java.util.List;

import static study.querydsl.entity.QMember.member;
import static study.querydsl.entity.QTeam.team;


/**
 * 결국 Querydsl 도 JPQL 에 의존적이다. (JPQL 의 빌더 역할) 내부적으로 JPQL 동작. 이점 명심하자.
 */
@SpringBootTest
@Transactional
public class MemberTest {

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
        //초기화
//        em.flush(); em.clear();
        //확인
//        List<Member> members = em.createQuery("select m from Member m",
//                        Member.class)
//                .getResultList();
//        for (Member member : members) {
//            System.out.println("member=" + member);
//            System.out.println("-> member.team=" + member.getTeam());
//        }
    }

    @Test
    public void startJPQL() {
        Member member = em.createQuery("select m from Member m where m.username = :username", Member.class)
                .setParameter("username", "member1")
                .getSingleResult();

        Assertions.assertThat(member.getUsername()).isEqualTo("member1");
    }

    @Test
    public void startQuerydsl() {

        Member findMember = queryFactory
                .select(member)
                .from(member)
                .where(member.username.eq("member1"))
                .fetchOne();


        Assertions.assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    @Test
    public void searchAndParam() {

        Member findMember = queryFactory
                .selectFrom(member)
                .where(
                        member.username.eq("member1"),
                        member.age.in(10, 30)
                )
                .fetchOne();

        Assertions.assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    @Test
    public void resultFetch(){
        List<Member> fetch = queryFactory
                .selectFrom(member)
                .fetch();

        //결과 2이상이면 exception 뱉는다
        Member fetchOne = queryFactory
                .selectFrom(member)
                .fetchOne();

        Member fetchFirst = queryFactory.selectFrom(member)
                .fetchFirst();

        //fetch 권장
        QueryResults<Member> results = queryFactory
                .selectFrom(member)
                .fetchResults();

        results.getTotal();
        List<Member> content = results.getResults();

        long total = queryFactory
                .selectFrom(member)
                .fetchCount();

    }

    @Test
    public void sort() {
        em.persist(new Member(null, 100));
        em.persist(new Member("member5", 100));
        em.persist(new Member("member6", 100));

        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.eq(100))
                .orderBy(member.age.desc(), member.username.asc().nullsLast())
                .fetch();

        Member member5 = result.get(0);
        Member member6 = result.get(1);
        Member memberNull = result.get(2);
        Assertions.assertThat(member5.getUsername().equals("member5"));
        Assertions.assertThat(member6.getUsername().equals("member6"));
        Assertions.assertThat(memberNull.getUsername()).isNull();
    }

    @Test
    public void paging1(){
        List<Member> result = queryFactory
                .selectFrom(member)
                .orderBy(member.username.desc())
                //몇개 제외 시키고 가져올것인지
                .offset(1)
                .limit(2)
                .fetch();

        Assertions.assertThat(result.size()).isEqualTo(2);
    }

    @Test
    public void paging2(){
        QueryResults<Member> result = queryFactory
                .selectFrom(member)
                .orderBy(member.username.desc())
                //몇개 제외 시키고 가져올것인지
                .offset(1)
                .limit(2)
                .fetchResults();

        Assertions.assertThat(result.getTotal()).isEqualTo(4);
        Assertions.assertThat(result.getOffset()).isEqualTo(1);
        Assertions.assertThat(result.getLimit()).isEqualTo(2);
        Assertions.assertThat(result.getResults().size()).isEqualTo(2);
    }

    @Test
    public void aggregation(){

        //tuple 로도 뽑지만 대부분 DTO 이용해서 뽑는다.
        List<Tuple> result = queryFactory
                .select(
                        member.count(),
                        member.age.sum(),
                        member.age.avg(),
                        member.age.max(),
                        member.age.min()
                )
                .from(member)
                .fetch();

        Tuple tuple = result.get(0);
        Assertions.assertThat(tuple.get(member.count())).isEqualTo(4);
        Assertions.assertThat(tuple.get(member.age.sum())).isEqualTo(100);
        Assertions.assertThat(tuple.get(member.age.max())).isEqualTo(40);
        Assertions.assertThat(tuple.get(member.age.min())).isEqualTo(10);
    }

    /**
     *
     * 팀의 이름과 각 팀의 평균 연령을 구하라.
     */
    @Test
    public void group() throws  Exception{
        List<Tuple> result = queryFactory
                .select(team.name, member.age.avg())
                .from(member)
                .join(member.team, team)
                .groupBy(team.name)
                .fetch();

        Tuple teamA = result.get(0);
        Tuple teamB = result.get(1);

        Assertions.assertThat(teamA.get(team.name)).isEqualTo("teamA");
        Assertions.assertThat(teamB.get(team.name)).isEqualTo("teamB");
    }

    /**
     * 팀 A에 소속된 모든 회원
     */
    @Test
    public void join() {
        List<Member> result = queryFactory
                .selectFrom(member)
                .join(member.team, team)
                .where(team.name.eq("teamA"))
                .fetch();

        Assertions.assertThat(result)
                .extracting("username")
                .containsExactly("member1", "member2");
    }

    /**
     * 세타조인
     * 회원의 이름이 팀 이름과 같은 회원 조회
     * ** 연관관계가 설정되지 않은 객체 끼리 join **
     */
    @Test
    public void theta_join() {
        em.persist(new Member("teamA"));
        em.persist(new Member("teamB"));
        em.persist(new Member("teamC"));

        List<Member> result = queryFactory
                .select(member)
                .from(member,team)
                .where(member.username.eq(team.name))
                .fetch();

        Assertions.assertThat(result)
                .extracting("username")
                .containsExactly("teamA", "teamB");
    }

    /**
     * 회원과 팀을 조인 하면서, 팀 이름이 teamA인 팀만 조인, 회원은 모두 조회
     * on절 VS where절
     * -> innerjoin 사용할때에는 둘이 똑같다. :: 데이터베이스가 이런 부분을 내부에서 최적화해주기 때문에 두가지 방법의 결과 성능은 거의 같다고 보시면 됩니다. - "김영한님"
     * -> 하지만 outerjoin 을 할 경우에는 on 절을 통해 조건을 구현한다.
     */
    @Test
    public void join_on_filtering() {
        List<Tuple> result = queryFactory
                .select(member, team)
                .from(member)
                .leftJoin(member.team, team).on(team.name.eq("teamA"))
                .fetch();


//        Assertions.assertThat(result)
//                .extracting("username")
//                .containsExactly("teamA", "teamB");
    }

    /**
     * ** 연관관계가 설정되지 않은 객체 끼리 outerjoin & on절 활용 **
     * 회원의 이름이 팀 이름과 같은 대상 외부 조인
     */
    @Test
    public void join_on_no_relation() {
        em.persist(new Member("teamA"));
        em.persist(new Member("teamB"));
        em.persist(new Member("teamC"));

        List<Tuple> result = queryFactory
                .select(member,team)
                .from(member)
                .leftJoin(team).on(member.username.eq(team.name))
                .fetch();

//        Assertions.assertThat(result)
//                .extracting("username")
//                .containsExactly("teamA", "teamB");
    }

    /**
     * Fetch join : SQL 조인을 활용해서 연관된 엔티티를 SQL 한번에 조회하는 기능.
     * 주로 성능 최적화에 사용하는 방법
     */
    @PersistenceUnit
    EntityManagerFactory emf;

    @Test
    public void fetch_join_no() {
        em.flush();
        em.clear();

        Member findMember = queryFactory
                .selectFrom(member)
                .where(member.username.eq("member1"))
                .fetchOne();

        boolean loaded = emf.getPersistenceUnitUtil().isLoaded(findMember.getTeam());
        Assertions.assertThat(loaded).as("페치 조인 미적용").isFalse();

    }


    @Test
    public void fetch_join_use() {
        em.flush();
        em.clear();

        Member findMember = queryFactory
                .selectFrom(member)
                .join(member.team,team).fetchJoin()
                .where(member.username.eq("member1"))
                .fetchOne();

        boolean loaded = emf.getPersistenceUnitUtil().isLoaded(findMember.getTeam());
        Assertions.assertThat(loaded).as("페치 조인 적용").isTrue();
    }

    /**
     * member 중 age 가 max 인 member 조회
     */
    @Test
    public void sub_query() {
        QMember memberSub = new QMember("memberSub");

         List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.eq(
                        JPAExpressions
                                .select(memberSub.age.max())
                                .from(memberSub)
                ))
                .fetch();

        Assertions.assertThat(result).extracting("age")
                .containsExactly(40);
    }

    /**
     * member 중 age 가 avg 이상 인 member 조회
     */
    @Test
    public void sub_query_goe() {
        QMember memberSub = new QMember("memberSub");

        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.goe(
                        JPAExpressions
                                .select(memberSub.age.avg())
                                .from(memberSub)
                ))
                .fetch();

        Assertions.assertThat(result).extracting("age")
                .containsExactly(30,40);
    }

    /**
     * 예제상.. 억지스러운 예제
     * in : sql 내에서 특정값 여러개 추출
     * member 중 age 10살 초
     */
    @Test
    public void sub_query_in() {
        QMember memberSub = new QMember("memberSub");

        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.in(
                        JPAExpressions
                                .select(memberSub.age)
                                .from(memberSub)
                                .where(memberSub.age.gt(10))
                ))
                .fetch();

        Assertions.assertThat(result).extracting("age")
                .containsExactly(20, 30, 40);
    }

    /**
     * stringValue() Enum type 처리 할 때에도 효과적이다.
     */
    @Test
    public void concat(){
        List<String> result = queryFactory
                .select(member.username.concat("_").concat(member.age.stringValue()))
                .from(member)
                .where(member.username.eq("member1"))
                .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }

    }

}
