package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestDemo {

	private AtomicInteger counter = new AtomicInteger();

	private int f;

	public int getCount() {
		return counter.get();
	}

	public void setCount(int value) {
		counter.set(value);
	}

	public int increment() {
		return counter.getAndIncrement();
	}

	private void addTen() {
		counter.addAndGet(10);
	}

	private void subtract23() {
		counter.addAndGet(-23);
	}

	private void add6() {
		counter.addAndGet(6);
	}

	// TODO The statements in the method below are not properly synchronized.

	private synchronized void doubleCounter() {
		// TODO The operations below cannot be executed atomically.
		counter.addAndGet(counter.get());
	}

	// TODO The statements in the method below are not properly synchronized.

	private synchronized void bar() {
		// TODO The operations below cannot be executed atomically.
		counter.set((counter.get()*3) - counter.get());
	}

	// TODO The statements in the method below are not properly synchronized.

	private synchronized void foo() {
		// TODO The operations below cannot be executed atomically.
		counter.addAndGet((counter.get()/3));
	}

	// TODO The statements in the method below are not properly synchronized.

	private synchronized void foo2() {
		// TODO The operations below cannot be executed atomically.
		counter.addAndGet(foo());
	}
}