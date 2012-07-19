package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestSubtract {

	AtomicInteger f = new AtomicInteger();
	int j;

	void subtract() {
		f.addAndGet(-12);
	}

	public synchronized void foo() {
		// TODO The operations below cannot be executed atomically.
		f.addAndGet(-((f.get()-bar())*f.get()*j));
	}

	public void bar() {
		// TODO The operations below cannot be executed atomically.
		f.addAndGet(-(12 + j + f.get()));
	}

	public void bar2() {
		// TODO The operations below cannot be executed atomically.
		f.set(j - f.get() - 12);
	}

	public void bar3() {
		// TODO The operations below cannot be executed atomically.
		f.set(j - 12 - f.get());
	}
}