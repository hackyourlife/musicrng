package org.unknown;

import java.util.Arrays;

public class Chord {
	private final int[] keys;

	public static final int MINOR = 0;
	public static final int MAJOR = 1;

	public Chord(int[] keys) {
		this.keys = keys;
		Arrays.sort(keys);
	}

	public int[] getKeys() {
		return keys;
	}

	public Chord transpose(int offset) {
		int[] result = new int[keys.length];

		for(int i = 0; i < keys.length; i++) {
			result[i] = (keys[i] + offset) % 12;
		}

		return new Chord(result);
	}

	public Chord up() {
		int[] result = new int[keys.length];

		for(int i = 0; i < keys.length - 1; i++) {
			result[i] = keys[i + 1];
		}
		result[keys.length - 1] = keys[0] + 12;

		return new Chord(result);
	}

	public Chord down() {
		int[] result = new int[keys.length];

		for(int i = 1; i < keys.length; i++) {
			result[i] = keys[i - 1];
		}
		result[0] = keys[keys.length - 1] - 12;

		return new Chord(result);
	}

	public Chord alternate() {
		if(keys.length == 3) {
			int[] result = new int[keys.length];

			if(keys[0] > keys[1] || keys[1] > keys[2]) {
				throw new AssertionError("fail!");
			}

			if(keys[0] + 7 == keys[2]) {
				result[0] = keys[0] - 2;
				result[1] = keys[1] - 1;
				result[2] = keys[2];
			} else if(keys[0] + 9 == keys[2]) {
				result[0] = keys[0];
				result[1] = keys[1] + 1;
				result[2] = keys[2];
			} else {
				return this;
			}
			return new Chord(result);
		} else {
			return this;
		}
	}

	public Chord octave(int base) {
		int[] result = new int[keys.length];

		for(int i = 0; i < keys.length; i++) {
			result[i] = (keys[i] % 12) + base;
		}

		return new Chord(result);
	}

	public int[] getScale(boolean minor) {
		int root = keys[0];
		if(minor) {
			return new int[] { root, root + 2, root + 3, root + 5, root + 7, root + 8, root + 10 };
		} else {
			return new int[] { root, root + 2, root + 4, root + 5, root + 7, root + 9,
					root + 11 };
		}
	}

	public static Chord getTriad(int n, boolean minor) {
		if(minor) {
			return new Chord(new int[] { (n % 12), (n + 3) % 12, (n + 7) % 12 });
		} else {
			return new Chord(new int[] { n % 12, (n + 4) % 12, (n + 7) % 12 });
		}
	}
}
