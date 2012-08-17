package object_in;

public class TestNotRemoveSynchronizedBlockForWhileStatement {

	private int i = 0;
	
	public void bar() {
		synchronized (this) {
			while (i < 10) {
				i =i+2;
			}
		}
	}
}