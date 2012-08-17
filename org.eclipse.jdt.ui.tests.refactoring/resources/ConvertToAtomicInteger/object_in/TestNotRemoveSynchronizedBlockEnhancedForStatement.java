package object_in;

import java.util.ArrayList;

public class TestNotRemoveSynchronizedBlockEnhancedForStatement {

	private int i;
	private ArrayList<int> list;

	public void bar() {
		synchronized (this) {
			for (int a : list) {
				i++;
			}
		}
	}
}
