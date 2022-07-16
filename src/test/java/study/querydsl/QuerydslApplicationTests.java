package study.querydsl;

import com.querydsl.jpa.impl.JPAQueryFactory;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Commit;
import study.querydsl.entity.Hello;
import study.querydsl.entity.QHello;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;

@Transactional
@SpringBootTest
@Commit
class QuerydslApplicationTests {

	@Autowired
	EntityManager em;

	@Test
	void contextLoads() {

		Hello hello = new Hello();
		em.persist(hello);

		JPAQueryFactory query = new JPAQueryFactory(em);
//		QHello qHello = new QHello("h");
		QHello qHello = QHello.hello;

		Hello result = query
				.selectFrom(qHello)
				.fetchOne();

		Assertions.assertThat(result).isEqualTo(hello);

	}

}
