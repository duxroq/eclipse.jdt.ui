package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestTodoCommentingWithinWhileStatement_NoBlockBody {

	private AtomicInteger i = new AtomicInteger();

	public void foo() {
		while (i.get() < 10) {
			// TODO The operations below cannot be executed atomically.
			i.addAndGet(12 + (i.get()*2));
		}
	}
}