package object_in;

public class TestNotRemoveSynchronizedBlockForAnIfStatement {

	private int i;

	public void bar() {
		synchronized (this) {
			if (i == 3) {
				i++;
			}
		}
	}
}