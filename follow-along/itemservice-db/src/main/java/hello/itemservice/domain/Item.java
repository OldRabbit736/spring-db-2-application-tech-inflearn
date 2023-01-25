package hello.itemservice.domain;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;

@Data
@Entity // JPA Entity 선언
public class Item {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "item_name", length = 10) // DDL 생성될 때 length 와 같은 프로퍼티가 사용된다. (varchar 10)
    private String itemName;
    // @Column 생략할 경우 필드의 이름을 테이블 컬럼 이름으로 사용한다.
    // 스프링부트와 통합해서 사용하면 필드 이름을 테이블 컬럼 명으로 변경할 때 객체 필드의 카멜 케이스를 테이블 컬럼의 언더스코어로 자동 변환해준다.
    // itemName --> item_name 따라서 위의 @Column(name="item_name") 선언을 생략해도 된다.
    private Integer price;
    private Integer quantity;

    // JPA는 public 또는 protected의 기본 생성자가 필수이다.(스펙으로 명시되어 있다고 한다.) 기본 생성자를 꼭 넣어주자.
    public Item() {
    }

    public Item(String itemName, Integer price, Integer quantity) {
        this.itemName = itemName;
        this.price = price;
        this.quantity = quantity;
    }
}
