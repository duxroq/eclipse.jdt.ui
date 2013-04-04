package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestTodoCommentingWithinForStatement {

	private AtomicInteger i = new AtomicInteger();
	
	public void foo() {
		for (i.set(0); i.get() < 10; i.getAndIncrement()) {
			// TODO The operations below cannot be executed atomically.
			i.set(i.get() * 2);
		}
	}
}