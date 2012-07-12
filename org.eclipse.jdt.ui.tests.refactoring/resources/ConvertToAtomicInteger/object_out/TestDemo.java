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
}