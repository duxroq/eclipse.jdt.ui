package object_in;

public class TestNotRemoveSynchronizedBlockForConditionalStatements {

	private int i;

	public void bar() {
		synchronized (this) {
			i= i < 2 ? 2 : 8;
		}
	}
}
