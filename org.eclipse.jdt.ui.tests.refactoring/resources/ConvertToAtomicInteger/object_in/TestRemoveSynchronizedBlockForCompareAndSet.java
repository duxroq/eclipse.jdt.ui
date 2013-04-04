package object_in;

public class TestRemoveSynchronizedBlockForCompareAndSet {

	private int i;

	public void foo() {
		synchronized (this) {
			if (i == 3) {
				i = 2;
			}
		}
	}
}
