package object_in;

public class TestWarningDueToMultiplicationAssignment {

	int f;
	int j;

	void multiply() {
		f *= 12;
	}

	public synchronized void foo() {
		f *=(f-multiply())*f*j;
	}
}
