package hello.itemservice.repository.jpa;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import hello.itemservice.domain.Item;
import hello.itemservice.repository.ItemRepository;
import hello.itemservice.repository.ItemSearchCond;
import hello.itemservice.repository.ItemUpdateDto;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.persistence.EntityManager;
import java.util.List;
import java.util.Optional;

import static hello.itemservice.domain.QItem.*;


@Repository // Querydsl 은 JPQL 빌더 역할을 할 뿐이고 JPA 예외를 변환 시켜주지는 않는다. JPA 예외를 Spring의 DataAccessException 으로 변환시켜주는 일은 @Repository 에서 수행하게 된다.
@Transactional
public class JpaItemRepositoryV3 implements ItemRepository {

    private final EntityManager em;
    private final JPAQueryFactory query;

    public JpaItemRepositoryV3(EntityManager em) {
        this.em = em;
        this.query = new JPAQueryFactory(em);
    }

    @Override
    public Item save(Item item) {
        em.persist(item);
        return item;
    }

    @Override
    public void update(Long itemId, ItemUpdateDto updateParam) {
        Item item = em.find(Item.class, itemId);
        item.setItemName(updateParam.getItemName());
        item.setPrice(updateParam.getPrice());
        item.setQuantity(updateParam.getQuantity());
    }

    @Override
    public Optional<Item> findById(Long id) {
        Item item = em.find(Item.class, id);
        return Optional.ofNullable(item);
    }

    @Override
    public List<Item> findAll(ItemSearchCond cond) {

        String itemName = cond.getItemName();
        Integer maxPrice = cond.getMaxPrice();

        return query.select(item)
                .from(item)
                .where(likeItemName(itemName), maxPrice(maxPrice))  // BooleanExpression 들을 and 조건으로 연결한다. null 인 BooleanExpression 은 무시한다.
                .fetch();
    }

    // 쿼리 조건 부분적 모듈화
    private BooleanExpression maxPrice(Integer maxPrice) {
        if (maxPrice != null) {
            return item.price.loe(maxPrice);
        }
        return null;
    }

    // 쿼리 조건 부분적 모듈화
    private BooleanExpression likeItemName(String itemName) {
        if (StringUtils.hasText(itemName)) {
            return item.itemName.like("%" + itemName + "%");
        }
        return null;
    }


//    public List<Item> findAllOld(ItemSearchCond cond) {
//
//        String itemName = cond.getItemName();
//        Integer maxPrice = cond.getMaxPrice();
//
//        BooleanBuilder builder = new BooleanBuilder();
//        if (StringUtils.hasText(itemName)) {
//            builder.and(item.itemName.like("%" + itemName + "%"));
//        }
//        if (maxPrice != null) {
//            builder.and(item.price.loe(maxPrice));
//        }
//
//        return query.select(item)
//                .from(item)
//                .where(builder)
//                .fetch();
//    }
}
