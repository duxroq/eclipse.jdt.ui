package object_in;

public class TestWarningDueToFieldAccessedTwiceInSynchronizedMethod {

	int f;

	synchronized void twoFieldsInSyncMethod() {
		f = f - 12;
		f++;
	}
}