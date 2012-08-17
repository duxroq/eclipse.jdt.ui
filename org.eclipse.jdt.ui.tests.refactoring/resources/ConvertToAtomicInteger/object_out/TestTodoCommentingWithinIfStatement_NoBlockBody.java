package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestTodoCommentingWithinIfStatement_NoBlockBody {

	private AtomicInteger i = new AtomicInteger();

	public void foo() {
		if (i.get() < 3) {
			// TODO The operations below cannot be executed atomically.
			i.addAndGet(12 + (i.get()*4));
		}
	}
}