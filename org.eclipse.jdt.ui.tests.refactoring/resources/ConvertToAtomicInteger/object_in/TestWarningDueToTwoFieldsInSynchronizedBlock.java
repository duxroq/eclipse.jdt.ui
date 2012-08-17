package object_in;

public class TestWarningDueToTwoFieldsInSynchronizedBlock {

	int f;
	int g;

	void twoFieldsInSyncBlock() {
		synchronized (this) {
			f = f + 12;
			g = g + 3;
		}
	}
}