package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestNotRemoveSynchronizedBlockSwitchStatement {

	private AtomicInteger i = new AtomicInteger();

	public void bar() {
		synchronized (this) {
			// TODO This block is not properly synchronized in relation to other accesses to the refactored field.
			switch (i.get()) {
				case 3:
					break;
				case 4:
					break;
			}
		}
	}
}
