package object_in;

public class TestWarningDueToTwoFieldsInSynchronizedMethod {

	int f;
	int g;

	synchronized void twoFieldsInSyncMethod() {
		f = f + 12;
		g = g + 3;
	}
}