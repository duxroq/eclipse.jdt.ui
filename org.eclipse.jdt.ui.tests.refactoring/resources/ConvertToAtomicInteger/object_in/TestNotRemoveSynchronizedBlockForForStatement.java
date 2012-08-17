package object_in;

public class TestNotRemoveSynchronizedBlockForForStatement {

	private int i;

	public void bar() {
		synchronized (this) {
			for (i = 0; i<10; i++) {
				i = i+2;
			}
		}
	}
}