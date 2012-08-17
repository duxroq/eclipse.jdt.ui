package object_in;

public class TestNotRemoveSynchronizedBlockSwitchStatement {

	private int i;

	public void bar() {
		synchronized (this) {
			switch (i) {
				case 3:
					break;
				case 4:
					break;
			}
		}
	}
}
