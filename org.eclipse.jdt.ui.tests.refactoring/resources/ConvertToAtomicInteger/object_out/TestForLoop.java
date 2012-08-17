package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestForLoop {

	private AtomicInteger i = new AtomicInteger();

	public void foo() {
		for (i.set(0); i.get() < 10; i.getAndIncrement()) {
			i.set(3);
		}
	}
}
