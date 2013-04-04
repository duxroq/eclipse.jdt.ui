package object_out;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class TestNotRemoveSynchronizedBlockEnhancedForStatement {

	private AtomicInteger i = new AtomicInteger();
	private ArrayList<int> list;

	public void bar() {
		synchronized (this) {
			// TODO This block is not properly synchronized in relation to other accesses to the refactored field.
			for (int a : list) {
				i.getAndIncrement();
			}
		}
	}
}
