package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestTodoCommentingWithinIfStatement_elseStatementNoBlockBody {

	private AtomicInteger i = new AtomicInteger();

	public void foo() {
		if (i.get()==3)
			i.set(2);
		else {
			// TODO The operations below cannot be executed atomically.
			i.set(i.get() * 2);
		}
	}
}