package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestTodoCommentingWithinIfStatement_elseStatement {

	private AtomicInteger i = new AtomicInteger();

	public void foo() {
		if (i.get()==3) {
			// TODO The operations below cannot be executed atomically.
			i.addAndGet(2 + foo());
		} else {
			i.getAndIncrement();
			// TODO The operations below cannot be executed atomically.
			i.set(i.get() * 2);
		}
	}
}