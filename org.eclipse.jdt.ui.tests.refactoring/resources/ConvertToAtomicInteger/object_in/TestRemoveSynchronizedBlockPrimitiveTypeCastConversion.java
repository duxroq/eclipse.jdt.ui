package object_in;

public class TestRemoveSynchronizedBlockPrimitiveTypeCastConversion {

	private int i;

	public double foo() {
		synchronized (this) {
			return ((double) i);
		}
	}
}