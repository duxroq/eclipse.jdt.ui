package object_in;

public class TestNotRemoveSynchronizedBlockAssertStatement {

	private int i;
	
	public void bar() {
		synchronized (this) {
			assert (i != 4);
		}
	}
}
