package object_in;

public class TestWarningDueToFieldAccessedTwiceInSynchronizedBlock {

	int f;

	void twoFieldsInSyncBlock() {
		synchronized (this) {
			f = f + 12;
			f++;
		}
	}
}