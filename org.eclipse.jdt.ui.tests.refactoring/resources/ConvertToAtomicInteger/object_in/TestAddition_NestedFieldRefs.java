package object_in;

public class TestAddition_NestedFieldRefs {

	private int i;
	
	public void foo() {
		i = (i*2) + i + i + (i*3);
	}
}
