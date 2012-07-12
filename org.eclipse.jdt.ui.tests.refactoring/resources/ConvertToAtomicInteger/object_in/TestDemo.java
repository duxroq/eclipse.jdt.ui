package object_in;

public class TestDemo {

	private int counter;

	private int f;

	public int getCount() {
		return counter;
	}

	public void setCount(int value) {
		counter = value;
	}

	public int increment() {
		return counter++;
	}

	private void addTen() {
		counter = counter + 10;
	}

	private void subtract23() {
		synchronized (this) {
			counter = counter - 23;					
		}
	}

	private synchronized void add6() {
		counter = counter + 6;
	}
}