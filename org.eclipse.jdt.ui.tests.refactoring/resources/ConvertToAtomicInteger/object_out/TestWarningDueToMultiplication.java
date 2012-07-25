package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestWarningDueToMultiplication {

	AtomicInteger f = new AtomicInteger();
	int j;

	void multiply() {
		// TODO The operations below cannot be executed atomically.
		f.set(f.get() * 12);
	}

	// TODO The statements in the method below are not properly synchronized.

	public synchronized void multiply2() {
		// TODO The operations below cannot be executed atomically.
		f.set(j * bar() * 2);
	}

	// TODO The statements in the method below are not properly synchronized.

	public synchronized void multiply3() {
		// TODO The operations below cannot be executed atomically.
		f.set((12 + j) * 2);
	}

	// TODO The statements in the method below are not properly synchronized.

	public synchronized void multiply4() {
		// TODO The operations below cannot be executed atomically.
		f.set((f.get() + 12) * 2);
	}

	public void multiply5() {
		// TODO The operations below cannot be executed atomically.
		f.set(f.get()*(f.get()/3)*j*2);
	}
}
