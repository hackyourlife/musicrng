package org.unknown;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiDevice.Info;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Receiver;
import javax.sound.midi.ShortMessage;

public class MusicGenerator {
	private int chordChannel;
	private int melodyChannel;
	private Receiver receiver;
	private boolean printDevices = true;

	private static int[] bias(int[] keys, int bias) {
		int[] result = new int[keys.length];
		for(int i = 0; i < keys.length; i++) {
			result[i] = keys[i] + bias;
		}
		return result;
	}

	private static int[] union(int[]... sets) {
		boolean[] keys = new boolean[128];
		for(int[] set : sets) {
			for(int key : set) {
				keys[key] = true;
			}
		}

		int cnt = 0;
		for(int i = 0; i < keys.length; i++) {
			if(keys[i]) {
				cnt++;
			}
		}

		int[] result = new int[cnt];
		for(int i = 0, j = 0; i < keys.length; i++) {
			if(keys[i]) {
				result[j++] = i;
			}
		}

		return result;
	}

	private void playRandom() throws Exception {
		playRandom(true, 0.3, 3, 0.1, 0.4);
	}

	private void playRandom(boolean minor, double altProbability, int maxChordLength, double switchFromPrimary,
			double switchToPrimary) throws Exception {
		int basekey = 52;
		int melodyoffset = 12;
		double scaleProbability = 0.2;

		int[] seeds = { 2, 0, 7 };

		Chord[] primaryChords = new Chord[seeds.length];
		Chord[] secondaryChords = new Chord[seeds.length];
		for(int i = 0; i < seeds.length; i++) {
			primaryChords[i] = Chord.getTriad(seeds[i], minor);
			secondaryChords[i] = Chord.getTriad(seeds[i], !minor);
		}

		boolean isPrimary = true;
		Chord[] chords = primaryChords;

		// Fixed seed; change it to generate a completely different track
		Random rng = new Random(0);

		boolean[] active = new boolean[128];

		int lastkey = 0;
		int transpose = 0;
		Chord lastChord = null;
		int lastChordId = -1;
		int chordLength = 0;

		int bartime = 500;
		long time = System.currentTimeMillis();
		long nextTime = time + bartime;

		loop: while(true) {
			// switch between primary and secondary?
			double switchrng = rng.nextFloat();
			if(isPrimary && switchrng < switchFromPrimary) {
				isPrimary = false;
				chords = secondaryChords;
			} else if(!isPrimary && switchrng < switchToPrimary) {
				isPrimary = true;
				chords = primaryChords;
			}

			// move chord transposition up or down?
			int move = rng.nextInt(3);
			int delta = move - 1;

			if(transpose + delta >= -1 && transpose + delta <= 1) {
				transpose += delta;
			}

			Chord c;
			int[] allkeys;
			if(lastChord != null && rng.nextFloat() > (1 - altProbability)) {
				// choose alternate version of last chord
				c = lastChord.alternate();
				allkeys = c.getKeys();
				chordLength = 0;
			} else {
				// choose random chord and make sure it's not the same chord all the time
				int chordId = rng.nextInt(chords.length);
				if(chordId == lastChordId) {
					chordLength++;
					if(chordLength > maxChordLength) {
						while((chordId = rng.nextInt(chords.length)) == lastChordId) {
							// nothing
						}
						chordLength = 0;
						lastChordId = chordId;
					}
				} else {
					lastChordId = chordId;
					chordLength = 0;
				}

				Chord base = chords[chordId].octave(basekey);

				// transpose accordingly
				switch(transpose) {
				case -1:
					c = base.down();
					break;
				default:
				case 0:
					c = base;
					break;
				case 1:
					c = base.up();
				}

				lastChord = c;

				allkeys = union(base.getKeys(), c.getKeys());
			}

			////////////////////////////////////////////////////////
			// update chord
			boolean doChords = true;
			if(doChords) {
				// get key sets
				int[] keys = c.getKeys();

				boolean[] newkeys = new boolean[128];
				for(int key : keys) {
					newkeys[key] = true;
				}

				// kill old keys
				for(int key = 0; key < active.length; key++) {
					if(active[key] && !newkeys[key]) {
						ShortMessage off = new ShortMessage(ShortMessage.NOTE_OFF, chordChannel,
								key, 64);
						receiver.send(off, 0);
						active[key] = false;
					}
				}

				// start new keys
				for(int key : keys) {
					if(!active[key]) {
						ShortMessage on = new ShortMessage(ShortMessage.NOTE_ON, chordChannel,
								key, 127);
						receiver.send(on, 0);
						active[key] = true;
					}
				}
			}

			////////////////////////////////////////////////////////
			// melody part
			for(int i = 0; i < 4; i++) {
				int key;
				// maybe choose a key that's not on a chord, but close to one?
				if(i > 0 && i < 3 && rng.nextFloat() < scaleProbability) {
					// if(isPrimary) minor else !minor
					// isPrimary && minor || !isPrimary && !minor
					// isPrimary == minor
					int[] scale = c.getScale(isPrimary == minor);

					int bestidx = -1;
					int bestscore = Integer.MAX_VALUE;
					for(int n = 0; n < scale.length; n++) {
						int a = scale[n] % 12;
						for(int m = 0; m < allkeys.length; m++) {
							int b = allkeys[m] % 12;
							int distance = (a - b) * (a - b);
							if(distance != 0 && distance < bestscore) {
								bestscore = distance;
								bestidx = n;
							}
						}
					}

					if(bestidx != -1) {
						// found something, use it
						key = scale[bestidx] + melodyoffset;
					} else {
						// found nothing, use one of the keys from the chord
						key = scale[rng.nextInt(scale.length)] + melodyoffset;
					}
				} else {
					// use one of the keys from the chord
					key = allkeys[rng.nextInt(allkeys.length)] + melodyoffset;
				}

				if(active[key]) {
					// don't play key if it overlaps chord
					// TODO: maybe *do* play it since it's a different instrument?
				} else {
					if(lastkey != -1 && lastkey != key) {
						ShortMessage off = new ShortMessage(ShortMessage.NOTE_OFF,
								melodyChannel, lastkey, 64);
						receiver.send(off, 0);
					}
					if(lastkey != key) {
						ShortMessage on = new ShortMessage(ShortMessage.NOTE_ON, melodyChannel,
								key, 127);
						receiver.send(on, 0);
					}
					lastkey = key;
				}

				// improve timing accuracy
				long now = System.currentTimeMillis();
				long sleep = nextTime - now;
				if(sleep > 0) {
					try {
						Thread.sleep(500);
					} catch(InterruptedException e) {
						break loop;
					}
				}
				nextTime += bartime;
			}
		}

		for(int key = 0; key < active.length; key++) {
			if(active[key]) {
				ShortMessage off = new ShortMessage(ShortMessage.NOTE_OFF, chordChannel, key, 64);
				receiver.send(off, 0);
			}
		}

		if(lastkey != -1) {
			ShortMessage off = new ShortMessage(ShortMessage.NOTE_OFF, melodyChannel, lastkey, 64);
			receiver.send(off, 0);
		}
	}

