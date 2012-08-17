package object_in;

public class TestNotRemoveSynchronizedBlockDoStatement {

	private int i;

	public void bar() {
		synchronized (this) {
			do {
				i++;
			} while (i < 3);
		}
	}
}