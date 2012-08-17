package oject_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestTodoCommentingWithinEnhancedForStatement {

	private AtomicInteger i = new AtomicInteger();
	
	public void foo() {
		for (int a : list) {
			// TODO The operations below cannot be executed atomically.
			i.addAndGet(12 + list.get(0));
		}
	}
}
