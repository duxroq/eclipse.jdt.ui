package object_in;

public class TestMultipleInfixExpressionsWithReferenceToChosenField {

	int f;
	int a;
	
	public void foo() {
		f= 12 + a + f;
	}
}