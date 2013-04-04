package object_in;

public class TestNotRemoveSynchronizedBlockTryStatement {

	private int i;

	public void bar() {
		synchronized (this) {
			try {
				exceptionMethod();
			} catch (Exception e) {
				i--;
			} finally {
				i*=2;
			}
		}
	}
	
	public void exceptionMethod() throws Exception {
		throw new Exception("Error");
	}
}
