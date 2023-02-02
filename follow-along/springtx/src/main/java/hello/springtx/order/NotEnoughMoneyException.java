package hello.springtx.order;

/**
 * 잔고 부족을 나타내는 비즈니스 예외
 * Checked Exception
 */
public class NotEnoughMoneyException extends Exception {

    public NotEnoughMoneyException(String message) {
        super(message);
    }
}
