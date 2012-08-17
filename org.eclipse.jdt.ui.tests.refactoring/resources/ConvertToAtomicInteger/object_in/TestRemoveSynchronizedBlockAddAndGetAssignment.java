package object_in;

public class TestRemoveSynchronizedBlockAddAndGetAssignment {

	int counter;
	
	private void subtract23() {
		synchronized (this) {
			counter = counter - 23;					
		}
	}
}
