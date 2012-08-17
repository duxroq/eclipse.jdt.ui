package object_in;

public class TestNotRemoveSynchronizedBlockCompareAndSetExtraFieldRefs {

	private int i;
	private int j;
	
	public void foo() {
		synchronized (this) {
			if (i == j) {
				i= ++j;
			}
		}
	}
}