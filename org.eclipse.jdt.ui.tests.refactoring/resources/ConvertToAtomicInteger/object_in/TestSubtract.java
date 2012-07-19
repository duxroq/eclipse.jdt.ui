package object_in;

public class TestSubtract {

	int f;
	int j;

	void subtract() {
		f = f - 12;
	}

	public synchronized void foo() {
		f -=(f-bar())*f*j;
	}

	public void bar() {
		f= f - 12 - j - f;
	}

	public void bar2() {
		f = j - f - 12;
	}

	public void bar3() {
		f = j - 12 - f;
	}
}