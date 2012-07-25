package object_in;

public class TestWarningDueToMultiplication {

	int f;
	int j;

	void multiply() {
		f = f * 12;
	}

	public synchronized void multiply2() {
		f = j * bar() * 2;
	}

	public synchronized void multiply3() {
		f = (12 + j) * 2;
	}

	public synchronized void multiply4() {
		f = (f + 12) * 2;
	}

	public void multiply5() {
		f = f*(f/3)*j*2;
	}
}