	public void start(String name, int chordChannel, int melodyChannel) throws MidiUnavailableException {
		this.chordChannel = chordChannel;
		this.melodyChannel = melodyChannel;

		System.out.println("Enumerating MIDI devices");
		List<Info> devices = new ArrayList<>();
		for(Info info : MidiSystem.getMidiDeviceInfo()) {
			try {
				MidiDevice device = MidiSystem.getMidiDevice(info);
				int maxout = device.getMaxReceivers();
				if(maxout == 0) {
					continue;
				}
				devices.add(info);
			} catch(Throwable t) {
				System.out.printf("Error reading device: %s\n", t.getMessage());
				System.out.printf("device: '%s' (%s, %s)\n", info.getName(), info.getVendor(),
						info.getDescription());
				throw t;
			}
		}

		if(printDevices) {
			System.out.println("Available MIDI OUT devices:");
			for(int i = 0; i < devices.size(); i++) {
				Info info = devices.get(i);
				System.out.printf("Device %d: '%s' (%s, %s)\n", i, info.getName(), info.getVendor(),
						info.getDescription());
			}
		}

		MidiDevice device = null;
		for(Info info : devices) {
			if(info.getName().equals(name)) {
				System.out.printf("Selecting device %s (%s, %s)\n", info.getName(), info.getVendor(),
						info.getDescription());
				device = MidiSystem.getMidiDevice(info);
				break;
			}
		}

		if(device == null) {
			System.out.println("not found: " + name);
			return;
		}

		device.open();

		receiver = device.getReceiver();

		Thread t = new Thread() {
			@Override
			public void run() {
				try {
					playRandom();
				} catch(Exception e) {
					e.printStackTrace();
					receiver.close();
				}
			}
		};

		t.start();

		try {
			System.in.read();
		} catch(IOException e) {
			// nothing
		}

		t.interrupt();

		try {
			t.join();
		} catch(InterruptedException e) {
			// nothing
		}

		try {
			ShortMessage alloff = new ShortMessage(ShortMessage.CONTROL_CHANGE, chordChannel, 123, 127);
			receiver.send(alloff, 0);
			alloff = new ShortMessage(ShortMessage.CONTROL_CHANGE, melodyChannel, 123, 127);
			receiver.send(alloff, 0);
		} catch(InvalidMidiDataException e) {
			e.printStackTrace();
		}
		receiver.close();
	}

	public static void main(String[] args) throws MidiUnavailableException {
		String device = args[0];
		int chordChannel = Integer.parseInt(args[1]);
		int melodyChannel = Integer.parseInt(args[2]);
		MusicGenerator generator = new MusicGenerator();
		// generator.start("MIDISPORT 2x2 Anniv", 0, 1);
		generator.start(device, chordChannel, melodyChannel);
	}
}
