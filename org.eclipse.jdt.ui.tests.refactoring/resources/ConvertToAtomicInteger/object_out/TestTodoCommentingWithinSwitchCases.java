package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestTodoCommentingWithinSwitchCases {

	private AtomicInteger i = new AtomicInteger();
	
	public void foo() {
		switch (i.get()) {
			case 4:
		// TODO The operations below cannot be executed atomically.
		i.set(i.get() * 2);
				break;
			case 6:
				i.getAndIncrement();
				break;
			default:
				break;
		}
	}
}
