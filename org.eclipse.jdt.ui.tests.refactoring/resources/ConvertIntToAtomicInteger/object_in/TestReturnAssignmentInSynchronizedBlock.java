package object_in;

public class TestReturnAssignmentInSynchronizedBlock {

	public int i;

	public int getI() {
		synchronized (this) {
			return i= 12;
		}
	}
}
