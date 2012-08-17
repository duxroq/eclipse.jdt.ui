package object_in;

public class TestReplaceIfStatementWithCompareAndSet {

	private int i;
	
	public synchronized void foo() {
		if (i == 3) {
			i = 2;
		}
	}
}