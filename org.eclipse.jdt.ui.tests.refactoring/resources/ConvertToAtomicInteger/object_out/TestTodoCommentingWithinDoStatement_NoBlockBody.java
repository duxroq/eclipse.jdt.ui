package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestTodoCommentingWithinDoStatement_NoBlockBody {

	private AtomicInteger i = new AtomicInteger();

	public void foo() {
		do {
			// TODO The operations below cannot be executed atomically.
			i.addAndGet(i.get() + 12);
		} while (i.get() < 3);
	}
}