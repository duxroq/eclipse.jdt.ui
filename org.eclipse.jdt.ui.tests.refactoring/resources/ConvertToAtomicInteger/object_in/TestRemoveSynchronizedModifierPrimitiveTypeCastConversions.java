package object_in;

public class TestRemoveSynchronizedModifierPrimitiveTypeCastConversions {

	private int i;

	public synchronized double foo() {
		return ((double) i);
	}
}