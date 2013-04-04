package object_in;

public class TestMultipleInfixExpWithMultipleRefsToChosenField {

	int i;
	int j;

	public synchronized void foo() {
		i= i + j + 12 + i;
	}
}